package com.cyan.exam.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ExamStudentMapper {
    int batchInsertExamStudent(@Param("examId") Long examId, @Param("userIds") List<Long> userIds);
    int deleteExamStudentByExamId(Long examId);
    List<Long> selectUserIdsByExamId(Long examId);
    List<Long> selectExamIdsByUserId(Long userId);
}