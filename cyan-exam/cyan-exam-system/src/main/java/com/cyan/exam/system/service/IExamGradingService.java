package com.cyan.exam.system.service;

import java.util.List;
import java.util.Map;
import com.cyan.exam.system.domain.ExamScoreSubmission;

public interface IExamGradingService {
    List<Map<String, Object>> getExamListForGrading();
    List<Map<String, Object>> getStudentProgress(Long examId);
    Map<String, Object> getStudentAnswerDetail(Long examId, Long userId);
    void submitScores(ExamScoreSubmission submission, Long graderId);
}
