package com.cyan.exam.system.service;

import java.util.List;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.domain.CyanQuestion;
import java.util.Map;

public interface ICyanExamInfoService {
    List<CyanExamInfo> selectCyanExamInfoList(CyanExamInfo examInfo);
    CyanExamInfo selectCyanExamInfoByExamId(Long examId);
    int insertCyanExamInfo(CyanExamInfo examInfo);
    int updateCyanExamInfo(CyanExamInfo examInfo);
    void publish(Long examId);
    int deleteCyanExamInfoByExamIds(Long[] examIds);
    // 查询已分配学生ID
    List<Long> selectStudentIdsByExamId(Long examId);
    // 保存学生分配
    void saveExamStudents(Long examId, List<Long> userIds);
    // 查询已分配试题ID
    List<Long> selectQuestionIdsByExamId(Long examId);
    // 保存试题选择
    void saveExamQuestions(Long examId, List<Long> questionIds);
    List<CyanQuestion> selectAssignedQuestions(Long examId);
    void saveQuestionPoints(Long examId, Map<Long, Integer> points);
}
