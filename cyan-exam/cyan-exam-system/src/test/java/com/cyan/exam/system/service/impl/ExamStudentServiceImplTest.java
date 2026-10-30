package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.domain.ExamAnswerRecord;
import com.cyan.exam.system.domain.ExamSession;
import com.cyan.exam.system.mapper.CyanExamInfoMapper;
import com.cyan.exam.system.mapper.ExamAnswerRecordMapper;
import com.cyan.exam.system.mapper.ExamQuestionMapper;
import com.cyan.exam.system.mapper.ExamStudentMapper;
import com.cyan.exam.system.mapper.ExamSessionMapper;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamStudentServiceImplTest {
    @InjectMocks private ExamStudentServiceImpl service;
    @Mock private CyanExamInfoMapper examInfoMapper;
    @Mock private ExamStudentMapper examStudentMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private ExamAnswerRecordMapper answerRecordMapper;
    @Mock private ExamSessionMapper sessionMapper;

    private static final long EXAM_ID = 1L;
    private static final long USER_ID = 2L;
    private static final long QUESTION_ID = 3L;

    @Test
    void pythonIncludesCompleteStatementAndScoreButOnlyPublicSamples() {
        CyanQuestion question = new CyanQuestion();
        question.setQuestionId(QUESTION_ID);
        question.setQuestionType("PYTHON");
        question.setTitle("Sum");
        question.setSubTitle("Read two integers.\nInput: a b\nOutput: their sum.");
        question.setMaxScore(10);
        question.setAnswer("{\"cases\":["
                + "{\"input\":\"3 5\",\"expected\":\"8\",\"public\":true,\"weight\":0,\"comparison\":\"EXACT\"},"
                + "{\"input\":\"SECRET\",\"expected\":\"HIDDEN\",\"public\":false,\"weight\":10,\"comparison\":\"EXACT\"}]}");
        when(examQuestionMapper.selectSnapshots(EXAM_ID)).thenReturn(List.of(question));
        when(answerRecordMapper.selectByExamAndUser(EXAM_ID, USER_ID)).thenReturn(List.of());
        var item = service.getQuestionsWithAnswers(EXAM_ID, USER_ID).get(0);
        assertEquals(question.getSubTitle(), item.get("description"));
        assertEquals(10, item.get("maxScore"));
        assertEquals(List.of(Map.of("input", "3 5", "expected", "8")), item.get("samples"));
        assertFalse(item.containsKey("answer"));
        assertFalse(item.toString().contains("SECRET"));
        assertFalse(item.toString().contains("HIDDEN"));
    }

    @Test
    void optionsExposeOnlyPublicFields() {
        CyanQuestion question = new CyanQuestion();
        question.setQuestionId(QUESTION_ID);
        question.setSubTitle("[{\"label\":\"A\",\"content\":\"choice\",\"isAnswer\":1,\"private\":\"secret\"}]");
        when(examQuestionMapper.selectSnapshots(EXAM_ID)).thenReturn(List.of(question));
        when(answerRecordMapper.selectByExamAndUser(EXAM_ID, USER_ID)).thenReturn(List.of());

        List<Map<String, Object>> result = service.getQuestionsWithAnswers(EXAM_ID, USER_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) result.get(0).get("options");
        assertEquals(Map.of("label", "A", "content", "choice"), options.get(0));
        assertFalse(result.get(0).containsKey("answer"));
        verify(examQuestionMapper).selectSnapshots(EXAM_ID);
    }

    @Test
    void rejectsUnassignedStudent() {
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "A"));
        verifyNoInteractions(answerRecordMapper);
    }

    @Test
    void rejectsBeforeStartAndAfterEnd() {
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        activeSession();
        CyanExamInfo exam = new CyanExamInfo();
        exam.setStatus("0");
        exam.setStartTime(new Date(System.currentTimeMillis() + 60_000));
        exam.setEndTime(new Date(System.currentTimeMillis() + 120_000));
        when(examInfoMapper.selectCyanExamInfoByExamId(EXAM_ID)).thenReturn(exam);
        assertThrows(ServiceException.class, () -> service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "A"));

        exam.setStartTime(new Date(System.currentTimeMillis() - 120_000));
        exam.setEndTime(new Date(System.currentTimeMillis() - 60_000));
        assertThrows(ServiceException.class, () -> service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "A"));
        verifyNoInteractions(answerRecordMapper);
    }

    @Test
    void rejectsQuestionOutsideExam() {
        activeExam();
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        activeSession();
        when(examQuestionMapper.containsSnapshot(EXAM_ID, QUESTION_ID)).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "A"));
        verifyNoInteractions(answerRecordMapper);
    }

    @Test
    void acceptsValidAnswerAndRejectsGuardedWrite() {
        activeExam();
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        activeSession();
        when(examQuestionMapper.containsSnapshot(EXAM_ID, QUESTION_ID)).thenReturn(1);
        when(answerRecordMapper.insertOrUpdate(any(ExamAnswerRecord.class))).thenReturn(1, 0);
        service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "A");
        assertThrows(ServiceException.class, () -> service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "B"));
        verify(answerRecordMapper, times(2)).insertOrUpdate(any(ExamAnswerRecord.class));
    }

    @Test
    void acceptsUnchangedAnswerWhenDatabaseReportsNoChange() {
        activeExam();
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        activeSession();
        when(examQuestionMapper.containsSnapshot(EXAM_ID, QUESTION_ID)).thenReturn(1);
        ExamAnswerRecord saved = new ExamAnswerRecord();
        saved.setAnswer("A");
        when(answerRecordMapper.selectByExamUserQuestion(any(ExamAnswerRecord.class))).thenReturn(saved);

        assertDoesNotThrow(() -> service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "A"));
    }

    private void activeExam() {
        CyanExamInfo exam = new CyanExamInfo();
        exam.setStatus("0");
        exam.setPublishedAt(new Date());
        exam.setStartTime(new Date(System.currentTimeMillis() - 60_000));
        exam.setEndTime(new Date(System.currentTimeMillis() + 60_000));
        when(examInfoMapper.selectCyanExamInfoByExamId(EXAM_ID)).thenReturn(exam);
    }

    @Test
    void repeatedFinishDoesNotWriteAgain() {
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        ExamSession session = activeSession();
        activeExam();
        assertEquals("SUBMITTED", service.finishSession(EXAM_ID, USER_ID).getStatus());
        assertEquals("SUBMITTED", service.finishSession(EXAM_ID, USER_ID).getStatus());
        verify(sessionMapper, times(1)).finish(session.getSessionId(), "SUBMITTED");
    }

    @Test
    void rejectsAnswerAfterSubmission() {
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        ExamSession session = activeSession();
        session.setStatus("SUBMITTED");
        assertThrows(ServiceException.class, () -> service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "A"));
        verifyNoInteractions(answerRecordMapper);
    }

    @Test
    void startsOnlyAssignedSessionsAndSealsExpiredOnReturn() {
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        CyanExamInfo exam = new CyanExamInfo();
        exam.setStatus("0");
        exam.setPublishedAt(new Date());
        exam.setStartTime(new Date(System.currentTimeMillis() - 120_000));
        exam.setEndTime(new Date(System.currentTimeMillis() - 60_000));
        when(examInfoMapper.selectCyanExamInfoByExamId(EXAM_ID)).thenReturn(exam);
        ExamSession session = activeSession();
        assertEquals("AUTO_SUBMITTED", service.startSession(EXAM_ID, USER_ID).getStatus());
        verify(sessionMapper, never()).createIfAbsent(any(), any());
        verify(sessionMapper).finish(session.getSessionId(), "AUTO_SUBMITTED");
    }

    @Test
    void startsAndResumesSameActiveSession() {
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        activeExam();
        ExamSession session = activeSession();
        assertSame(session, service.startSession(EXAM_ID, USER_ID));
        verify(sessionMapper).createIfAbsent(EXAM_ID, USER_ID);
        verify(sessionMapper, never()).finish(any(), any());
    }

    @Test
    void draftCannotStartOrSubmit() {
        when(examStudentMapper.selectExamIdsByUserId(USER_ID)).thenReturn(List.of(EXAM_ID));
        CyanExamInfo draft = new CyanExamInfo();
        draft.setStatus("0");
        draft.setStartTime(new Date(System.currentTimeMillis() - 60_000));
        draft.setEndTime(new Date(System.currentTimeMillis() + 60_000));
        when(examInfoMapper.selectCyanExamInfoByExamId(EXAM_ID)).thenReturn(draft);
        assertThrows(ServiceException.class, () -> service.startSession(EXAM_ID, USER_ID));
        activeSession();
        assertThrows(ServiceException.class, () -> service.submitAnswer(EXAM_ID, USER_ID, QUESTION_ID, "A"));
        verifyNoInteractions(answerRecordMapper);
    }

    private ExamSession activeSession() {
        ExamSession session = new ExamSession();
        session.setSessionId(9L);
        session.setStatus("IN_PROGRESS");
        when(sessionMapper.lockByExamAndUser(EXAM_ID, USER_ID)).thenReturn(session);
        return session;
    }
}
