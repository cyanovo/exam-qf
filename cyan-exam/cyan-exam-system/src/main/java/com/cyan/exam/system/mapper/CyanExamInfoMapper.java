package com.cyan.exam.system.mapper;

import java.util.List;
import com.cyan.exam.system.domain.CyanExamInfo;

public interface CyanExamInfoMapper {
    List<CyanExamInfo> selectCyanExamInfoList(CyanExamInfo examInfo);
    CyanExamInfo selectCyanExamInfoByExamId(Long examId);
    CyanExamInfo lockByExamId(Long examId);
    int publish(Long examId);
    int insertCyanExamInfo(CyanExamInfo examInfo);
    int updateCyanExamInfo(CyanExamInfo examInfo);
    int deleteCyanExamInfoByExamId(Long examId);
    int deleteCyanExamInfoByExamIds(Long[] examIds);
}
