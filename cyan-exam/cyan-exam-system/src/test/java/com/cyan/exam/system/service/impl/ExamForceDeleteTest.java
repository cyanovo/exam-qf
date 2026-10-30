package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.mapper.*;
import com.cyan.exam.system.service.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamForceDeleteTest extends AuthenticatedExamTest {
    @Spy private ExamAccessScope scope;
    @InjectMocks private ExamManagementService service;
    @Mock private CyanExamInfoMapper exams;
    @Mock private ExamManagementMapper mapper;
    @Mock private ExamQuestionMapper questions;
    @Mock private OnsiteMapper onsite;
    @Mock private ExamResultMapper results;
    @Mock private ExamDossierMapper dossier;

    @AfterEach void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void nonAdminCannotForceDeleteEvenAsOwner() {
        assertThrows(ServiceException.class, () -> service.forceDelete(7L, "Test", "mistaken exam"));
        verifyNoInteractions(exams, mapper);
    }

    @Test void confirmationMustMatchLockedExam() {
        login(1L);
        CyanExamInfo exam = new CyanExamInfo();
        exam.setExamName("Test");
        when(exams.lockByExamId(7L)).thenReturn(exam);
        assertThrows(ServiceException.class, () -> service.forceDelete(7L, "Other", "mistaken exam"));
        assertThrows(ServiceException.class, () -> service.forceDelete(7L, "Test", "bad"));
        verifyNoInteractions(mapper);
    }

    @Test void deletesRelationsAndPreservesDeletionAudit() {
        login(1L);
        CyanExamInfo exam = new CyanExamInfo();
        exam.setExamName("Test");
        when(exams.lockByExamId(7L)).thenReturn(exam);
        when(mapper.fileKeys(7L)).thenReturn(List.of());
        when(exams.deleteCyanExamInfoByExamIds(new Long[]{7L})).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        service.forceDelete(7L, "Test", "test cleanup");

        verify(mapper).deleteCodeHistory(7L);
        verify(mapper).deleteResultEvents(7L);
        verify(mapper).deleteIncidentEvents(7L);
        verify(mapper).deleteOnsiteScores(7L);
        verify(mapper).deleteOnsiteSubmissions(7L);
        verify(mapper).deleteDraftTasks(7L);
        verify(mapper).deleteBlankAnswers(7L);
        verify(mapper).deleteBlankSessions(7L);
        verify(mapper).deleteFiles(7L);
        verify(mapper).deleteExamStudents(7L);
        verify(questions).deleteExamQuestionByExamId(7L);
        verify(mapper).deleteManagementHistory(7L);
        verify(mapper).event(eq(7L), eq(1L), eq("FORCE_DELETE"), eq("test cleanup"), anyString());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }
}
