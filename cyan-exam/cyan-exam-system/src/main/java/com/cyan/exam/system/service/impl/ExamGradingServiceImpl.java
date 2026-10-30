package com.cyan.exam.system.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.cyan.exam.system.service.IExamGradingService;
import com.cyan.exam.system.service.ExamAccessScope;
import com.cyan.exam.common.constant.HttpStatus;
import com.cyan.exam.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.core.domain.entity.SysUser;
import com.cyan.exam.system.domain.*;
import com.cyan.exam.system.mapper.*;

@Service
public class ExamGradingServiceImpl implements IExamGradingService {

    @Autowired
    private CyanExamInfoMapper examInfoMapper;
    @Autowired
    private ExamStudentMapper examStudentMapper;
    @Autowired
    private ExamQuestionMapper examQuestionMapper;
    @Autowired
    private ExamAnswerRecordMapper answerRecordMapper;
    @Autowired
    private SysUserMapper userMapper;   // 新增：用于查询学生姓名
    @Autowired
    private ExamSessionMapper sessionMapper;
    @Autowired
    private ExamAccessScope accessScope;
    @Autowired
    private com.cyan.exam.system.service.ExamResultService resultService;
    @Autowired
    private CodeSubmissionMapper codeSubmissions;

    @Override
    public List<Map<String, Object>> getExamListForGrading() {
        CyanExamInfo filter = new CyanExamInfo();
        accessScope.restrictList(filter);
        List<CyanExamInfo> exams = examInfoMapper.selectCyanExamInfoList(filter);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CyanExamInfo exam : exams) {
            if (exam.getPublishedAt() == null) {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("examId", exam.getExamId());
            map.put("examName", exam.getExamName());
            map.put("startTime", exam.getStartTime());
            map.put("endTime", exam.getEndTime());
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getStudentProgress(Long examId) {
        CyanExamInfo exam = examInfoMapper.selectCyanExamInfoByExamId(examId);
        accessScope.requireAccess(exam);
        if (exam == null || exam.getPublishedAt() == null) {
            throw new ServiceException("考试未发布或不存在");
        }
        List<Long> userIds = examStudentMapper.selectUserIdsByExamId(examId);
        List<CyanQuestion> snapshots = examQuestionMapper.selectSnapshots(examId);
        int totalQuestions = snapshots.size();
        Set<Long> questionIds = snapshots.stream().map(CyanQuestion::getQuestionId).collect(Collectors.toSet());
        int maxScore = snapshots.stream().mapToInt(q -> q.getMaxScore() == null ? 0 : q.getMaxScore()).sum();

        List<Map<String, Object>> progressList = new ArrayList<>();
        for (Long userId : userIds) {
            List<ExamAnswerRecord> records = answerRecordMapper.selectByExamAndUser(examId, userId).stream()
                    .filter(r -> questionIds.contains(r.getQuestionId())).collect(Collectors.toList());
            int answered = (int) records.stream().filter(r -> r.getAnswer() != null && !r.getAnswer().isBlank()).count();
            int graded = (int) records.stream().filter(r -> r.getGradedAt() != null).count();
            ExamSession session = sessionMapper.selectByExamAndUser(examId, userId);
            SysUser user = userMapper.selectUserById(userId);
            String studentName = user != null ? user.getNickName() : "未知";
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userId);
            map.put("studentName", studentName);
            map.put("answered", answered);
            map.put("total", totalQuestions);
            map.put("graded", graded);
            map.put("pending", totalQuestions - graded);
            map.put("score", records.stream().filter(r -> r.getGradedAt() != null && r.getScore() != null)
                    .mapToInt(ExamAnswerRecord::getScore).sum());
            map.put("maxScore", maxScore);
            map.put("gradingComplete", totalQuestions > 0 && graded == totalQuestions
                    && session != null && !"IN_PROGRESS".equals(session.getStatus()));
            map.put("sessionStatus", session == null ? "NOT_STARTED" : session.getStatus());
            progressList.add(map);
        }
        return progressList;
    }

    @Override
    public Map<String, Object> getStudentAnswerDetail(Long examId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        CyanExamInfo exam = examInfoMapper.selectCyanExamInfoByExamId(examId);
        accessScope.requireAccess(exam);
        if (exam == null || exam.getPublishedAt() == null
                || !examStudentMapper.selectUserIdsByExamId(examId).contains(userId)) {
            throw new ServiceException("考试或考生不存在");
        }
        result.put("examName", exam.getExamName());
        result.put("resultsLocked", resultService.isLocked(examId));
        ExamSession session = sessionMapper.selectByExamAndUser(examId, userId);
        result.put("sessionStatus", session == null ? "NOT_STARTED" : session.getStatus());

        List<CyanQuestion> questions = examQuestionMapper.selectSnapshots(examId);

        List<ExamAnswerRecord> records = answerRecordMapper.selectByExamAndUser(examId, userId);
        Map<Long, ExamAnswerRecord> recordMap = records.stream()
                .collect(Collectors.toMap(ExamAnswerRecord::getQuestionId, r -> r));

        List<Map<String, Object>> questionDetails = new ArrayList<>();
        for (CyanQuestion q : questions) {
            Map<String, Object> item = new HashMap<>();
            item.put("questionId", q.getQuestionId());
            item.put("title", q.getTitle());
            item.put("type", q.getQuestionType());
            item.put("maxScore", q.getMaxScore());
            item.put("referenceAnswer", q.getAnswer());
            item.put("gradingRules", q.getGradingRules());
            if ("SINGLE_CHOICE".equals(q.getQuestionType()) || "MULTI_CHOICE".equals(q.getQuestionType())
                    || "JUDGE".equals(q.getQuestionType())) {
                if (q.getSubTitle() != null && !q.getSubTitle().isEmpty()) {
                    item.put("options", JSON.parseArray(q.getSubTitle()));
                }
            }
            ExamAnswerRecord record = recordMap.get(q.getQuestionId());
            item.put("studentAnswer", record != null ? record.getAnswer() : "");
            if ("PYTHON".equals(q.getQuestionType())) {
                Map<String, Object> formal = codeSubmissions.latestFormal(examId, userId, q.getQuestionId());
                item.put("studentAnswer", formal == null ? "" : formal.get("source"));
                item.put("formalSubmissionId", formal == null ? null : formal.get("id"));
                item.put("evaluationState", formal == null ? "NOT_SUBMITTED" : formal.get("state"));
                item.put("runVersion", formal == null ? null : formal.get("runVersion"));
            }
            item.put("score", record != null && record.getGradedAt() != null ? record.getScore() : null);
            item.put("recordId", record != null ? record.getRecordId() : null);

            boolean autoGradable = "SINGLE_CHOICE".equals(q.getQuestionType())
                    || "JUDGE".equals(q.getQuestionType());
            item.put("autoGradable", autoGradable);

            questionDetails.add(item);
        }

        result.put("questions", questionDetails);
        result.put("revisions", answerRecordMapper.selectScoreRevisions(examId, userId));
        // 可选：增加学生姓名
        SysUser student = userMapper.selectUserById(userId);
        result.put("studentName", student != null ? student.getNickName() : "未知");
        return result;
    }

    @Override
    @Transactional
    public void submitScores(ExamScoreSubmission submission, Long graderId) {
        if (submission == null || submission.getExamId() == null || submission.getUserId() == null
                || graderId == null || submission.getScores() == null || submission.getScores().isEmpty()) {
            throw new ServiceException("批改参数不完整");
        }
        Long examId = submission.getExamId();
        Long userId = submission.getUserId();
        if (!accessScope.actorId().equals(graderId)) {
            throw new ServiceException("批改人身份不匹配", HttpStatus.FORBIDDEN);
        }
        CyanExamInfo exam = examInfoMapper.selectCyanExamInfoByExamId(examId);
        accessScope.requireAccess(exam);
        resultService.lockForGrading(examId);
        ExamSession session = sessionMapper.lockByExamAndUser(examId, userId);
        if (session == null || !("SUBMITTED".equals(session.getStatus())
                || "AUTO_SUBMITTED".equals(session.getStatus())
                || "GRADED".equals(session.getStatus()))) {
            throw new ServiceException("考生尚未交卷，不能批改");
        }
        if (exam == null || exam.getPublishedAt() == null
                || !examStudentMapper.selectUserIdsByExamId(examId).contains(userId)) {
            throw new ServiceException("考试或考生不存在");
        }
        Map<Long, CyanQuestion> questions = examQuestionMapper.selectSnapshots(examId).stream()
                .collect(Collectors.toMap(CyanQuestion::getQuestionId, q -> q));
        Set<Long> seen = new HashSet<>();
        for (ExamScoreSubmission.Item item : submission.getScores()) {
            if (item == null || item.getRecordId() == null || !seen.add(item.getRecordId())
                    || item.getScore() == null) {
                throw new ServiceException("批改记录重复或分数为空");
            }
            ExamAnswerRecord record = answerRecordMapper.lockByRecordId(item.getRecordId());
            CyanQuestion question = record == null ? null : questions.get(record.getQuestionId());
            if (record == null || !examId.equals(record.getExamId())
                    || !userId.equals(record.getUserId()) || question == null
                    || question.getMaxScore() == null || item.getScore() < 0
                    || item.getScore() > question.getMaxScore()) {
                throw new ServiceException("批改记录不属于该场考试或得分超出题目满分");
            }
            if (record.getGradedAt() != null && item.getScore().equals(record.getScore())) {
                continue;
            }
            if ("PYTHON".equals(question.getQuestionType())) {
                throw new ServiceException("编程题由独立评测计分，系统失败请使用重试入口");
            }
            String reason = "首次批改";
            if (record.getGradedAt() != null) {
                reason = submission.getReason() == null ? "" : submission.getReason().trim();
                if (reason.isEmpty() || reason.length() > 500) {
                    throw new ServiceException("修改已批改分数必须填写不超过 500 字的原因");
                }
            }
            if (answerRecordMapper.insertScoreRevision(record, item.getScore(), graderId, reason) != 1
                    || answerRecordMapper.saveGrade(record.getRecordId(), item.getScore()) != 1) {
                throw new ServiceException("保存批改分数失败");
            }
        }
    }
}
