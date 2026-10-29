package com.cyan.exam.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.cyan.exam.system.domain.ExamAnswerRecord;

public interface ExamAnswerRecordMapper {
    List<ExamAnswerRecord> selectExamAnswerRecordList(ExamAnswerRecord record);
    ExamAnswerRecord selectByExamUserQuestion(ExamAnswerRecord record);
    List<ExamAnswerRecord> selectByExamAndUser(@Param("examId") Long examId, @Param("userId") Long userId);
    int insertOrUpdate(ExamAnswerRecord record);
    int insertMissingSubmittedAnswers(@Param("examId") Long examId, @Param("userId") Long userId);
    ExamAnswerRecord lockByRecordId(Long recordId);
    int saveGrade(@Param("recordId") Long recordId, @Param("score") int score);
    int insertScoreRevision(@Param("record") ExamAnswerRecord record, @Param("score") int score,
                            @Param("graderId") Long graderId, @Param("reason") String reason);
    List<java.util.Map<String, Object>> selectScoreRevisions(@Param("examId") Long examId,
                                                               @Param("userId") Long userId);
    int updateExamAnswerRecord(ExamAnswerRecord record);
    int deleteExamAnswerRecordByRecordId(Long recordId);
    int deleteExamAnswerRecordByRecordIds(Long[] recordIds);
    int deleteByExamAndUser(@Param("examId") Long examId, @Param("userId") Long userId);
}
