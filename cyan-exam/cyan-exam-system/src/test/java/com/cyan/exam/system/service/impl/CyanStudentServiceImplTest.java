package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.core.domain.entity.SysRole;
import com.cyan.exam.common.core.domain.entity.SysUser;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanStudent;
import com.cyan.exam.system.domain.dto.CyanStudentImportDTO;
import com.cyan.exam.system.mapper.CyanStudentMapper;
import com.cyan.exam.system.mapper.SysRoleMapper;
import com.cyan.exam.system.mapper.StudentLifecycleMapper;
import com.cyan.exam.system.mapper.SysUserRoleMapper;
import com.cyan.exam.system.service.ISysUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CyanStudentServiceImplTest extends AuthenticatedExamTest {
    @Mock CyanStudentMapper studentMapper;
    @Mock ISysUserService userService;
    @Mock SysRoleMapper roleMapper;
    @Mock StudentLifecycleMapper studentLifecycleMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @InjectMocks CyanStudentServiceImpl service;

    private CyanStudentImportDTO request(int count) {
        var dto = new CyanStudentImportDTO();
        dto.setPrefix("STU");
        dto.setStartNo(1);
        dto.setCount(count);
        dto.setPassword("Test123456!");
        return dto;
    }

    private void studentRole() {
        when(roleMapper.selectRoleList(any())).thenReturn(List.of(new SysRole(3L)));
    }

    @Test void historicalStudentNumberIsSkippedWithoutTouchingAccount() {
        studentRole();
        when(studentMapper.countByStudentNo("STU1")).thenReturn(1);
        String result = service.importStudents(request(1));
        assertTrue(result.contains("0"));
        assertTrue(result.contains("STU1"));
        verifyNoInteractions(userService);
        verify(studentMapper, never()).insertCyanStudent(any());
    }

    @Test void mixedBatchCreatesOnlyUnusedNumberAndRetrySkipsAll() {
        studentRole();
        when(studentMapper.countByStudentNo("STU1")).thenReturn(1);
        when(studentMapper.countByStudentNo("STU2")).thenReturn(0);
        when(studentMapper.countByStudentNo("STU3")).thenReturn(0, 1);
        when(userService.selectUserByUserName("STU2")).thenReturn(new SysUser());
        when(userService.selectUserByUserName("STU3")).thenReturn(null);
        when(userService.insertUser(any())).thenAnswer(invocation -> {
            invocation.<SysUser>getArgument(0).setUserId(80L);
            return 1;
        });
        String first = service.importStudents(request(3));
        assertTrue(first.contains("成功创建 1"));
        assertTrue(first.contains("跳过 2"));
        verify(studentMapper).insertCyanStudent(argThat(s ->
                "STU3".equals(s.getStudentNo()) && Long.valueOf(80).equals(s.getUserId())));
        verify(userService).insertUser(argThat(u -> Long.valueOf(102).equals(u.getDeptId())));
        String retry = service.importStudents(request(3));
        assertTrue(retry.contains("成功创建 0"));
        verify(userService, times(1)).insertUser(any());
        verify(userService, times(1)).resetUserPwd(eq(80L), anyString());
        verify(userService, never()).insertUserAuth(any(), any());
        verify(userRoleMapper).batchUserRole(argThat(roles -> roles.size() == 1
                && Long.valueOf(80L).equals(roles.get(0).getUserId())
                && Long.valueOf(3L).equals(roles.get(0).getRoleId())));
    }

    @Test void singleCreateAlsoRejectsHistoricalNumberBeforeCreatingUser() {
        when(studentMapper.countByStudentNo("STU1")).thenReturn(1);
        var student = new CyanStudent();
        student.setStudentNo("STU1");
        assertThrows(ServiceException.class, () -> service.insertCyanStudent(student));
        verifyNoInteractions(userService);
    }

    @Test void singleCreateUsesStudentGroup() {
        studentRole();
        var student = new CyanStudent();
        student.setStudentNo("NEW1");
        service.insertCyanStudent(student);
        verify(userService).insertUser(argThat(u -> Long.valueOf(102).equals(u.getDeptId())));
        verify(userService, never()).insertUserAuth(any(), any());
        verify(userRoleMapper).batchUserRole(anyList());
    }

    @Test void concurrentCollisionThrowsForTransactionRollback() {
        studentRole();
        when(studentMapper.insertCyanStudent(any())).thenThrow(new DuplicateKeyException("duplicate"));
        var error = assertThrows(ServiceException.class, () -> service.importStudents(request(1)));
        assertTrue(error.getMessage().contains("回滚"));
    }

    @Test void invalidBatchIsRejectedBeforeAnyWrites() {
        assertThrows(ServiceException.class, () -> service.importStudents(request(201)));
        assertThrows(ServiceException.class, () -> service.importStudents(request(0)));
        var dto = request(1);
        dto.setStartNo(null);
        assertThrows(ServiceException.class, () -> service.importStudents(dto));
        verifyNoInteractions(studentMapper, userService, roleMapper);
    }

    @Test void missingStudentRoleIsFailureNotSuccess() {
        when(roleMapper.selectRoleList(any())).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.importStudents(request(1)));
        verifyNoInteractions(userService);
    }

    @Test void deletionUsesSharedUserLifecycleInsteadOfRemovingHistory() {
        var student = new CyanStudent();
        student.setUserId(80L);
        when(studentMapper.selectCyanStudentByStudentId(8L)).thenReturn(student);
        when(userService.deleteUserByIds(any())).thenReturn(1);
        assertEquals(1, service.deleteCyanStudentByStudentIds(new Long[]{8L}));
        verify(userService).deleteUserByIds(argThat(ids -> java.util.Arrays.equals(ids, new Long[]{80L})));
        verify(studentMapper, never()).deleteCyanStudentByStudentIds(any());
    }

    @Test void creationReclaimsUnusedDeletedNumberBeforeRecheckingIt() {
        studentRole();
        when(studentMapper.countByStudentNo("STU1")).thenReturn(1, 0);
        service.importStudents(request(1));
        var order = inOrder(studentLifecycleMapper, studentMapper);
        order.verify(studentMapper).countByStudentNo("STU1");
        order.verify(studentLifecycleMapper).releaseDeletedStudentByNumber("STU1");
        order.verify(studentMapper).countByStudentNo("STU1");
    }

    @Test void freshNumberDoesNotAcquireCleanupGapLock() {
        studentRole();
        service.importStudents(request(1));
        verifyNoInteractions(studentLifecycleMapper);
    }
}
