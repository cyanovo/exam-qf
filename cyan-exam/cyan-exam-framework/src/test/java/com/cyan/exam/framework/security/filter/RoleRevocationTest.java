package com.cyan.exam.framework.security.filter;

import com.cyan.exam.common.core.domain.entity.SysRole;
import com.cyan.exam.common.core.domain.entity.SysUser;
import com.cyan.exam.common.core.domain.model.LoginUser;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.framework.web.service.SysPermissionService;
import com.cyan.exam.framework.web.service.TokenService;
import com.cyan.exam.system.service.ISysUserService;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleRevocationTest {
    @Mock TokenService tokenService;
    @Mock ISysUserService userService;
    @Mock SysPermissionService permissionService;
    @InjectMocks JwtAuthenticationTokenFilter filter;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void existingSessionUsesCurrentMembershipAndEnabledRoles() throws Exception {
        var old = new SysUser(7L);
        var login = new LoginUser(7L, 100L, old, Set.of("system:role:edit"));
        var current = new SysUser(7L);
        current.setStatus("0");
        current.setDelFlag("0");
        current.setPwdUpdateDate(new Date());
        var disabled = new SysRole(9L);
        disabled.setRoleKey("judge");
        disabled.setStatus("1");
        current.setRoles(List.of(disabled));
        when(tokenService.getLoginUser(any())).thenReturn(login);
        when(userService.selectUserById(7L)).thenReturn(current);
        when(permissionService.getMenuPermission(current)).thenReturn(Set.of());
        filter.doFilter(new MockHttpServletRequest("GET", "/system/role/list"),
                new MockHttpServletResponse(), (request, response) -> {
                    assertTrue(SecurityUtils.getLoginUser().getPermissions().isEmpty());
                    assertTrue(SecurityUtils.getLoginUser().getUser().getRoles().isEmpty());
                });
        assertSame(current, login.getUser());
    }
}
