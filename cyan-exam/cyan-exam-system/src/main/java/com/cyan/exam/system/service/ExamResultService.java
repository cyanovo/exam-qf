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
        if (results.isReviewer(examId, scope.actorId()) != 1) {
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
        scope.requireAccess(exams.lockByExamId(examId));
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
        if (exam.getPublishedAt() == null || exam.getEndTime() == null || new Date().before(exam.getEndTime())) {
            throw new ServiceException("考试结束后才能提交成绩复核");
        }
        if (results.reviewers(examId).isEmpty()) throw new ServiceException("请先绑定复核员");
        List<CyanQuestion> qs = questions.selectSnapshots(examId);
        if (qs.isEmpty()) throw new ServiceException("试卷快照为空");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Long> roster = students.selectUserIdsByExamId(examId);
        if (roster.isEmpty()) throw new ServiceException("考试没有考生");
        for (Long userId : roster) {
            ExamSession session = sessions.lockByExamAndUser(examId, userId);
            var user = users.selectUserById(userId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("studentName", user == null ? String.valueOf(userId) : user.getNickName());
            row.put("status", session == null ? "ABSENT" : "GRADED");
            if (session == null) {
                row.put("total", null); row.put("rank", null); row.put("tied", false); row.put("scores", List.of());
            } else {
                if (!Set.of("SUBMITTED", "AUTO_SUBMITTED", "GRADED").contains(session.getStatus())) {
                    throw new ServiceException("存在尚未交卷的考生：" + userId);
                }
                Map<Long, ExamAnswerRecord> records = new HashMap<>();
                answers.selectByExamAndUser(examId, userId).forEach(r -> records.put(r.getQuestionId(), r));
                int total = 0;
                List<Map<String, Object>> scores = new ArrayList<>();
                for (CyanQuestion q : qs) {
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
                    scores.add(Map.of("questionId", q.getQuestionId(), "score", r.getScore(), "maxScore", q.getMaxScore()));
                    total += r.getScore();
                }
                row.put("total", total); row.put("scores", scores);
            }
            rows.add(row);
        }
        rank(rows);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("examId", examId); snapshot.put("examName", exam.getExamName());
        snapshot.put("maxScore", qs.stream().mapToInt(CyanQuestion::getMaxScore).sum());
        snapshot.put("rows", rows); snapshot.put("statistics", statistics(rows));
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
            if (scope.actorId().equals(number(current, "requestedBy"))
                    || results.hasGraded(examId, scope.actorId()) > 0) {
                throw new ServiceException("不能复核自己提交或批改的成绩");
            }
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
