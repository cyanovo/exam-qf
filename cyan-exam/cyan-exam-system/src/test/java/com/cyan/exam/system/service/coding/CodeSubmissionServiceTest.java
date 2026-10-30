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
    @Mock ExamAnswerRecordMapper answers;
    @InjectMocks CodeSubmissionService service;

    CodeSubmissionService.Request request() {
        return new CodeSubmissionService.Request(3L, "FORMAL", "print(1)", "test-idempotency-key", "snapshot", CodingRules.hash("print(1)"));
    }
    Map<String, Object> existing() {
        return new HashMap<>(Map.of("id", 5L, "questionId", 3L, "mode", "FORMAL", "sourceHash", CodingRules.hash("print(1)"),
                "snapshotHash", "snapshot", "state", "SUCCEEDED", "feedback", "[{\"input\":\"SECRET\"}]", "score", 10));
    }
    @Test void duplicateReturnsOwnScoreEvenAfterDeadlineButNeverLeaksHiddenDiagnostics() {
        when(scope.actorId()).thenReturn(2L);
        when(students.selectExamIdsByUserId(2L)).thenReturn(List.of(1L));
        when(submissions.byKey(1L, 2L, "test-idempotency-key")).thenReturn(existing());
        String response = JSON.toJSONString(service.submit(1L, request()));
        assertTrue(response.contains("\"id\":5"));
        assertFalse(response.contains("SECRET")); assertTrue(response.contains("\"score\":10"));
        verify(submissions, never()).insert(any());
        verifyNoInteractions(runner, exams);
    }
    @Test void sampleAndPendingFormalNeverExposeScoresOrHiddenDiagnostics() {
        when(scope.actorId()).thenReturn(2L);
        when(students.selectExamIdsByUserId(2L)).thenReturn(List.of(1L));
        Map<String, Object> sample = existing();
        sample.put("mode", "SAMPLE");
        sample.put("feedback", "[{\"case\":1,\"passed\":true,\"stdout\":\"ok\"}]");
        Map<String, Object> pending = existing();
        pending.put("state", "QUEUED");
        pending.put("score", null);
        when(submissions.history(1L, 2L, 3L)).thenReturn(List.of(sample, pending));
        List<Map<String, Object>> history = service.history(1L, 3L);
        assertFalse(history.get(0).containsKey("score"));
        assertTrue(history.get(0).containsKey("feedback"));
        assertFalse(history.get(1).containsKey("score"));
        assertFalse(history.get(1).containsKey("feedback"));
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
    Map<String, Object> regradeRow() {
        var row = existing();
        row.put("examId", 1L); row.put("userId", 2L); row.put("runVersion", 1);
        when(submissions.get(5L)).thenReturn(row);
        return row;
    }
    @Test void regradeArchivesBeforeResetAndClearsOnlyLatestFormalGrade() {
        var row = regradeRow();
        when(submissions.lock(5L)).thenReturn(row);
        when(scope.actorId()).thenReturn(7L);
        when(submissions.archive(5L, 1, 7L, "verify")).thenReturn(1);
        when(submissions.regrade(5L, 1)).thenReturn(1);
        when(submissions.latestFormal(1L, 2L, 3L)).thenReturn(row);
        service.regrade(5L, 1, "verify");
        var order = inOrder(results, sessions, submissions, answers);
        order.verify(results).lockForGrading(1L);
        order.verify(sessions).lockByExamAndUser(1L, 2L);
        order.verify(submissions).lock(5L);
        order.verify(submissions).archive(5L, 1, 7L, "verify");
        order.verify(submissions).regrade(5L, 1);
        order.verify(submissions).latestFormal(1L, 2L, 3L);
        order.verify(answers).clearCodeGrade(1L, 2L, 3L);
    }
    @Test void oldSubmissionRegradeNeverChangesFinalGrade() {
        var row = regradeRow();
        when(submissions.lock(5L)).thenReturn(row);
        when(scope.actorId()).thenReturn(7L);
        when(submissions.archive(5L, 1, 7L, "verify")).thenReturn(1);
        when(submissions.regrade(5L, 1)).thenReturn(1);
        when(submissions.latestFormal(1L, 2L, 3L)).thenReturn(Map.of("id", 6L));
        service.regrade(5L, 1, "verify");
        verifyNoInteractions(answers);
    }
    @Test void rejectsMissingReasonStaleVersionAndPendingTask() {
        assertThrows(ServiceException.class, () -> service.regrade(5L, 1, " "));
        var row = regradeRow();
        when(submissions.lock(5L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> service.regrade(5L, 2, "verify"));
        row.put("state", "RUNNING");
        assertThrows(ServiceException.class, () -> service.regrade(5L, 1, "verify"));
        verify(submissions, never()).archive(any(), anyInt(), any(), anyString());
        verifyNoInteractions(answers, runner);
    }
    @Test void lockedResultsRejectRegradingEvenCompletedSubmission() {
        regradeRow();
        when(results.lockForGrading(1L)).thenThrow(new ServiceException("locked"));
        assertThrows(ServiceException.class, () -> service.regrade(5L, 1, "verify"));
        verify(submissions, never()).lock(any());
    }
}
