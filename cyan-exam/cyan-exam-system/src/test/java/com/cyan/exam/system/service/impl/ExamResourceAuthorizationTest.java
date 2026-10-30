package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.domain.ExamScoreSubmission;
import com.cyan.exam.system.mapper.*;
import com.cyan.exam.system.service.ExamAccessScope;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamResourceAuthorizationTest extends AuthenticatedExamTest {
    @InjectMocks private CyanExamInfoServiceImpl management;
    @InjectMocks private ExamGradingServiceImpl grading;
    @Spy private ExamAccessScope accessScope;
    @Mock private CyanExamInfoMapper infoMapper;
    @Mock private CyanQuestionMapper questionMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private ExamStudentMapper studentMapper;
    @Mock private ExamSessionMapper sessionMapper;
    @Mock private ExamAnswerRecordMapper answerMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private com.cyan.exam.system.service.ExamResultService resultService;
    @Mock private com.cyan.exam.system.service.ExamManagementService configuration;

    private CyanExamInfo exam(Long owner) {
        CyanExamInfo exam = new CyanExamInfo();
        exam.setExamId(5L);
        exam.setOwnerUserId(owner);
        exam.setStartTime(new Date(System.currentTimeMillis() + 600_000));
        return exam;
    }

    private ExamScoreSubmission submission() {
        ExamScoreSubmission request = new ExamScoreSubmission();
        request.setExamId(5L);
        request.setUserId(2L);
        ExamScoreSubmission.Item item = new ExamScoreSubmission.Item();
        item.setRecordId(4L);
        item.setScore(5);
        request.setScores(List.of(item));
        return request;
    }

    @Test
    void ownerAndAdminCanReadButOtherTeachersAndLegacyOwnersAreDenied() {
        CyanExamInfo exam = exam(9L);
        when(infoMapper.selectCyanExamInfoByExamId(5L)).thenReturn(exam);
        assertSame(exam, management.selectCyanExamInfoByExamId(5L));
        login(10L);
        assertEquals(403, assertThrows(ServiceException.class,
                () -> management.selectCyanExamInfoByExamId(5L)).getCode());
        exam.setOwnerUserId(null);
        exam.setCreateBy("user-10");
        assertThrows(ServiceException.class, () -> management.selectCyanExamInfoByExamId(5L));
        login(1L);
        assertSame(exam, management.selectCyanExamInfoByExamId(5L));
        assertThrows(ServiceException.class, () -> management.selectCyanExamInfoByExamId(99L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"roster", "questions", "points", "progress", "answers", "grade"})
    void deniesOtherExamReadsAndGradingBeforeReadingSensitiveData(String action) {
        when(infoMapper.selectCyanExamInfoByExamId(5L)).thenReturn(exam(10L));
        assertThrows(ServiceException.class, () -> {
            switch (action) {
                case "roster" -> management.selectStudentIdsByExamId(5L);
                case "questions" -> management.selectQuestionIdsByExamId(5L);
                case "points" -> management.selectAssignedQuestions(5L);
                case "progress" -> grading.getStudentProgress(5L);
                case "answers" -> grading.getStudentAnswerDetail(5L, 2L);
                case "grade" -> grading.submitScores(submission(), 9L);
                default -> fail(action);
            }
        });
        verifyNoInteractions(studentMapper, examQuestionMapper, answerMapper, sessionMapper, userMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "publish", "delete", "roster", "questions", "points"})
    void deniesOtherExamMutationsBeforeAnyWrites(String action) {
        when(infoMapper.lockByExamId(5L)).thenReturn(exam(10L));
        assertThrows(ServiceException.class, () -> {
            switch (action) {
                case "update" -> management.updateCyanExamInfo(exam(9L));
                case "publish" -> management.publish(5L);
                case "delete" -> management.deleteCyanExamInfoByExamIds(new Long[]{5L});
                case "roster" -> management.saveExamStudents(5L, List.of(2L));
                case "questions" -> management.saveExamQuestions(5L, List.of(3L));
                case "points" -> management.saveQuestionPoints(5L, Map.of(3L, 10));
                default -> fail(action);
            }
        });
        verify(infoMapper, never()).updateCyanExamInfo(any());
        verify(infoMapper, never()).publish(any());
        verify(infoMapper, never()).deleteCyanExamInfoByExamIds(any());
        verifyNoInteractions(studentMapper, examQuestionMapper, questionMapper);
    }

    @Test
    void mixedOwnerBulkDeleteDoesNotStartDeleting() {
        when(infoMapper.lockByExamId(4L)).thenReturn(exam(9L));
        when(infoMapper.lockByExamId(5L)).thenReturn(exam(10L));
        assertThrows(ServiceException.class,
                () -> management.deleteCyanExamInfoByExamIds(new Long[]{5L, 4L}));
        verify(infoMapper, never()).deleteCyanExamInfoByExamIds(any());
        verifyNoInteractions(studentMapper, examQuestionMapper);
    }

    @Test
    void managementAndGradingListsUseTrustedScope() {
        CyanExamInfo filter = new CyanExamInfo();
        filter.setOwnerUserId(10L);
        management.selectCyanExamInfoList(filter);
        assertEquals(9L, filter.getOwnerUserId());
        grading.getExamListForGrading();
        ArgumentCaptor<CyanExamInfo> captor = ArgumentCaptor.forClass(CyanExamInfo.class);
        verify(infoMapper, times(2)).selectCyanExamInfoList(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(q -> Long.valueOf(9L).equals(q.getOwnerUserId())));
        login(1L);
        management.selectCyanExamInfoList(filter);
        assertNull(filter.getOwnerUserId());
    }

    @Test
    void creationOverridesForgedOwnershipAndAuditFields() {
        CyanExamInfo request = exam(10L);
        request.setCreateBy("admin");
        request.setCreateTime(new Date(0));
        when(infoMapper.insertCyanExamInfo(request)).thenReturn(1);
        management.insertCyanExamInfo(request);
        assertEquals(9L, request.getOwnerUserId());
        assertEquals("user-9", request.getCreateBy());
        assertTrue(request.getCreateTime().after(new Date(0)));
    }

    @Test
    void updateCannotTransferOwnershipAndUsesTrustedAuditFields() {
        when(infoMapper.lockByExamId(5L)).thenReturn(exam(9L));
        CyanExamInfo request = exam(10L);
        request.setUpdateBy("admin");
        management.updateCyanExamInfo(request);
        assertEquals(9L, request.getOwnerUserId());
        assertEquals("user-9", request.getUpdateBy());
        assertNotNull(request.getUpdateTime());
        verify(infoMapper).updateCyanExamInfo(request);
    }

    @Test
    void graderCannotImpersonateAnotherUser() {
        assertThrows(ServiceException.class, () -> grading.submitScores(submission(), 10L));
        verifyNoInteractions(infoMapper, sessionMapper, answerMapper);
    }

    @Test
    void missingOrInvalidAuthenticationFailsClosed() {
        SecurityContextHolder.clearContext();
        assertThrows(ServiceException.class, () -> management.selectCyanExamInfoList(new CyanExamInfo()));
        assertThrows(ServiceException.class, () -> grading.getExamListForGrading());
        verifyNoInteractions(infoMapper);
    }
}
