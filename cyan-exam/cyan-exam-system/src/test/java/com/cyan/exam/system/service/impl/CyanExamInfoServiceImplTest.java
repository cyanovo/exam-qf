package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.mapper.CyanExamInfoMapper;
import com.cyan.exam.system.mapper.CyanQuestionMapper;
import com.cyan.exam.system.mapper.ExamQuestionMapper;
import com.cyan.exam.system.mapper.ExamStudentMapper;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CyanExamInfoServiceImplTest extends AuthenticatedExamTest {
    @org.mockito.Spy private com.cyan.exam.system.service.ExamAccessScope accessScope;
    @InjectMocks private CyanExamInfoServiceImpl service;
    @Mock private CyanExamInfoMapper infoMapper;
    @Mock private CyanQuestionMapper questionMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private ExamStudentMapper studentMapper;
    @Mock private com.cyan.exam.system.mapper.OnsiteMapper onsiteMapper;
    @Mock private com.cyan.exam.system.service.ExamManagementService management;

    private CyanExamInfo futureExam() {
        CyanExamInfo exam = new CyanExamInfo();
        exam.setOwnerUserId(9L);
        exam.setStatus("0");
        exam.setStartTime(new Date(System.currentTimeMillis() + 600_000));
        exam.setEndTime(new Date(System.currentTimeMillis() + 1_200_000));
        return exam;
    }

    @Test
    void publishesOrderedFrozenQuestions() {
        CyanExamInfo exam = futureExam();
        CyanQuestion question = new CyanQuestion();
        question.setQuestionId(3L);
        question.setStatus("0");
        question.setReviewState("PUBLISHED");
        question.setTitle("Question");
        question.setQuestionType("FILL_BLANK");
        question.setAnswer("answer");
        when(infoMapper.lockByExamId(1L)).thenReturn(exam);
        CyanQuestion assignment = new CyanQuestion();
        assignment.setQuestionId(3L);
        assignment.setMaxScore(10);
        when(examQuestionMapper.selectAssignedQuestions(1L)).thenReturn(List.of(assignment));
        when(questionMapper.selectCyanQuestionByQuestionIds(new Long[]{3L})).thenReturn(List.of(question));
        when(infoMapper.publish(1L)).thenReturn(1);
        when(studentMapper.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        service.publish(1L);
        assertEquals(10, question.getMaxScore());
        verify(examQuestionMapper).insertSnapshots(1L, List.of(question));
        verify(infoMapper).publish(1L);
    }

    @Test
    void publishesExamThatStartedTenMinutesAgo() {
        CyanExamInfo exam = futureExam();
        exam.setStartTime(new Date(System.currentTimeMillis() - 600_000));
        CyanQuestion question = new CyanQuestion();
        question.setQuestionId(3L);
        question.setStatus("0");
        question.setReviewState("PUBLISHED");
        question.setTitle("Python basics");
        question.setQuestionType("FILL_BLANK");
        question.setAnswer("def");
        CyanQuestion assignment = new CyanQuestion();
        assignment.setQuestionId(3L);
        assignment.setMaxScore(10);
        when(infoMapper.lockByExamId(1L)).thenReturn(exam);
        when(examQuestionMapper.selectAssignedQuestions(1L)).thenReturn(List.of(assignment));
        when(questionMapper.selectCyanQuestionByQuestionIds(new Long[]{3L})).thenReturn(List.of(question));
        when(studentMapper.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        when(infoMapper.publish(1L)).thenReturn(1);
        service.publish(1L);
        verify(infoMapper).publish(1L);
    }

    @Test
    void expiredUnpublishedExamCanBeRescheduled() {
        CyanExamInfo exam = futureExam();
        exam.setLifecycle("ACTIVE");
        exam.setStartTime(new Date(System.currentTimeMillis() - 1_200_000));
        exam.setEndTime(new Date(System.currentTimeMillis() - 600_000));
        when(infoMapper.lockByExamId(1L)).thenReturn(exam);
        CyanExamInfo update = futureExam();
        update.setExamId(1L);
        update.setStartTime(new Date(System.currentTimeMillis() - 600_000));
        service.updateCyanExamInfo(update);
        verify(infoMapper).updateCyanExamInfo(update);
    }

    @Test
    void rejectsIncompleteQuestionAndDoesNotPublish() {
        when(infoMapper.lockByExamId(1L)).thenReturn(futureExam());
        when(studentMapper.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        CyanQuestion assignment = new CyanQuestion();
        assignment.setQuestionId(3L);
        assignment.setMaxScore(10);
        when(examQuestionMapper.selectAssignedQuestions(1L)).thenReturn(List.of(assignment));
        CyanQuestion question = new CyanQuestion();
        question.setQuestionId(3L);
        question.setStatus("0");
        question.setReviewState("PUBLISHED");
        when(questionMapper.selectCyanQuestionByQuestionIds(new Long[]{3L})).thenReturn(List.of(question));
        assertThrows(ServiceException.class, () -> service.publish(1L));
        verify(infoMapper, never()).publish(any());
        verify(examQuestionMapper, never()).insertSnapshots(any(), any());
    }

    @Test
    void rejectsCompleteButUnreviewedQuestionBeforeSnapshot() {
        when(infoMapper.lockByExamId(1L)).thenReturn(futureExam());
        when(studentMapper.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        CyanQuestion assignment = new CyanQuestion();
        assignment.setQuestionId(3L);
        assignment.setMaxScore(10);
        when(examQuestionMapper.selectAssignedQuestions(1L)).thenReturn(List.of(assignment));
        CyanQuestion question = new CyanQuestion();
        question.setQuestionId(3L);
        question.setStatus("0");
        question.setReviewState("DRAFT");
        question.setTitle("Complete but not approved");
        question.setQuestionType("FILL_BLANK");
        question.setAnswer("print");
        when(questionMapper.selectCyanQuestionByQuestionIds(new Long[]{3L})).thenReturn(List.of(question));
        assertTrue(assertThrows(ServiceException.class, () -> service.publish(1L))
                .getMessage().contains("审核"));
        verify(infoMapper, never()).publish(any());
        verify(examQuestionMapper, never()).insertSnapshots(any(), any());
    }

    @Test
    void rejectsPythonQuestionWithConstantHiddenOutputBeforeSnapshot() {
        when(infoMapper.lockByExamId(1L)).thenReturn(futureExam());
        when(studentMapper.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        CyanQuestion assignment = new CyanQuestion();
        assignment.setQuestionId(3L);
        assignment.setMaxScore(10);
        when(examQuestionMapper.selectAssignedQuestions(1L)).thenReturn(List.of(assignment));
        CyanQuestion question = new CyanQuestion();
        question.setQuestionId(3L);
        question.setStatus("0");
        question.setReviewState("PUBLISHED");
        question.setTitle("Python task");
        question.setQuestionType("PYTHON");
        question.setAnswer("{\"cases\":["
                + "{\"input\":\"1\",\"expected\":\"1\",\"public\":true,\"weight\":0,\"comparison\":\"EXACT\"},"
                + "{\"input\":\"2\",\"expected\":\"1\",\"public\":false,\"weight\":3,\"comparison\":\"EXACT\"},"
                + "{\"input\":\"3\",\"expected\":\"1\",\"public\":false,\"weight\":3,\"comparison\":\"EXACT\"},"
                + "{\"input\":\"4\",\"expected\":\"1\",\"public\":false,\"weight\":4,\"comparison\":\"EXACT\"}]}");
        when(questionMapper.selectCyanQuestionByQuestionIds(new Long[]{3L})).thenReturn(List.of(question));
        assertTrue(assertThrows(ServiceException.class, () -> service.publish(1L))
                .getMessage().contains("3种不同"));
        verify(examQuestionMapper, never()).insertSnapshots(any(), any());
        verify(infoMapper, never()).publish(any());
    }

    @Test
    void expiredDraftCanBeDeletedWithItsRelations() {
        CyanExamInfo exam = futureExam();
        exam.setStartTime(new Date(System.currentTimeMillis() - 1_200_000));
        exam.setEndTime(new Date(System.currentTimeMillis() - 600_000));
        when(infoMapper.lockByExamId(1L)).thenReturn(exam);
        when(infoMapper.deleteCyanExamInfoByExamIds(new Long[]{1L})).thenReturn(1);
        assertEquals(1, service.deleteCyanExamInfoByExamIds(new Long[]{1L}));
        verify(management).validateDeletion(1L);
        verify(management).removeDraftRelations(1L);
        verify(studentMapper).deleteExamStudentByExamId(1L);
        verify(examQuestionMapper).deleteExamQuestionByExamId(1L);
    }

    @Test
    void deletionValidatesWholeBatchBeforeRemovingAnything() {
        when(infoMapper.lockByExamId(1L)).thenReturn(futureExam());
        CyanExamInfo published = futureExam();
        published.setPublishedAt(new Date());
        when(infoMapper.lockByExamId(2L)).thenReturn(published);
        assertThrows(ServiceException.class, () -> service.deleteCyanExamInfoByExamIds(new Long[]{1L,2L}));
        verify(studentMapper, never()).deleteExamStudentByExamId(any());
        verify(management, never()).removeDraftRelations(any());
        verify(infoMapper, never()).deleteCyanExamInfoByExamIds(any());
    }

    @Test
    void protectedRecordsPreventDraftDeletion() {
        when(infoMapper.lockByExamId(1L)).thenReturn(futureExam());
        doThrow(new ServiceException("Protected records")).when(management).validateDeletion(1L);
        assertThrows(ServiceException.class, () -> service.deleteCyanExamInfoByExamIds(new Long[]{1L}));
        verify(management, never()).removeDraftRelations(any());
        verify(infoMapper, never()).deleteCyanExamInfoByExamIds(any());
    }
    @Test
    void endedBlankCanBeDeletedWithoutGrading() {
        CyanExamInfo exam = futureExam();
        exam.setPublishedAt(new Date());
        exam.setLifecycle("TERMINATED");
        when(infoMapper.lockByExamId(1L)).thenReturn(exam);
        service.deleteCyanExamInfoByExamIds(new Long[]{1L});
        verify(management).validateBlankDeletion(1L);
        verify(management).removeBlankRelations(1L);
        verify(infoMapper).deleteCyanExamInfoByExamIds(new Long[]{1L});
    }
    @Test
    void actualAnswersBlockWholeBatchEvenWhenEnded() {
        when(infoMapper.lockByExamId(1L)).thenReturn(futureExam());
        CyanExamInfo exam = futureExam();
        exam.setPublishedAt(new Date());
        exam.setEndTime(new Date(System.currentTimeMillis()-1000));
        when(infoMapper.lockByExamId(2L)).thenReturn(exam);
        doThrow(new ServiceException("Actual answers")).when(management).validateBlankDeletion(2L);
        assertThrows(ServiceException.class,()->service.deleteCyanExamInfoByExamIds(new Long[]{1L,2L}));
        verify(management,never()).removeBlankRelations(any());
        verify(management,never()).removeDraftRelations(any());
        verify(infoMapper,never()).deleteCyanExamInfoByExamIds(any());
    }

    @Test
    void publishedExamCannotBeChanged() {
        CyanExamInfo exam = futureExam();
        exam.setPublishedAt(new Date());
        when(infoMapper.lockByExamId(1L)).thenReturn(exam);
        CyanExamInfo update = new CyanExamInfo();
        update.setExamId(1L);
        assertThrows(ServiceException.class, () -> service.updateCyanExamInfo(update));
        assertThrows(ServiceException.class, () -> service.saveExamStudents(1L, List.of(2L)));
        assertThrows(ServiceException.class, () -> service.saveExamQuestions(1L, List.of(3L)));
        assertThrows(ServiceException.class, () -> service.deleteCyanExamInfoByExamIds(new Long[]{1L}));
        assertThrows(ServiceException.class, () -> service.publish(1L));
        verify(infoMapper, never()).updateCyanExamInfo(any());
    }
}
