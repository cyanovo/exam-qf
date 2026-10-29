package com.cyan.exam.system.service;

import java.util.List;
import java.util.Map;
import com.cyan.exam.system.domain.ExamSession;

public interface IExamStudentService {
    Map<String, Object> getWaitInfo(Long userId);
    List<Map<String, Object>> getQuestionsWithAnswers(Long examId, Long userId);
    void submitAnswer(Long examId, Long userId, Long questionId, String answer);
    ExamSession startSession(Long examId, Long userId);
    ExamSession finishSession(Long examId, Long userId);
}
