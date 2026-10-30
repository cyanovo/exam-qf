package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.core.domain.entity.SysRole;
import com.cyan.exam.system.mapper.SysRoleMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessRoleTest {
    @Mock SysRoleMapper roles;
    @InjectMocks SysRoleServiceImpl service;

    @Test void disabledRoleDoesNotGrantRoleIdentity() {
        var disabled = new SysRole(10L);
        disabled.setRoleKey("judge");
        disabled.setStatus("1");
        var enabled = new SysRole(11L);
        enabled.setRoleKey("student");
        enabled.setStatus("0");
        when(roles.selectRolePermissionByUserId(7L)).thenReturn(List.of(disabled, enabled));
        assertEquals(Set.of("student"), service.selectRolePermissionByUserId(7L));
    }
}
