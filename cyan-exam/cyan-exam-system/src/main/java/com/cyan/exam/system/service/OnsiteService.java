package com.cyan.exam.system.service;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.mapper.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnsiteService {
    public record Item(String title, Integer maxScore) {}
    public record TaskInput(String title, String description, Integer durationMinutes, Long judgeId, List<Item> items) {}
    public record ScoreInput(List<Integer> points, String note, Integer revision, boolean submit, Long submissionId, boolean markBlank) {
        public ScoreInput(List<Integer> points, String note, Integer revision, boolean submit, Long submissionId) {
            this(points,note,revision,submit,submissionId,false);
        }
        public ScoreInput(List<Integer> points, String note, Integer revision, boolean submit) {
            this(points,note,revision,submit,null,false);
        }
    }
    private final OnsiteMapper mapper;
    private final CyanExamInfoMapper exams;
    private final SysUserMapper users;
    private final ExamAccessScope scope;
    private final ExamResultMapper results;
    private final CyanQuestionMapper questionBank;

    public OnsiteService(OnsiteMapper mapper, CyanExamInfoMapper exams, SysUserMapper users,
            ExamAccessScope scope, ExamResultMapper results, CyanQuestionMapper questionBank) {
        this.mapper = mapper; this.exams = exams; this.users = users; this.scope = scope; this.results = results;
        this.questionBank = questionBank;
    }

    @Transactional
    public Long createFromBank(Long examId, Long questionId, Integer durationMinutes, Long judgeId) {
        var question = questionBank.selectCyanQuestionByQuestionId(questionId);
        if (question == null || !"0".equals(question.getStatus()) || !"PUBLISHED".equals(question.getReviewState())
                || !"ROBOT_PRACTICAL".equals(question.getQuestionType()))
            throw new ServiceException("请选择启用的机器人实操编程题");
        List<Item> items;
        try {
            items = JSON.parseArray(question.getAnswer(), Item.class);
            validateItems(items);
        } catch (RuntimeException ex) {
            throw new ServiceException("题库评分项无效，请先修正题目");
        }
        return create(examId, new TaskInput(question.getTitle(), question.getSubTitle(),
                durationMinutes, judgeId, items));
    }

    public List<Map<String, Object>> exams() {
        return mapper.exams(scope.actorId(), SecurityUtils.isAdmin(scope.actorId()), canJudge());
    }

    private boolean canJudge() {
        return SecurityUtils.isAdmin(scope.actorId()) || mapper.canJudge(scope.actorId());
    }

    private boolean owner(CyanExamInfo exam) {
        return exam != null && (SecurityUtils.isAdmin(scope.actorId()) || scope.actorId().equals(exam.getOwnerUserId()));
    }

    public Map<String, Object> detail(Long examId) {
        CyanExamInfo exam = exams.selectCyanExamInfoByExamId(examId);
        boolean manage = owner(exam);
        boolean judge = canJudge();
        List<Map<String, Object>> tasks = mapper.tasks(examId);
        if (!manage) tasks = tasks.stream().filter(t -> judge && exam != null && exam.getPublishedAt() != null
                && !"DRAFT".equals(t.get("state"))).toList();
        if (exam == null || (!manage && tasks.isEmpty())) throw new ServiceException("无权访问现场任务", 403);
        List<Map<String, Object>> response = new ArrayList<>();
        for (var t : tasks) {
            var value = new LinkedHashMap<>(t);
            value.put("items", JSON.parseArray((String) t.get("rubric"), Item.class));
            value.remove("rubric");
            value.put("canScore", judge);
            value.put("submittedUsers", mapper.submittedUsers(number(t, "id")));
            value.put("scores", mapper.scores(number(t, "id")).stream().map(s -> {
                var score = new LinkedHashMap<>(s);
                boolean own = scope.actorId().equals(number(s, "judgeId"));
                score.put("canEdit", judge && own && "DRAFT".equals(s.get("state")));
                score.put("canWithdraw", judge && own && "SUBMITTED".equals(s.get("state")));
                score.put("canApprove", manage && !own && "WITHDRAWAL".equals(s.get("state")));
                return score;
            }).toList());
            value.put("events", mapper.events(number(t, "id")));
            response.add(value);
        }
        return Map.of("examName", exam.getExamName(), "canManage", manage, "tasks", response,
                "roster", mapper.roster(examId), "judges", manage ? mapper.users() : List.of(),
                "resultLocked", "ARCHIVED".equals(exam.getLifecycle()) || locked(examId),
                "examEnded", ended(exam));
    }

    private boolean ended(CyanExamInfo exam) {
        return exam.getPublishedAt() != null && ("TERMINATED".equals(exam.getLifecycle())
                || (exam.getEndTime() != null && !exam.getEndTime().after(new Date())));
    }
    private boolean locked(Long examId) {
        var r = results.latest(examId);
        return r != null && !"REJECTED".equals(r.get("state")) && !"REOPENED".equals(r.get("state"));
    }

    private CyanExamInfo lock(Long examId) {
        CyanExamInfo exam = exams.lockByExamId(examId);
        if (exam == null) throw new ServiceException("考试不存在");
        if ("ARCHIVED".equals(exam.getLifecycle())) throw new ServiceException("场次已归档，只能查询");
        if (locked(examId)) throw new ServiceException("成绩已锁定，请先完成成绩更正审批");
        return exam;
    }

    @Transactional
    public Long create(Long examId, TaskInput input) {
        var exam = lock(examId);
        scope.requireAccess(exam);
        ExamManagementService.editable(exam);
        if (input == null) throw new ServiceException("任务不能为空");
        String title = text(input.title(), 120, true);
        String description = text(input.description(), 5000, false);
        if (input.durationMinutes() == null || input.durationMinutes() < 1 || input.durationMinutes() > 240)
            throw new ServiceException("机器人运行观察时长须为1至240分钟");
        int max = validateItems(input.items());
        Map<String, Object> t = new HashMap<>(Map.of("examId", examId, "title", title,
                "description", description, "durationMinutes", input.durationMinutes(), "judgeId", 0L,
                "rubric", JSON.toJSONString(input.items()), "maxScore", max, "actor", scope.actorId()));
        mapper.insert(t);
        Long id = number(t, "id");
        mapper.event(id, null, scope.actorId(), "CREATE", "创建现场任务", JSON.toJSONString(input));
        return id;
    }

    public static int validateItems(List<Item> items) {
        if (items == null || items.isEmpty() || items.size() > 30) throw new ServiceException("评分项数量须为1至30");
        int max = 0;
        for (Item item : items) {
            if (item == null) throw new ServiceException("评分项不能为空");
            text(item.title(), 120, true);
            if (item.maxScore() == null || item.maxScore() < 1 || item.maxScore() > 100)
                throw new ServiceException("单项满分须为1至100");
            max += item.maxScore();
        }
        if (max > 1000) throw new ServiceException("任务总分不得超过1000");
        return max;
    }

    public static int validatePoints(List<Item> items, List<Integer> points, boolean submit) {
        if (points == null || points.size() != items.size()) throw new ServiceException("评分项数量不匹配");
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            Integer score = points.get(i);
            if (score == null) {
                if (submit) throw new ServiceException("请完成全部评分项");
            } else {
                if (score < 0 || score > items.get(i).maxScore()) throw new ServiceException("评分超出单项满分");
                total += score;
            }
        }
        return total;
    }

    // Every writer takes the exam lock first, matching the existing result workflow.
    private Map<String, Object> lockedTask(Long examId, Long taskId) {
        lock(examId);
        var task = mapper.task(taskId);
        if (task == null || !examId.equals(number(task, "examId"))) throw new ServiceException("任务不存在", 404);
        return task;
    }

    @Transactional
    public void taskAction(Long examId, Long taskId, String action, String reason) {
        var task = lockedTask(examId, taskId);
        scope.requireAccess(exams.selectCyanExamInfoByExamId(examId));
        reason = text(reason, 1000, true);
        if ("publish".equals(action)) {
            throw new ServiceException("请通过考试编排统一发布现场任务");
        } else if ("lock".equals(action)) {
            require(task, "OPEN");
            var scores = mapper.scores(taskId);
            var roster = mapper.roster(examId);
            if (roster.isEmpty() || roster.stream().anyMatch(u -> scores.stream().noneMatch(s ->
                    number(u, "userId").equals(number(s, "userId")) && "SUBMITTED".equals(s.get("state")))))
                throw new ServiceException("所有考生评分提交后才能锁定");
            mapper.state(taskId, "LOCKED");
        } else if ("reopen".equals(action)) {
            require(task, "LOCKED");
            mapper.state(taskId, "OPEN");
        } else throw new ServiceException("不支持的任务操作");
        mapper.event(taskId, null, scope.actorId(), action.toUpperCase(Locale.ROOT), reason, JSON.toJSONString(task));
    }

    @Transactional
    public void score(Long examId, Long taskId, Long userId, ScoreInput input) {
        var task = lockedTask(examId, taskId);
        var exam=exams.selectCyanExamInfoByExamId(examId);
        if(exam.getPublishedAt()==null || new Date().before(exam.getStartTime()))
            throw new ServiceException("考试尚未开始，不能评分");
        if (!canJudge()) throw new ServiceException("仅评委可以评分", 403);
        require(task, "OPEN");
        if (mapper.roster(examId).stream().noneMatch(u -> userId.equals(number(u, "userId"))))
            throw new ServiceException("考生不在本场名单", 403);
        if (input == null) throw new ServiceException("评分不能为空");
        var old = mapper.scores(taskId).stream().filter(s -> userId.equals(number(s, "userId"))).findFirst().orElse(null);
        if (old != null && !"DRAFT".equals(old.get("state"))) throw new ServiceException("已提交评分不能覆盖，请申请撤回");
        if (old != null && !scope.actorId().equals(number(old, "judgeId")))
            throw new ServiceException("其他评委已开始评分，不能覆盖其评分草稿", 409);
        long revision = old == null ? 0 : number(old, "revision");
        if (input.revision() == null || input.revision() != revision) throw new ServiceException("评分已变化，请刷新后重试");
        var history=mapper.codeHistory(taskId,userId);
        Long submissionId = history.isEmpty() ? null : number(history.get(0),"id");
        if (history.isEmpty()) {
            if (!input.markBlank() || !ended(exam) || !input.submit() || input.submissionId() != null
                    || input.points() == null || input.points().stream().anyMatch(p -> p == null || p != 0))
                throw new ServiceException("未提交代码只能在考试结束后确认按白卷记0分");
        } else if (input.markBlank() || !submissionId.equals(input.submissionId())) {
            throw new ServiceException("考生提交版本已变化，请重新打开代码后评分");
        }
        int total = validatePoints(JSON.parseArray((String) task.get("rubric"), Item.class), input.points(), input.submit());
        String note = history.isEmpty() ? "未提交实操代码，按白卷记0分" : text(input.note(), 1000, false);
        Map<String, Object> score = new HashMap<>(Map.of("taskId", taskId, "userId", userId, "judgeId", scope.actorId(),
                "points", JSON.toJSONString(input.points()), "note", note, "total", total,
                "state", input.submit() ? "SUBMITTED" : "DRAFT"));
        score.put("submissionId", submissionId);
        mapper.save(score);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("before", old); payload.put("after", score);
        mapper.event(taskId, userId, scope.actorId(), input.submit() ? "SUBMIT" : "SAVE", note, JSON.toJSONString(payload));
    }

    @Transactional
    public void withdrawal(Long examId, Long taskId, Long userId, String action, String reason) {
        var task = lockedTask(examId, taskId);
        require(task, "OPEN");
        reason = text(reason, 1000, true);
        var score = mapper.scores(taskId).stream().filter(s -> userId.equals(number(s, "userId"))).findFirst()
                .orElseThrow(() -> new ServiceException("评分不存在"));
        String next;
        if ("request".equals(action)) {
            if (!canJudge() || !scope.actorId().equals(number(score, "judgeId")))
                throw new ServiceException("仅原评分评委可以申请撤回", 403);
            require(score, "SUBMITTED"); next = "WITHDRAWAL";
        } else {
            scope.requireAccess(exams.selectCyanExamInfoByExamId(examId));
            if (scope.actorId().equals(number(score, "judgeId"))) throw new ServiceException("不能审批自己的撤回申请");
            require(score, "WITHDRAWAL");
            next = switch (action) {
                case "approve" -> "DRAFT";
                case "reject" -> "SUBMITTED";
                default -> throw new ServiceException("不支持的审批操作");
            };
        }
        mapper.scoreState(taskId, userId, next);
        mapper.event(taskId, userId, scope.actorId(), "WITHDRAW_" + action.toUpperCase(Locale.ROOT), reason, JSON.toJSONString(score));
    }

    static Long number(Map<String, Object> value, String key) { return ((Number) value.get(key)).longValue(); }
    private static void require(Map<String, Object> value, String state) {
        if (!state.equals(value.get("state"))) throw new ServiceException("当前状态不允许该操作");
    }
    private static String text(String value, int max, boolean required) {
        String result = value == null ? "" : value.trim();
        if ((required && result.isBlank()) || result.length() > max) throw new ServiceException("文本为空或超出长度限制：" + max);
        return result;
    }
}
