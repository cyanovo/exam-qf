package com.cyan.exam.system.service.impl;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.domain.ExamAnswerRecord;
import com.cyan.exam.system.domain.ExamSession;
import com.cyan.exam.system.mapper.ExamAnswerRecordMapper;
import com.cyan.exam.system.mapper.ExamQuestionMapper;
import com.cyan.exam.system.mapper.ExamSessionMapper;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamAutoGradingService {
    private final ExamSessionMapper sessions;
    private final ExamQuestionMapper questions;
    private final ExamAnswerRecordMapper answers;
    private final com.cyan.exam.system.service.ExamResultService resultService;
    private final com.cyan.exam.system.mapper.CodeSubmissionMapper codeSubmissions;

    public ExamAutoGradingService(ExamSessionMapper sessions, ExamQuestionMapper questions,
                                 ExamAnswerRecordMapper answers,
                                 com.cyan.exam.system.service.ExamResultService resultService,
                                 com.cyan.exam.system.mapper.CodeSubmissionMapper codeSubmissions) {
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
        this.resultService = resultService;
        this.codeSubmissions = codeSubmissions;
    }

    @Transactional
    public void gradeSubmitted(Long examId, Long userId) {
        resultService.lockForGrading(examId);
        // Match manual grading's lock order so retries cannot overwrite a teacher's grade.
        ExamSession session = sessions.lockByExamAndUser(examId, userId);
        if (session == null || !Set.of("SUBMITTED", "AUTO_SUBMITTED", "GRADED").contains(session.getStatus())) {
            return;
        }
        answers.insertMissingSubmittedAnswers(examId, userId);
        Map<Long, CyanQuestion> snapshots = questions.selectSnapshots(examId).stream()
                .collect(Collectors.toMap(CyanQuestion::getQuestionId, q -> q));
        for (ExamAnswerRecord record : answers.selectByExamAndUser(examId, userId)) {
            CyanQuestion question = snapshots.get(record.getQuestionId());
            if (question == null || record.getGradedAt() != null) {
                continue;
            }
            Integer score;
            if ("PYTHON".equals(question.getQuestionType())) {
                Map<String, Object> latest = codeSubmissions.latestFormal(examId, userId, question.getQuestionId());
                score = latest == null ? Integer.valueOf(0) : "SUCCEEDED".equals(latest.get("state"))
                        ? ((Number) latest.get("score")).intValue() : null;
            } else {
                score = calculateScore(question, record.getAnswer());
            }
            if (score == null) {
                continue;
            }
            // Actor 0 is reserved for system scoring, never a teacher account.
            if (answers.insertScoreRevision(record, score, 0L, "系统自动判分 v1") != 1
                    || answers.saveGrade(record.getRecordId(), score) != 1) {
                throw new ServiceException("自动判分保存失败");
            }
        }
    }

    static Integer calculateScore(CyanQuestion question, String answer) {
        if (question.getMaxScore() == null || question.getMaxScore() < 1) {
            throw new ServiceException("题目分值配置无效");
        }
        if (answer == null || answer.isBlank()) {
            return 0;
        }
        String type = question.getQuestionType();
        if (!Set.of("SINGLE_CHOICE", "MULTI_CHOICE", "JUDGE").contains(type)) {
            return null;
        }
        Set<String> available = new HashSet<>();
        Map<String, Set<String>> contentLabels = new HashMap<>();
        try {
            List<Map> options = JSON.parseArray(question.getSubTitle(), Map.class);
            if (options == null) {
                throw new IllegalArgumentException();
            }
            for (Map option : options) {
                if (option == null || option.get("label") == null
                        || !available.add(option.get("label").toString().trim())) {
                    throw new IllegalArgumentException();
                }
                if (option.get("content") != null) {
                    contentLabels.computeIfAbsent(option.get("content").toString().trim(), key -> new HashSet<>())
                            .add(option.get("label").toString().trim());
                }
            }
        } catch (RuntimeException ex) {
            throw new ServiceException("题目选项配置无效，需人工复核");
        }
        String reference = question.getAnswer();
        if ("JUDGE".equals(type)) {
            reference = judgeLabel(reference, available, contentLabels);
            answer = judgeLabel(answer, available, contentLabels);
        }
        Set<String> expected = labels(reference);
        if (expected == null || !available.containsAll(expected)
                || (!"MULTI_CHOICE".equals(type) && expected.size() != 1)) {
            throw new ServiceException("题目标准答案配置无效，需人工复核");
        }
        Set<String> actual = labels(answer);
        return expected.equals(actual) ? question.getMaxScore() : 0;
    }

    private static String judgeLabel(String value, Set<String> available, Map<String, Set<String>> contentLabels) {
        if (value == null || available.contains(value.trim())) {
            return value;
        }
        Set<String> matches = contentLabels.get(value.trim());
        if (matches != null && matches.size() == 1) {
            return matches.iterator().next();
        }
        // Ambiguous legacy text must be reviewed, never interpreted as a wrong answer.
        if (matches != null) {
            throw new ServiceException("判断题历史答案存在歧义，需人工复核");
        }
        return value;
    }

    private static Set<String> labels(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (String label : value.split(",", -1)) {
            if (label.trim().isEmpty() || !result.add(label.trim())) {
                return null;
            }
        }
        return result;
    }
}
