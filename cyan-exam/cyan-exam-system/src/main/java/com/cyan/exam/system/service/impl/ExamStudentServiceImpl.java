package com.cyan.exam.system.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cyan.exam.system.domain.*;
import com.cyan.exam.system.mapper.*;
import com.cyan.exam.system.service.IExamStudentService;
import com.cyan.exam.system.service.ExamPolicy;

@Service
public class ExamStudentServiceImpl implements IExamStudentService {

    @Autowired
    private CyanExamInfoMapper examInfoMapper;
    @Autowired
    private ExamStudentMapper examStudentMapper;
    @Autowired
    private ExamQuestionMapper examQuestionMapper;
    @Autowired
    private ExamAnswerRecordMapper answerRecordMapper;
    @Autowired
    private ExamSessionMapper sessionMapper;

    @Override
    @Transactional
    public ExamSession startSession(Long examId, Long userId) {
        if (!examStudentMapper.selectExamIdsByUserId(userId).contains(examId)) {
            throw new ServiceException("您没有权限参加该考试");
        }
        CyanExamInfo exam = examInfoMapper.selectCyanExamInfoByExamId(examId);
        if (exam == null || exam.getPublishedAt() == null || !"0".equals(exam.getStatus())
                || exam.getStartTime() == null || exam.getEndTime() == null) {
            throw new ServiceException("考试不存在或不可作答");
        }
        Date now = new Date();
        if (now.before(exam.getStartTime())) {
            throw new ServiceException("考试尚未开始");
        }
        if (now.before(exam.getEndTime())) {
            sessionMapper.createIfAbsent(examId, userId);
        }
        ExamSession session = sessionMapper.lockByExamAndUser(examId, userId);
        if (session == null) {
            throw new ServiceException("考试已结束");
        }
        if ("IN_PROGRESS".equals(session.getStatus()) && !now.before(ExamPolicy.deadline(exam, session))) {
            sessionMapper.finish(session.getSessionId(), "AUTO_SUBMITTED");
            session.setStatus("AUTO_SUBMITTED");
        }
        return session;
    }

    @Override
    @Transactional
    public ExamSession finishSession(Long examId, Long userId) {
        if (!examStudentMapper.selectExamIdsByUserId(userId).contains(examId)) {
            throw new ServiceException("您没有权限参加该考试");
        }
        ExamSession session = sessionMapper.lockByExamAndUser(examId, userId);
        if (session == null) {
            throw new ServiceException("考试会话不存在");
        }
        if ("IN_PROGRESS".equals(session.getStatus())) {
            CyanExamInfo exam = examInfoMapper.selectCyanExamInfoByExamId(examId);
            String status = exam != null && exam.getEndTime() != null
                    && new Date().before(ExamPolicy.deadline(exam, session)) ? "SUBMITTED" : "AUTO_SUBMITTED";
            sessionMapper.finish(session.getSessionId(), status);
            session.setStatus(status);
        }
        return session;
    }

