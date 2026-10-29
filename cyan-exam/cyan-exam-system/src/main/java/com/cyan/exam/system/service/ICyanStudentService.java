package com.cyan.exam.system.service;

import com.cyan.exam.system.domain.CyanStudent;
import com.cyan.exam.system.domain.dto.CyanStudentImportDTO;
import java.util.List;

public interface ICyanStudentService {
    List<CyanStudent> selectCyanStudentList(CyanStudent student);
    CyanStudent selectCyanStudentByStudentId(Long studentId);
    int insertCyanStudent(CyanStudent student);
    int updateCyanStudent(CyanStudent student);
    int deleteCyanStudentByStudentIds(Long[] studentIds);

    String importStudents(CyanStudentImportDTO dto);
}
