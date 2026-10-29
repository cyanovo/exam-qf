package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.core.domain.entity.SysUser;
import com.cyan.exam.common.core.domain.model.LoginUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

abstract class AuthenticatedExamTest {
    @BeforeEach
    void authenticateTeacher() {
        login(9L);
    }

    protected void login(long userId) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("user-" + userId);
        LoginUser principal = new LoginUser(userId, 1L, user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
