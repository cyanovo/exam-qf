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
        service.publish(1L);
        assertEquals(10, question.getMaxScore());
        verify(examQuestionMapper).insertSnapshots(1L, List.of(question));
        verify(infoMapper).publish(1L);
    }

    @Test
    void rejectsIncompleteQuestionAndDoesNotPublish() {
        when(infoMapper.lockByExamId(1L)).thenReturn(futureExam());
        CyanQuestion assignment = new CyanQuestion();
        assignment.setQuestionId(3L);
        assignment.setMaxScore(10);
        when(examQuestionMapper.selectAssignedQuestions(1L)).thenReturn(List.of(assignment));
        CyanQuestion question = new CyanQuestion();
        question.setQuestionId(3L);
        question.setStatus("0");
        when(questionMapper.selectCyanQuestionByQuestionIds(new Long[]{3L})).thenReturn(List.of(question));
        assertThrows(ServiceException.class, () -> service.publish(1L));
        verify(infoMapper, never()).publish(any());
        verify(examQuestionMapper, never()).insertSnapshots(any(), any());
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
