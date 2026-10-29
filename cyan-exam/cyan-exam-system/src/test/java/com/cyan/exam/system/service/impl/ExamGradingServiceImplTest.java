package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.*;
import com.cyan.exam.system.mapper.*;
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
class ExamGradingServiceImplTest extends AuthenticatedExamTest {
    @org.mockito.Spy private com.cyan.exam.system.service.ExamAccessScope accessScope;
    @InjectMocks private ExamGradingServiceImpl service;
    @Mock private CyanExamInfoMapper infoMapper;
    @Mock private ExamQuestionMapper questionMapper;
    @Mock private ExamStudentMapper studentMapper;
    @Mock private ExamSessionMapper sessionMapper;
    @Mock private ExamAnswerRecordMapper answerMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private com.cyan.exam.system.service.ExamResultService resultService;

    private ExamScoreSubmission submission(int score) {
        ExamScoreSubmission request = new ExamScoreSubmission();
        request.setExamId(1L);
        request.setUserId(2L);
        ExamScoreSubmission.Item item = new ExamScoreSubmission.Item();
        item.setRecordId(4L);
        item.setScore(score);
        request.setScores(List.of(item));
        return request;
    }

    private void submittedExam() {
        ExamSession session = new ExamSession();
        session.setStatus("SUBMITTED");
        when(sessionMapper.lockByExamAndUser(1L, 2L)).thenReturn(session);
        CyanExamInfo exam = new CyanExamInfo();
        exam.setOwnerUserId(9L);
        exam.setPublishedAt(new Date());
        when(infoMapper.selectCyanExamInfoByExamId(1L)).thenReturn(exam);
        when(studentMapper.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        CyanQuestion snapshot = new CyanQuestion();
        snapshot.setQuestionId(3L);
        snapshot.setMaxScore(10);
        when(questionMapper.selectSnapshots(1L)).thenReturn(List.of(snapshot));
    }

    private ExamAnswerRecord record(long examId) {
        ExamAnswerRecord record = new ExamAnswerRecord();
        record.setRecordId(4L);
        record.setExamId(examId);
        record.setUserId(2L);
        record.setQuestionId(3L);
        return record;
    }

    @Test
    void progressDistinguishesBlankAnswersPendingGradesAndScoredSubtotal() {
        CyanExamInfo exam = new CyanExamInfo();
        exam.setOwnerUserId(9L);
        exam.setPublishedAt(new Date());
        when(infoMapper.selectCyanExamInfoByExamId(1L)).thenReturn(exam);
        when(studentMapper.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        CyanQuestion first = new CyanQuestion();
        first.setQuestionId(3L);
        first.setMaxScore(10);
        CyanQuestion second = new CyanQuestion();
        second.setQuestionId(5L);
        second.setMaxScore(20);
        when(questionMapper.selectSnapshots(1L)).thenReturn(List.of(first, second));
        ExamAnswerRecord blank = record(1L);
        blank.setAnswer("");
        blank.setGradedAt(new Date());
        blank.setScore(0);
        ExamAnswerRecord pending = record(1L);
        pending.setQuestionId(5L);
        pending.setAnswer("manual");
        when(answerMapper.selectByExamAndUser(1L, 2L)).thenReturn(List.of(blank, pending));
        ExamSession session = new ExamSession();
        session.setStatus("SUBMITTED");
        when(sessionMapper.selectByExamAndUser(1L, 2L)).thenReturn(session);
        var progress = service.getStudentProgress(1L).get(0);
        assertEquals(1, progress.get("answered"));
        assertEquals(1, progress.get("pending"));
        assertEquals(0, progress.get("score"));
        assertEquals(30, progress.get("maxScore"));
        assertEquals(false, progress.get("gradingComplete"));
        pending.setGradedAt(new Date());
        pending.setScore(17);
        progress = service.getStudentProgress(1L).get(0);
        assertEquals(17, progress.get("score"));
        assertEquals(true, progress.get("gradingComplete"));
    }

    @Test
    void rejectsGradingBeforeSubmission() {
        CyanExamInfo exam = new CyanExamInfo();
        exam.setOwnerUserId(9L);
        when(infoMapper.selectCyanExamInfoByExamId(1L)).thenReturn(exam);
        ExamSession session = new ExamSession();
        session.setStatus("IN_PROGRESS");
        when(sessionMapper.lockByExamAndUser(1L, 2L)).thenReturn(session);
        assertThrows(ServiceException.class, () -> service.submitScores(submission(5), 9L));
        verifyNoInteractions(answerMapper);
    }

    @Test
    void rejectsOtherExamRecordAndOverLimit() {
        submittedExam();
        when(answerMapper.lockByRecordId(4L)).thenReturn(record(99L), record(1L));
        assertThrows(ServiceException.class, () -> service.submitScores(submission(5), 9L));
        assertThrows(ServiceException.class, () -> service.submitScores(submission(11), 9L));
        verify(answerMapper, never()).saveGrade(any(), anyInt());
    }

    @Test
    void zeroScoreIsSavedAndRevisionsAreIdempotent() {
        submittedExam();
        ExamAnswerRecord record = record(1L);
        when(answerMapper.lockByRecordId(4L)).thenReturn(record);
        when(answerMapper.insertScoreRevision(record, 0, 9L, "首次批改")).thenReturn(1);
        when(answerMapper.saveGrade(4L, 0)).thenReturn(1);
        service.submitScores(submission(0), 9L);
        record.setGradedAt(new Date());
        record.setScore(0);
        service.submitScores(submission(0), 9L);
        verify(answerMapper, times(1)).insertScoreRevision(record, 0, 9L, "首次批改");
        verify(answerMapper, times(1)).saveGrade(4L, 0);
    }

    @Test
    void correctionRequiresReasonAndRecordsIt() {
        submittedExam();
        ExamAnswerRecord record = record(1L);
        record.setGradedAt(new Date());
        record.setScore(3);
        when(answerMapper.lockByRecordId(4L)).thenReturn(record);
        ExamScoreSubmission request = submission(5);
        assertThrows(ServiceException.class, () -> service.submitScores(request, 9L));
        verify(answerMapper, never()).saveGrade(any(), anyInt());
        request.setReason("复核发现漏计测试点");
        when(answerMapper.insertScoreRevision(record, 5, 9L, request.getReason())).thenReturn(1);
        when(answerMapper.saveGrade(4L, 5)).thenReturn(1);
        service.submitScores(request, 9L);
        verify(answerMapper).insertScoreRevision(record, 5, 9L, request.getReason());
    }
}
