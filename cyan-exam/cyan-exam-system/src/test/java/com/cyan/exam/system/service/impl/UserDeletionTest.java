package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserDeletionTest extends AuthenticatedExamTest {
    @Mock SysUserMapper userMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @Mock SysUserPostMapper userPostMapper;
    @Mock StudentLifecycleMapper studentLifecycleMapper;
    @InjectMocks SysUserServiceImpl service;

    @Test void bothEntryPointsUseConditionalCleanupAndReturnUserCount() {
        login(1L);
        when(userMapper.deleteUserByIds(any())).thenReturn(1);
        assertEquals(1, service.deleteUserById(80L));
        var order = inOrder(userMapper, userRoleMapper, userPostMapper, studentLifecycleMapper);
        order.verify(userMapper).deleteUserByIds(any());
        order.verify(userRoleMapper).deleteUserRole(any());
        order.verify(userPostMapper).deleteUserPost(any());
        order.verify(studentLifecycleMapper).releaseDeletedStudentByUserId(80L);
    }

    @Test void adminInBatchRejectsBeforeAnyDeletion() {
        login(1L);
        assertThrows(ServiceException.class, () -> service.deleteUserByIds(new Long[]{80L,1L}));
        verify(userMapper, never()).deleteUserByIds(any());
        verifyNoInteractions(userRoleMapper, userPostMapper, studentLifecycleMapper);
    }
}
