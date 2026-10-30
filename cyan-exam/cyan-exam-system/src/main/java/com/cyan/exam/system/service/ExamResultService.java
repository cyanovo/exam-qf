package com.cyan.exam.system.service;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.domain.*;
import com.cyan.exam.system.mapper.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamResultService {
    private final CyanExamInfoMapper exams;
    private final ExamResultMapper results;
    private final ExamStudentMapper students;
    private final ExamSessionMapper sessions;
    private final ExamQuestionMapper questions;
    private final ExamAnswerRecordMapper answers;
    private final SysUserMapper users;
    private final ExamAccessScope scope;
    @org.springframework.beans.factory.annotation.Autowired
    private CodeSubmissionMapper codeSubmissions;
    @org.springframework.beans.factory.annotation.Autowired
    private OnsiteMapper onsite;

    public ExamResultService(CyanExamInfoMapper exams, ExamResultMapper results, ExamStudentMapper students,
            ExamSessionMapper sessions, ExamQuestionMapper questions, ExamAnswerRecordMapper answers,
            SysUserMapper users, ExamAccessScope scope) {
        this.exams = exams; this.results = results; this.students = students; this.sessions = sessions;
        this.questions = questions; this.answers = answers; this.users = users; this.scope = scope;
    }

    // All grade writers take the exam lock before the session lock.
    public CyanExamInfo lockForGrading(Long examId) {
        CyanExamInfo exam = exams.lockByExamId(examId);
        if (exam == null) throw new ServiceException("考试不存在");
        if ("ARCHIVED".equals(exam.getLifecycle())) throw new ServiceException("已归档成绩只允许查询");
        Map<String, Object> latest = results.latest(examId);
        if (latest != null && !"REJECTED".equals(latest.get("state")) && !"REOPENED".equals(latest.get("state"))) {
            throw new ServiceException("成绩已锁定，须通过复核后才能修改");
        }
        return exam;
    }

    public boolean isLocked(Long examId) {
        Map<String, Object> latest = results.latest(examId);
        return latest != null && !"REJECTED".equals(latest.get("state")) && !"REOPENED".equals(latest.get("state"));
    }

    public List<Map<String, Object>> reviewExams() { return results.reviewExams(scope.actorId()); }

    private void requireReviewer(Long examId) {
        if (!SecurityUtils.isAdmin(scope.actorId()) && results.isReviewer(examId, scope.actorId()) != 1) {
            throw new ServiceException("未绑定该考试的复核权限", 403);
        }
    }

    public Map<String, Object> detail(Long examId) {
        CyanExamInfo exam = exams.selectCyanExamInfoByExamId(examId);
        if (results.isReviewer(examId, scope.actorId()) != 1) scope.requireAccess(exam);
        if (exam == null) throw new ServiceException("考试不存在");
        Map<String, Object> value = results.latest(examId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("examName", exam.getExamName());
        response.put("archived", "ARCHIVED".equals(exam.getLifecycle()));
        response.put("reviewers", results.reviewers(examId));
        response.put("state", value == null ? "GRADING" : value.get("state"));
        if (value != null) {
            response.put("version", value.get("version"));
            response.put("snapshot", JSON.parseObject((String) value.get("payload")));
            response.put("events", results.events(number(value, "id")));
            response.put("releasedAt", value.get("releasedAt"));
        }
        return response;
    }

    @Transactional
    public void assign(Long examId, Long userId) {
        var exam=exams.lockByExamId(examId);
        scope.requireAccess(exam);
        if("ARCHIVED".equals(exam.getLifecycle())) throw new ServiceException("已归档，只允许查询");
        var user = userId == null ? null : users.selectUserById(userId);
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) {
            throw new ServiceException("请选择有效复核员");
        }
        results.assign(examId, userId, scope.actorId());
    }

    @Transactional
    public void requestReview(Long examId, String reason) {
        reason = reason(reason);
        CyanExamInfo exam = lockForGrading(examId);
        scope.requireAccess(exam);
        if (exam.getPublishedAt() == null || exam.getEndTime() == null
                || (!"TERMINATED".equals(exam.getLifecycle()) && new Date().before(exam.getEndTime()))) {
            throw new ServiceException("考试结束后才能提交成绩复核");
        }
        if (results.reviewers(examId).isEmpty()) throw new ServiceException("请先绑定复核员");
        List<CyanQuestion> qs = questions.selectSnapshots(examId);
        List<Map<String, Object>> tasks = onsite.tasks(examId);
        if (qs.isEmpty() && tasks.isEmpty()) throw new ServiceException("考试没有题目或现场任务");
        if (tasks.stream().anyMatch(t -> !"LOCKED".equals(t.get("state"))))
            throw new ServiceException("请先提交并锁定全部现场任务评分");
        Map<Long, List<Map<String, Object>>> taskScores = new HashMap<>();
        for (var task : tasks) taskScores.put(number(task, "id"), onsite.scores(number(task, "id")));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Long> roster = students.selectUserIdsByExamId(examId);
        if (roster.isEmpty()) throw new ServiceException("考试没有考生");
        for (Long userId : roster) {
            ExamSession session = sessions.lockByExamAndUser(examId, userId);
            var user = users.selectUserById(userId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("studentName", user == null ? String.valueOf(userId) : user.getNickName());
            row.put("submittedAt", session == null ? null : session.getSubmittedAt());
            row.put("status", session == null && tasks.isEmpty() ? "ABSENT" : "GRADED");
            if (session == null && tasks.isEmpty()) {
                row.put("total", null); row.put("rank", null); row.put("tied", false); row.put("scores", List.of());
            } else {
                if (session != null && !Set.of("SUBMITTED", "AUTO_SUBMITTED", "GRADED").contains(session.getStatus())) {
                    throw new ServiceException("存在尚未交卷的考生：" + userId);
                }
                Map<Long, ExamAnswerRecord> records = new HashMap<>();
                answers.selectByExamAndUser(examId, userId).forEach(r -> records.put(r.getQuestionId(), r));
                int total = 0;
                List<Map<String, Object>> scores = new ArrayList<>();
                for (CyanQuestion q : qs) {
                    if (session == null) {
                        scores.add(Map.of("questionId", q.getQuestionId(), "title", Objects.toString(q.getTitle(),""), "score", 0, "maxScore", q.getMaxScore()));
                        continue;
                    }
                    if ("PYTHON".equals(q.getQuestionType())) {
                        Map<String, Object> code = codeSubmissions.latestFormal(examId, userId, q.getQuestionId());
                        if (code != null && !"SUCCEEDED".equals(code.get("state"))) {
                            throw new ServiceException("编程评测尚未完成：" + userId + "/" + q.getQuestionId());
                        }
                    }
                    ExamAnswerRecord r = records.get(q.getQuestionId());
                    if (r == null || r.getGradedAt() == null || r.getScore() == null
                            || r.getScore() < 0 || r.getScore() > q.getMaxScore()) {
                        throw new ServiceException("存在未完成或无效评分：" + userId + "/" + q.getQuestionId());
                    }
                    scores.add(Map.of("questionId", q.getQuestionId(), "title", Objects.toString(q.getTitle(),""), "score", r.getScore(), "maxScore", q.getMaxScore()));
                    total += r.getScore();
                }
                for (var task : tasks) {
                    var score = taskScores.get(number(task, "id")).stream()
                            .filter(s -> userId.equals(number(s, "userId"))).findFirst()
                            .orElseThrow(() -> new ServiceException("现场任务缺少考生评分：" + userId));
                    int value = ((Number) score.get("total")).intValue();
                    if (!"SUBMITTED".equals(score.get("state")) || value < 0 || value > ((Number) task.get("maxScore")).intValue())
                        throw new ServiceException("现场任务评分无效");
                    scores.add(Map.of("taskId", task.get("id"), "kind", "ONSITE", "title", task.get("title"),
                            "score", value, "maxScore", task.get("maxScore")));
                    total += value;
                }
                row.put("total", total); row.put("scores", scores);
            }
            rows.add(row);
        }
        var policy = ExamPolicy.read(exam);
        rank(rows, policy);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("examId", examId); snapshot.put("examName", exam.getExamName());
        snapshot.put("maxScore", qs.stream().mapToInt(CyanQuestion::getMaxScore).sum()
                + tasks.stream().mapToInt(t -> ((Number) t.get("maxScore")).intValue()).sum());
        snapshot.put("rows", rows); snapshot.put("statistics", statistics(rows));
        snapshot.put("policy", policy);
        snapshot.put("taskStatistics", taskStatistics(rows));
        Map<String, Object> old = results.latest(examId);
        Map<String, Object> insert = new HashMap<>();
        insert.put("examId", examId); insert.put("version", old == null ? 1 : number(old, "version") + 1);
        insert.put("payload", JSON.toJSONString(snapshot)); insert.put("actorId", scope.actorId()); insert.put("reason", reason);
        results.insert(insert);
        results.event(number(results.latest(examId), "id"), scope.actorId(), "GRADING", "REVIEW", reason);
        results.markGraded(examId);
    }

    @Transactional
    public void transition(Long examId, String action, String reason) {
        reason = reason(reason);
        CyanExamInfo exam = exams.lockByExamId(examId);
        if (exam == null) throw new ServiceException("考试不存在");
        if ("ARCHIVED".equals(exam.getLifecycle())) throw new ServiceException("已归档成绩只允许查询");
        Map<String, Object> current = results.latest(examId);
        if (current == null) throw new ServiceException("尚未提交复核");
        String before = (String) current.get("state");
        String after;
        if ("requestCorrection".equals(action)) {
            scope.requireAccess(exam);
            if (!"RELEASED".equals(before)) throw new ServiceException("仅已发布成绩可申请更正");
            after = "CORRECTION";
        } else {
            requireReviewer(examId);
            if (!SecurityUtils.isAdmin(scope.actorId()) && (scope.actorId().equals(number(current, "requestedBy"))
                    || results.hasGraded(examId, scope.actorId()) > 0
                    || onsite.hasGraded(examId, scope.actorId()) > 0)) {
                throw new ServiceException("不能复核自己提交或批改的成绩");
            }
            if (SecurityUtils.isAdmin(scope.actorId())) reason = "[超级管理员复核] " + reason;
            after = switch (action) {
                case "approve" -> { requireState(before, "REVIEW"); yield "APPROVED"; }
                case "reject" -> { requireState(before, "REVIEW"); yield "REJECTED"; }
                case "release" -> { requireState(before, "APPROVED"); yield "RELEASED"; }
                case "allowCorrection" -> { requireState(before, "CORRECTION"); yield "REOPENED"; }
                case "denyCorrection" -> { requireState(before, "CORRECTION"); yield "RELEASED"; }
                default -> throw new ServiceException("不支持的成绩操作");
            };
        }
        Long id = number(current, "id");
        if (results.transition(id, before, after, scope.actorId()) != 1) throw new ServiceException("成绩状态已变化");
        results.event(id, scope.actorId(), before, after, reason);
    }

    public List<Map<String, Object>> myResults() { return results.myResults(scope.actorId()); }

    public Map<String, Object> myResult(Long examId) {
        Long userId = scope.actorId();
        if (!students.selectUserIdsByExamId(examId).contains(userId)) throw new ServiceException("无权查看该考试", 403);
        Map<String, Object> published = results.published(examId);
        if (published == null) throw new ServiceException("成绩尚未发布");
        var snapshot = JSON.parseObject((String) published.get("payload"));
        for (Object item : snapshot.getJSONArray("rows")) {
            var row = (com.alibaba.fastjson.JSONObject) item;
            if (userId.equals(row.getLong("userId"))) {
                Map<String, Object> response = new LinkedHashMap<>(row);
                response.put("examName", snapshot.getString("examName"));
                response.put("version", published.get("version"));
                response.put("releasedAt", published.get("releasedAt"));
                var policy = snapshot.getJSONObject("policy");
                String visibility = policy == null ? "DETAIL" : policy.getString("releasePolicy");
                response.put("releasePolicy", visibility);
                response.remove("submittedAt");
                if (!"DETAIL".equals(visibility)) response.remove("scores");
                if ("TOTAL".equals(visibility)) { response.remove("rank"); response.remove("tied"); }
                return response;
            }
        }
        throw new ServiceException("成绩快照中无该考生");
    }

    public static void rank(List<Map<String, Object>> rows) {
        rows.sort(Comparator.<Map<String, Object>>comparingInt(r -> r.get("total") == null
                ? Integer.MIN_VALUE : ((Number) r.get("total")).intValue()).reversed()
                .thenComparingLong(r -> ((Number) r.get("userId")).longValue()));
        Map<Integer, Long> counts = new HashMap<>();
        rows.stream().filter(r -> r.get("total") != null).forEach(r -> counts.merge((Integer) r.get("total"), 1L, Long::sum));
        Integer previous = null; int rank = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Integer total = (Integer) row.get("total");
            if (total == null) continue;
            if (!total.equals(previous)) rank = i + 1;
            row.put("rank", rank); row.put("tied", counts.get(total) > 1);
            previous = total;
        }
    }

    public static void rank(List<Map<String, Object>> rows, ExamPolicy.Policy policy) {
        if ("SHARED".equals(policy.tieRule())) { rank(rows); return; }
        Comparator<Map<String,Object>> primary = Comparator.<Map<String,Object>>comparingInt(
                r -> r.get("total")==null ? Integer.MIN_VALUE : ((Number)r.get("total")).intValue()).reversed();
        Comparator<Map<String,Object>> secondary = "TASK_SCORE".equals(policy.tieRule())
                ? Comparator.<Map<String,Object>>comparingInt(r->taskScore(r,policy.tieTaskId())).reversed()
                : Comparator.comparing(r->(Date)r.get("submittedAt"),Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Map<String,Object>> ranking = primary.thenComparing(secondary);
        rows.sort(ranking.thenComparingLong(r->((Number)r.get("userId")).longValue()));
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);
            if(row.get("total")==null) continue;
            boolean previous=i>0 && ranking.compare(rows.get(i-1),row)==0;
            boolean next=i+1<rows.size() && ranking.compare(rows.get(i+1),row)==0;
            row.put("rank",previous ? rows.get(i-1).get("rank") : i+1);
            row.put("tied",previous || next);
        }
    }
    @SuppressWarnings("unchecked")
    private static int taskScore(Map<String,Object> row,Long id) {
        return ((List<Map<String,Object>>)row.getOrDefault("scores",List.of())).stream()
                .filter(s->s.get("taskId") instanceof Number n && id.equals(n.longValue()))
                .mapToInt(s->((Number)s.get("score")).intValue()).findFirst().orElse(0);
    }
    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> taskStatistics(List<Map<String,Object>> rows) {
        Map<String,Map<String,Object>> groups=new LinkedHashMap<>();
        for(var row:rows) for(var score:(List<Map<String,Object>>)row.getOrDefault("scores",List.of())) {
            String key=score.containsKey("taskId") ? "ONSITE:"+score.get("taskId") : "QUESTION:"+score.get("questionId");
            var g=groups.computeIfAbsent(key,k->new LinkedHashMap<>(Map.of("key",k,
                    "title",score.getOrDefault("title",k),"count",0,"earned",0L,"possible",0L)));
            g.put("count",((Number)g.get("count")).intValue()+1);
            g.put("earned",((Number)g.get("earned")).longValue()+((Number)score.get("score")).longValue());
            g.put("possible",((Number)g.get("possible")).longValue()+((Number)score.get("maxScore")).longValue());
        }
        for(var g:groups.values()) g.put("rate", ((Number)g.get("possible")).longValue()==0 ? 0
                : ((Number)g.get("earned")).doubleValue()/((Number)g.get("possible")).doubleValue()*100);
        return new ArrayList<>(groups.values());
    }

    public List<Map<String,Object>> history(Long examId) {
        detail(examId);
        var versions=results.history(examId);
        for(var v:versions) {
            v.put("snapshot",JSON.parseObject((String)v.remove("payload")));
            v.put("events",results.events(number(v,"id")));
        }
        return versions;
    }

    private static Map<String, Object> statistics(List<Map<String, Object>> rows) {
        IntSummaryStatistics stats = rows.stream().filter(r -> r.get("total") != null)
                .mapToInt(r -> (Integer) r.get("total")).summaryStatistics();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registered", rows.size()); result.put("completed", stats.getCount());
        result.put("highest", stats.getCount() == 0 ? null : stats.getMax());
        result.put("lowest", stats.getCount() == 0 ? null : stats.getMin());
        result.put("average", stats.getCount() == 0 ? null : stats.getAverage());
        return result;
    }

    private static void requireState(String actual, String expected) {
        if (!expected.equals(actual)) throw new ServiceException("当前成绩状态不允许该操作");
    }
    private static Long number(Map<String, Object> map, String key) { return ((Number) map.get(key)).longValue(); }
    private static String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) throw new ServiceException("请填写1至500字的操作原因");
        return reason.trim();
    }
}
