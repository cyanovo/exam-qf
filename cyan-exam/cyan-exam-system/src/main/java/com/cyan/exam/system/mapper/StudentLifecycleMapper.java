package com.cyan.exam.system.mapper;

public interface StudentLifecycleMapper {
    int releaseDeletedStudentByUserId(Long userId);
    int releaseDeletedStudentByNumber(String studentNo);
}
