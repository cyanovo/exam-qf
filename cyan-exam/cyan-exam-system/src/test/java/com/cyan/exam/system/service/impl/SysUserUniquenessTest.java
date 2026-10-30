package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.core.domain.entity.SysUser;
import com.cyan.exam.system.mapper.SysUserMapper;
import com.cyan.exam.system.service.ISysDeptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysUserUniquenessTest extends AuthenticatedExamTest {
    @Mock SysUserMapper userMapper;
    @Mock ISysDeptService deptService;
    @InjectMocks SysUserServiceImpl service;

    private SysUser user(Long id) {
        var user = new SysUser();
        user.setUserId(id);
        user.setUserName("STU02");
        user.setPhonenumber("13800000002");
        user.setEmail("student2@example.test");
        user.setDeptId(102L);
        return user;
    }

    private void occupiedBy(Long id) {
        when(userMapper.checkUserNameUnique("STU02")).thenReturn(user(id));
        when(userMapper.checkPhoneUnique("13800000002")).thenReturn(user(id));
        when(userMapper.checkEmailUnique("student2@example.test")).thenReturn(user(id));
    }

    @Test void ownValuesAreNotDuplicates() {
        occupiedBy(91026L);
        assertTrue(service.checkUserNameUnique(user(91026L)));
        assertTrue(service.checkPhoneUnique(user(91026L)));
        assertTrue(service.checkEmailUnique(user(91026L)));
    }

    @Test void anotherUsersValuesRemainDuplicates() {
        occupiedBy(91027L);
        assertFalse(service.checkUserNameUnique(user(91026L)));
        assertFalse(service.checkPhoneUnique(user(91026L)));
        assertFalse(service.checkEmailUnique(user(91026L)));
    }

    @Test void newUserCannotReuseOccupiedValues() {
        occupiedBy(91026L);
        assertFalse(service.checkUserNameUnique(user(null)));
        assertFalse(service.checkPhoneUnique(user(null)));
        assertFalse(service.checkEmailUnique(user(null)));
    }

    @Test void unusedValuesAreAccepted() {
        assertTrue(service.checkUserNameUnique(user(null)));
        assertTrue(service.checkPhoneUnique(user(null)));
        assertTrue(service.checkEmailUnique(user(null)));
    }

    @Test void changingGroupWithUnchangedIdentityReachesUpdate() {
        login(1L);
        occupiedBy(91026L);
        var edited = user(91026L);
        when(userMapper.updateUser(edited)).thenReturn(1);
        assertEquals(1, service.updateUser(edited));
        verify(deptService).checkDeptDataScope(102L);
        verify(userMapper).updateUser(edited);
    }
}
