package com.cyan.exam.system.service;

import com.cyan.exam.common.constant.HttpStatus;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.domain.CyanExamInfo;
import org.springframework.stereotype.Component;

/**
 * Resource checks supplement controller action permissions.
 * Student access remains governed separately by the exam roster.
 */
@Component
public class ExamAccessScope {
    public Long actorId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    public void restrictList(CyanExamInfo filter) {
        Long userId = actorId();
        // Always replace caller-supplied scope before SQL pagination/export.
        filter.setOwnerUserId(SecurityUtils.isAdmin(userId) ? null : userId);
    }

    public void requireAccess(CyanExamInfo exam) {
        Long userId = actorId();
        if (exam == null || (!SecurityUtils.isAdmin(userId)
                && !userId.equals(exam.getOwnerUserId()))) {
            throw new ServiceException("考试不存在或无权访问", HttpStatus.FORBIDDEN);
        }
    }
}
