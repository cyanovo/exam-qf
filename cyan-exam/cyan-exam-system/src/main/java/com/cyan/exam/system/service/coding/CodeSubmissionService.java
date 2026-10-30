package com.cyan.exam.system.service.coding;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.*;
import com.cyan.exam.system.mapper.*;
import com.cyan.exam.system.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeSubmissionService {
    private final CodeSubmissionMapper submissions;
    private final CyanExamInfoMapper exams;
    private final ExamStudentMapper students;
    private final ExamSessionMapper sessions;
    private final ExamQuestionMapper questions;
    private final Judge0Client runner;
    private final ExamAccessScope scope;
    private final ExamResultService results;
    private final ExamAnswerRecordMapper answers;

    public CodeSubmissionService(CodeSubmissionMapper submissions, CyanExamInfoMapper exams, ExamStudentMapper students,
            ExamSessionMapper sessions, ExamQuestionMapper questions, Judge0Client runner, ExamAccessScope scope,
            ExamResultService results, ExamAnswerRecordMapper answers) {
        this.submissions = submissions; this.exams = exams; this.students = students; this.sessions = sessions;
        this.questions = questions; this.runner = runner; this.scope = scope; this.results = results;
        this.answers = answers;
    }
    public record Request(Long questionId, String mode, String source, String key, String snapshotHash, String sourceHash) {}

    @Transactional
    public Map<String, Object> submit(Long examId, Request request) {
        Long userId = scope.actorId();
        if (!students.selectExamIdsByUserId(userId).contains(examId)) throw new ServiceException("无权参加考试", 403);
        if (request == null || request.questionId() == null || request.source() == null || request.source().isBlank()
                || request.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000 || request.key() == null || !request.key().matches("[a-zA-Z0-9-]{16,64}")
                || request.mode() == null || !Set.of("SAMPLE", "FORMAL").contains(request.mode())
                || !CodingRules.hash(request.source()).equals(request.sourceHash())) {
            throw new ServiceException("代码、摘要或提交参数无效");
        }
        // The session lock serializes submissions with finish/deadline sealing and per-user rate limits.
        ExamSession session = sessions.lockByExamAndUser(examId, userId);
        Map<String, Object> existing = submissions.byKey(examId, userId, request.key());
        if (existing != null) {
            if (!request.sourceHash().equals(existing.get("sourceHash")) || !request.mode().equals(existing.get("mode"))
                    || !request.questionId().equals(((Number) existing.get("questionId")).longValue())
                    || !Objects.equals(request.snapshotHash(), existing.get("snapshotHash"))) {
                throw new ServiceException("幂等键已用于其他提交");
            }
            return safe(existing);
        }
        CyanExamInfo exam = exams.selectCyanExamInfoByExamId(examId);
        Date now = new Date();
        if (session == null || !"IN_PROGRESS".equals(session.getStatus()) || exam == null
                || !"0".equals(exam.getStatus()) || exam.getPublishedAt() == null
                || exam.getStartTime() == null || exam.getEndTime() == null
                || now.before(exam.getStartTime()) || !now.before(ExamPolicy.deadline(exam, session))) throw new ServiceException("考试未开始或已截止");
        CyanQuestion question = question(examId, request.questionId());
        CodingRules.parse(question.getAnswer(), question.getMaxScore());
        String snapshotHash = CodingRules.hash(question.getAnswer());
        if (!snapshotHash.equals(request.snapshotHash())) throw new ServiceException("题目版本已变化，请重新加载");
        runner.requireConfigured();
        if (submissions.pendingOrRecent(userId) >= 3 || submissions.queueSize() >= 2000) throw new ServiceException("评测队列繁忙，请稍后重试");
        Map<String, Object> row = new HashMap<>();
        row.put("examId", examId); row.put("userId", userId); row.put("questionId", request.questionId());
        row.put("mode", request.mode()); row.put("key", request.key()); row.put("source", request.source());
        row.put("sourceHash", request.sourceHash()); row.put("snapshotHash", snapshotHash);
        if (submissions.insert(row) != 1) throw new ServiceException("考试已截止，本次提交未接收");
        return safe(submissions.get(((Number) row.get("id")).longValue()));
    }

    public CyanQuestion question(Long examId, Long questionId) {
        return questions.selectSnapshots(examId).stream().filter(q -> questionId.equals(q.getQuestionId())
                && "PYTHON".equals(q.getQuestionType())).findFirst().orElseThrow(() -> new ServiceException("编程题快照不存在"));
    }
    public List<Map<String, Object>> history(Long examId, Long questionId) {
        if (!students.selectExamIdsByUserId(scope.actorId()).contains(examId)) throw new ServiceException("无权查看", 403);
        return submissions.history(examId, scope.actorId(), questionId).stream().map(this::safe).toList();
    }
    public Map<String, Object> get(Long id) {
        Map<String, Object> row = submissions.get(id);
        if (row == null || !scope.actorId().equals(((Number) row.get("userId")).longValue())) throw new ServiceException("无权查看提交", 403);
        return safe(row);
    }

    @Transactional
    public void retry(Long id) {
        Map<String, Object> row = submissions.get(id);
        if (row == null) throw new ServiceException("提交不存在");
        Long examId = ((Number) row.get("examId")).longValue();
        scope.requireAccess(exams.selectCyanExamInfoByExamId(examId));
        results.lockForGrading(examId);
        if (!"FAILED".equals(row.get("state"))) throw new ServiceException("只有系统失败任务允许重试");
        regrade(id, ((Number) row.get("runVersion")).intValue(), "系统异常重试");
    }

    @Transactional
    public void regrade(Long id, int version, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw new ServiceException("必须填写不超过500字的重评原因");
        Map<String, Object> row = submissions.get(id);
        if (row == null) throw new ServiceException("提交不存在");
        Long examId = ((Number) row.get("examId")).longValue();
        Long userId = ((Number) row.get("userId")).longValue();
        Long questionId = ((Number) row.get("questionId")).longValue();
        scope.requireAccess(exams.selectCyanExamInfoByExamId(examId));
        results.lockForGrading(examId);
        sessions.lockByExamAndUser(examId, userId);
        row = submissions.lock(id);
        if (((Number) row.get("runVersion")).intValue() != version
                || !Set.of("FAILED", "SUCCEEDED").contains(row.get("state")))
            throw new ServiceException("评测版本已变化或任务正在运行，请刷新后重试");
        runner.requireConfigured();
        if (submissions.queueSize() >= 2000) throw new ServiceException("评测队列繁忙");
        if (submissions.archive(id, version, scope.actorId(), reason.trim()) != 1
                || submissions.regrade(id, version) != 1) throw new ServiceException("重评入队失败");
        Map<String, Object> latest = submissions.latestFormal(examId, userId, questionId);
        // Re-evaluating an earlier submission must never change the selected final submission.
        if ("FORMAL".equals(row.get("mode")) && latest != null
                && id.equals(((Number) latest.get("id")).longValue()))
            answers.clearCodeGrade(examId, userId, questionId);
    }

    public List<Map<String, Object>> teacherHistory(Long examId, Long userId, Long questionId) {
        scope.requireAccess(exams.selectCyanExamInfoByExamId(examId));
        List<Map<String, Object>> history = submissions.history(examId, userId, questionId);
        for (Map<String, Object> row : history) {
            row.remove("progress");
            row.put("evaluations", submissions.evaluations(((Number) row.get("id")).longValue()));
        }
        return history;
    }

    private Map<String, Object> safe(Map<String, Object> row) {
        Map<String, Object> output = new LinkedHashMap<>();
        for (String key : List.of("id", "questionId", "mode", "state", "sourceHash", "snapshotHash", "receivedAt", "runVersion")) output.put(key, row.get(key));
        // Formal diagnostics can echo hidden inputs. Only sample output is ever returned to students.
        if ("SAMPLE".equals(row.get("mode")) && row.get("feedback") != null) output.put("feedback", JSON.parse((String) row.get("feedback")));
        if ("FORMAL".equals(row.get("mode")) && "SUCCEEDED".equals(row.get("state")))
            output.put("score", row.get("score"));
        return output;
    }
}
