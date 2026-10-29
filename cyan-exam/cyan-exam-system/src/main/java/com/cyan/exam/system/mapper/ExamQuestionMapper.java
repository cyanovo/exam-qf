package com.cyan.exam.system.mapper;

import java.util.List;
import com.cyan.exam.system.domain.CyanQuestion;
import org.apache.ibatis.annotations.Param;

public interface ExamQuestionMapper {
    int batchInsertExamQuestion(@Param("examId") Long examId, @Param("questionIds") List<Long> questionIds);
    int deleteExamQuestionByExamId(Long examId);
    List<Long> selectQuestionIdsByExamId(Long examId);
    int insertSnapshots(@Param("examId") Long examId, @Param("questions") List<CyanQuestion> questions);
    List<CyanQuestion> selectSnapshots(Long examId);
    int containsSnapshot(@Param("examId") Long examId, @Param("questionId") Long questionId);
    List<CyanQuestion> selectAssignedQuestions(Long examId);
    int updatePoints(@Param("examId") Long examId, @Param("questionId") Long questionId, @Param("points") int points);
}