    @Override
    public Map<String, Object> getWaitInfo(Long userId) {
        Map<String, Object> result = new HashMap<>();
        // 查询学生已分配的所有考试ID
        List<Long> examIds = examStudentMapper.selectExamIdsByUserId(userId);  // 需补充
        if (examIds.isEmpty()) {
            result.put("status", "none");
            return result;
        }
        // 获取这些考试中正在进行或即将开始的
        Date now = new Date();
        List<CyanExamInfo> exams = examInfoMapper.selectCyanExamInfoList(new CyanExamInfo());
        exams = exams.stream()
                .filter(e -> examIds.contains(e.getExamId()) && e.getPublishedAt() != null && "0".equals(e.getStatus())
                        && e.getStartTime() != null && e.getEndTime() != null && e.getEndTime().after(now))
                .filter(e -> {
                    ExamSession session = sessionMapper.selectByExamAndUser(e.getExamId(), userId);
                    return session == null || "IN_PROGRESS".equals(session.getStatus());
                })
                .sorted(Comparator.comparing(CyanExamInfo::getStartTime))
                .collect(Collectors.toList());

        if (exams.isEmpty()) {
            result.put("status", "none");
            return result;
        }
        CyanExamInfo target = exams.get(0);
        result.put("startTime", target.getStartTime());
        result.put("instructions", ExamPolicy.read(target).instructions());
        result.put("entryAt", new Date(target.getStartTime().getTime() - target.getEarlyEntryMinutes() * 60000L));
        result.put("canEnter", System.currentTimeMillis() >= target.getStartTime().getTime() - target.getEarlyEntryMinutes() * 60000L);
        result.put("durationMinutes", target.getDurationMinutes());
        if (target.getStartTime().before(now) && target.getEndTime().after(now)) {
            result.put("status", "ongoing");
            result.put("examId", target.getExamId());
            result.put("examName", target.getExamName());
            result.put("endTime", target.getEndTime());
        } else if (target.getStartTime().after(now)) {
            result.put("status", "upcoming");
            result.put("examId", target.getExamId());
            result.put("examName", target.getExamName());
            result.put("startTime", target.getStartTime());
            result.put("endTime", target.getEndTime());
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getQuestionsWithAnswers(Long examId, Long userId) {
        List<CyanQuestion> questions = examQuestionMapper.selectSnapshots(examId);
        List<ExamAnswerRecord> records = answerRecordMapper.selectByExamAndUser(examId, userId);
        Map<Long, String> answerMap = records.stream()
                .collect(Collectors.toMap(ExamAnswerRecord::getQuestionId, ExamAnswerRecord::getAnswer));

        List<Map<String, Object>> result = new ArrayList<>();
        for (CyanQuestion q : questions) {
            Map<String, Object> item = new HashMap<>();
            item.put("questionId", q.getQuestionId());
            item.put("title", q.getTitle());
            item.put("questionType", q.getQuestionType());
            item.put("difficulty", q.getDifficulty());
            item.put("category", q.getCategory());
            item.put("maxScore", q.getMaxScore());
            if ("PYTHON".equals(q.getQuestionType())) {
                var rules = com.cyan.exam.system.service.coding.CodingRules.parse(q.getAnswer(), q.getMaxScore());
                item.put("description", q.getSubTitle());
                item.put("initialCode", q.getInitialCode() == null ? "" : q.getInitialCode());
                item.put("snapshotHash", com.cyan.exam.system.service.coding.CodingRules.hash(q.getAnswer()));
                item.put("samples", rules.cases().stream().filter(c -> c.publicCase())
                        .map(c -> Map.of("input", c.input(), "expected", c.expected())).toList());
            } else if (q.getSubTitle() != null && !q.getSubTitle().isEmpty()) {
                List<Map> storedOptions = JSON.parseArray(q.getSubTitle(), Map.class);
                List<Map<String, Object>> publicOptions = new ArrayList<>();
                for (Map option : storedOptions) {
                    Map<String, Object> publicOption = new HashMap<>();
                    publicOption.put("label", option.get("label"));
                    publicOption.put("content", option.get("content"));
                    publicOptions.add(publicOption);
                }
                item.put("options", publicOptions);
            }
            item.put("userAnswer", answerMap.getOrDefault(q.getQuestionId(), ""));
            result.add(item);
        }
        var policy = ExamPolicy.read(examInfoMapper.selectCyanExamInfoByExamId(examId));
        Map<Long, Integer> order = new HashMap<>();
        for (int i=0;i<policy.entries().size();i++) {
            var entry=policy.entries().get(i);
            if ("QUESTION".equals(entry.kind())) {
                order.put(entry.id(), i);
                result.stream().filter(q->entry.id().equals(q.get("questionId"))).forEach(q->{
                    q.put("section",entry.section()); q.put("sectionDescription",entry.description()); q.put("hint",entry.hint());
                });
            }
        }
        result.sort(Comparator.comparingInt(q->order.getOrDefault((Long)q.get("questionId"),Integer.MAX_VALUE)));
        return result;
    }

    @Override
    @Transactional
    public void submitAnswer(Long examId, Long userId, Long questionId, String answer) {
        if (answer == null || answer.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000) {
            throw new ServiceException("答案不能为空值或超过60KB");
        }
        if (!examStudentMapper.selectExamIdsByUserId(userId).contains(examId)) {
            throw new ServiceException("您没有权限参加该考试");
        }
        ExamSession session = sessionMapper.lockByExamAndUser(examId, userId);
        if (session == null || !"IN_PROGRESS".equals(session.getStatus())) {
            throw new ServiceException("考试会话未开始或已交卷");
        }
        CyanExamInfo exam = examInfoMapper.selectCyanExamInfoByExamId(examId);
        if (exam == null || exam.getPublishedAt() == null || !"0".equals(exam.getStatus())
                || exam.getStartTime() == null || exam.getEndTime() == null) {
            throw new ServiceException("考试不存在或不可作答");
        }
        Date now = new Date();
        if (now.before(exam.getStartTime()) || !now.before(ExamPolicy.deadline(exam, session))) {
            throw new ServiceException("考试尚未开始或已结束");
        }
        if (examQuestionMapper.containsSnapshot(examId, questionId) == 0) {
            throw new ServiceException("题目不属于该考试");
        }
        ExamAnswerRecord record = new ExamAnswerRecord();
        record.setExamId(examId);
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setAnswer(answer);
        if (answerRecordMapper.insertOrUpdate(record) == 0) {
            ExamAnswerRecord saved = answerRecordMapper.selectByExamUserQuestion(record);
            if (saved == null || !Objects.equals(saved.getAnswer(), answer)) {
                throw new ServiceException("答案未保存：考试已结束或无作答权限");
            }
        }
    }
}
