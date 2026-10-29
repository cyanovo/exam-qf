package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.*;
import com.cyan.exam.system.mapper.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamAutoGradingServiceTest {
    @Mock ExamSessionMapper sessions;
    @Mock ExamQuestionMapper questions;
    @Mock ExamAnswerRecordMapper answers;
    @Mock com.cyan.exam.system.service.ExamResultService resultService;
    @Mock CodeSubmissionMapper codeSubmissions;
    @InjectMocks ExamAutoGradingService service;

    private CyanQuestion question(String type, String expected) {
        CyanQuestion q = new CyanQuestion();
        q.setQuestionId(3L);
        q.setQuestionType(type);
        q.setAnswer(expected);
        q.setMaxScore(10);
        q.setSubTitle("[{\"label\":\"A\"},{\"label\":\"B\"},{\"label\":\"C\"}]");
        return q;
    }

    @Test
    void singleChoiceAndJudgeMatchLabels() {
        for (String type : List.of("SINGLE_CHOICE", "JUDGE")) {
            CyanQuestion q = question(type, "A");
            assertEquals(10, ExamAutoGradingService.calculateScore(q, "A"));
            assertEquals(0, ExamAutoGradingService.calculateScore(q, "B"));
            assertEquals(0, ExamAutoGradingService.calculateScore(q, "A,B"));
            assertEquals(0, ExamAutoGradingService.calculateScore(q, ""));
        }
    }

    @Test
    void judgeSupportsHistoricTextAndSnapshotLabelsWithoutGuessingAmbiguousText() {
        CyanQuestion q = question("JUDGE", "A");
        q.setSubTitle("[{\"label\":\"A\",\"content\":\"对\"},{\"label\":\"B\",\"content\":\"错\"}]");
        assertEquals(10, ExamAutoGradingService.calculateScore(q, "对"));
        assertEquals(0, ExamAutoGradingService.calculateScore(q, "错"));
        q.setAnswer("对");
        assertEquals(10, ExamAutoGradingService.calculateScore(q, "A"));
        assertEquals(10, ExamAutoGradingService.calculateScore(q, "对"));
        q.setSubTitle("[{\"label\":\"A\",\"content\":\"对\"},{\"label\":\"B\",\"content\":\"对\"}]");
        assertThrows(ServiceException.class, () -> ExamAutoGradingService.calculateScore(q, "对"));
    }

    @Test
    void multiChoiceIgnoresOrderButRejectsPartialExtraDuplicateAndMalformedLabels() {
        CyanQuestion q = question("MULTI_CHOICE", "A,B");
        assertEquals(10, ExamAutoGradingService.calculateScore(q, " B, A "));
        for (String answer : List.of("A", "A,B,C", "A,A,B", "A,B,", "A,Z", "a,b")) {
            assertEquals(0, ExamAutoGradingService.calculateScore(q, answer), answer);
        }
    }

    @Test
    void subjectiveAnswersRemainManualButBlankAnswersAreZero() {
        for (String type : List.of("FILL_BLANK", "SHORT_ANSWER")) {
            CyanQuestion q = question(type, "reference");
            assertNull(ExamAutoGradingService.calculateScore(q, "reference"));
            assertEquals(0, ExamAutoGradingService.calculateScore(q, " \n\t"));
            assertEquals(0, ExamAutoGradingService.calculateScore(q, null));
        }
    }

    @Test
    void invalidReferenceIsAnErrorNotAZeroGrade() {
        for (String reference : List.of("", "Z", "A,A", "A,B")) {
            CyanQuestion q = question("SINGLE_CHOICE", reference);
            assertThrows(ServiceException.class, () -> ExamAutoGradingService.calculateScore(q, "A"));
        }
        CyanQuestion q = question("SINGLE_CHOICE", "A");
        q.setSubTitle("invalid");
        assertThrows(ServiceException.class, () -> ExamAutoGradingService.calculateScore(q, "A"));
    }

    @Test
    void noScoringBeforeSubmissionOrWithoutSession() {
        service.gradeSubmitted(1L, 2L);
        ExamSession session = new ExamSession();
        session.setStatus("IN_PROGRESS");
        when(sessions.lockByExamAndUser(1L, 2L)).thenReturn(session);
        service.gradeSubmitted(1L, 2L);
        verifyNoInteractions(questions, answers);
    }

    private ExamAnswerRecord submitted(String status) {
        ExamSession session = new ExamSession();
        session.setStatus(status);
        when(sessions.lockByExamAndUser(1L, 2L)).thenReturn(session);
        when(questions.selectSnapshots(1L)).thenReturn(List.of(question("MULTI_CHOICE", "A,B")));
        ExamAnswerRecord record = new ExamAnswerRecord();
        record.setRecordId(4L);
        record.setExamId(1L);
        record.setUserId(2L);
        record.setQuestionId(3L);
        record.setAnswer("B,A");
        when(answers.selectByExamAndUser(1L, 2L)).thenReturn(List.of(record));
        return record;
    }

    @Test
    void timeoutScoringIsAuditedAndRetriesPreserveGrade() {
        ExamAnswerRecord record = submitted("AUTO_SUBMITTED");
        when(answers.insertScoreRevision(record, 10, 0L, "系统自动判分 v1")).thenReturn(1);
        when(answers.saveGrade(4L, 10)).thenAnswer(call -> {
            record.setScore(10);
            record.setGradedAt(new Date());
            return 1;
        });
        service.gradeSubmitted(1L, 2L);
        service.gradeSubmitted(1L, 2L);
        verify(answers, times(1)).insertScoreRevision(record, 10, 0L, "系统自动判分 v1");
        verify(answers, times(1)).saveGrade(4L, 10);
        var order = inOrder(sessions, answers);
        order.verify(sessions).lockByExamAndUser(1L, 2L);
        order.verify(answers).insertMissingSubmittedAnswers(1L, 2L);
    }

    @Test
    void teacherZeroScoreIsNeverOverwritten() {
        ExamAnswerRecord record = submitted("SUBMITTED");
        record.setScore(0);
        record.setGradedAt(new Date());
        service.gradeSubmitted(1L, 2L);
        verify(answers, never()).saveGrade(any(), anyInt());
        verify(answers, never()).insertScoreRevision(any(), anyInt(), any(), any());
    }

    @Test
    void failedAuditPreventsScoreWrite() {
        submitted("SUBMITTED");
        assertThrows(ServiceException.class, () -> service.gradeSubmitted(1L, 2L));
        verify(answers, never()).saveGrade(any(), anyInt());
    }

    @Test
    void workerSealsExpiredSessionsAndContinuesPastFailedSession() {
        ExamSession first = new ExamSession();
        first.setSessionId(1L);
        first.setExamId(1L);
        first.setUserId(2L);
        ExamSession second = new ExamSession();
        second.setSessionId(2L);
        second.setExamId(1L);
        second.setUserId(3L);
        when(sessions.selectPendingAutoGrades(0L)).thenReturn(List.of(first, second));
        ExamAutoGradingService scorer = mock(ExamAutoGradingService.class);
        doThrow(new ServiceException("test failure")).when(scorer).gradeSubmitted(1L, 2L);
        new ExamSessionExpiryJob(sessions, scorer).sealExpiredSessions();
        var order = inOrder(sessions, scorer);
        order.verify(sessions).autoFinishExpired();
        order.verify(scorer).gradeSubmitted(1L, 2L);
        order.verify(scorer).gradeSubmitted(1L, 3L);
    }
}
