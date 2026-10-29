package com.cyan.exam.system.service.coding;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.*;
import com.cyan.exam.system.mapper.*;
import com.cyan.exam.system.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSubmissionServiceTest {
    @Mock CodeSubmissionMapper submissions;
    @Mock CyanExamInfoMapper exams;
    @Mock ExamStudentMapper students;
    @Mock ExamSessionMapper sessions;
    @Mock ExamQuestionMapper questions;
    @Mock Judge0Client runner;
    @Mock ExamAccessScope scope;
    @Mock ExamResultService results;
    @InjectMocks CodeSubmissionService service;

    CodeSubmissionService.Request request() {
        return new CodeSubmissionService.Request(3L, "FORMAL", "print(1)", "test-idempotency-key", "snapshot", CodingRules.hash("print(1)"));
    }
    Map<String, Object> existing() {
        return new HashMap<>(Map.of("id", 5L, "questionId", 3L, "mode", "FORMAL", "sourceHash", CodingRules.hash("print(1)"),
                "snapshotHash", "snapshot", "state", "SUCCEEDED", "feedback", "[{\"input\":\"SECRET\"}]", "score", 10));
    }
    @Test void duplicateReturnsSameSubmissionEvenAfterDeadlineAndNeverLeaksHiddenDiagnostics() {
        when(scope.actorId()).thenReturn(2L);
        when(students.selectExamIdsByUserId(2L)).thenReturn(List.of(1L));
        when(submissions.byKey(1L, 2L, "test-idempotency-key")).thenReturn(existing());
        String response = JSON.toJSONString(service.submit(1L, request()));
        assertTrue(response.contains("\"id\":5"));
        assertFalse(response.contains("SECRET")); assertFalse(response.contains("\"score\""));
        verify(submissions, never()).insert(any());
        verifyNoInteractions(runner, exams);
    }
    @Test void reusedKeyWithDifferentSnapshotIsRejected() {
        when(scope.actorId()).thenReturn(2L);
        when(students.selectExamIdsByUserId(2L)).thenReturn(List.of(1L));
        var existing = existing(); existing.put("snapshotHash", "other");
        when(submissions.byKey(1L, 2L, "test-idempotency-key")).thenReturn(existing);
        assertThrows(ServiceException.class, () -> service.submit(1L, request()));
    }
    @Test void closedSessionRejectsNewSubmission() {
        when(scope.actorId()).thenReturn(2L);
        when(students.selectExamIdsByUserId(2L)).thenReturn(List.of(1L));
        ExamSession session = new ExamSession(); session.setStatus("SUBMITTED");
        when(sessions.lockByExamAndUser(1L, 2L)).thenReturn(session);
        assertThrows(ServiceException.class, () -> service.submit(1L, request()));
        verify(submissions, never()).insert(any());
    }
    @Test void studentCannotReadAnotherStudentsSourceOrSubmission() {
        var row = existing(); row.put("userId", 3L);
        when(submissions.get(5L)).thenReturn(row);
        when(scope.actorId()).thenReturn(2L);
        assertThrows(ServiceException.class, () -> service.get(5L));
    }
    @Test void infrastructureRetryIsBlockedByResultsLock() {
        var row = existing(); row.put("examId", 1L);
        when(submissions.get(5L)).thenReturn(row);
        when(results.lockForGrading(1L)).thenThrow(new ServiceException("locked"));
        assertThrows(ServiceException.class, () -> service.retry(5L));
        verify(submissions, never()).retry(any());
    }
}
