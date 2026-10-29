package com.cyan.exam.system.mapper;

import com.cyan.exam.system.domain.ExamSession;
import org.apache.ibatis.annotations.Param;

public interface ExamSessionMapper {
    ExamSession selectByExamAndUser(@Param("examId") Long examId, @Param("userId") Long userId);
    ExamSession lockByExamAndUser(@Param("examId") Long examId, @Param("userId") Long userId);
    int createIfAbsent(@Param("examId") Long examId, @Param("userId") Long userId);
    int finish(@Param("sessionId") Long sessionId, @Param("status") String status);
    int autoFinishExpired();
    java.util.List<ExamSession> selectPendingAutoGrades(@Param("afterId") Long afterId);
}
