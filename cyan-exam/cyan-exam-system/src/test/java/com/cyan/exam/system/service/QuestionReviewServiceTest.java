package com.cyan.exam.system.service;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.mapper.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestionReviewServiceTest {
    @Test void workflowHasNoDraftPublishShortcut() {
        assertEquals("REVIEW",QuestionReviewService.nextState("DRAFT","SUBMIT"));
        assertEquals("PUBLISHED",QuestionReviewService.nextState("REVIEW","APPROVE"));
        assertEquals("DRAFT",QuestionReviewService.nextState("REVIEW","REJECT"));
        assertEquals("DRAFT",QuestionReviewService.nextState("REVIEW","WITHDRAW"));
        assertEquals("DISABLED",QuestionReviewService.nextState("PUBLISHED","DISABLE"));
        assertEquals("DRAFT",QuestionReviewService.nextState("DISABLED","REOPEN"));
        assertThrows(ServiceException.class,()->QuestionReviewService.nextState("DRAFT","APPROVE"));
        assertThrows(ServiceException.class,()->QuestionReviewService.nextState("DISABLED","APPROVE"));
        assertThrows(ServiceException.class,()->QuestionReviewService.nextState("REVIEW",null));
    }
    @Test void rejectsStaleEditAndPendingReview() {
        var questions=mock(CyanQuestionMapper.class);
        var service=new QuestionReviewService(questions,mock(QuestionReviewMapper.class));
        var q=new CyanQuestion(); q.setRevision(2); q.setReviewState("REVIEW");
        when(questions.lockQuestion(1L)).thenReturn(q);
        assertThrows(ServiceException.class,()->service.lockForEdit(1L,1));
        assertThrows(ServiceException.class,()->service.lockForEdit(1L,2));
    }
    @Test void approvalRetainsVersionAndReason() {
        var questions=mock(CyanQuestionMapper.class); var events=mock(QuestionReviewMapper.class);
        var service=new QuestionReviewService(questions,events);
        var q=new CyanQuestion(); q.setQuestionId(1L); q.setRevision(2); q.setReviewState("REVIEW");
        q.setTitle("T"); q.setAnswer("A");
        when(questions.lockQuestion(1L)).thenReturn(q);
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("reviewer");
            service.decide(1L,new QuestionReviewService.Decision("APPROVE",2,"checked"));
        }
        verify(events).transition(1L,"PUBLISHED","0","reviewer");
        verify(events).event(eq(1L),eq(3),eq("APPROVE"),eq("REVIEW"),eq("PUBLISHED"),eq("reviewer"),eq("checked"),contains("\"answer\":\"A\""));
    }
    @Test void rejectionNeedsReasonAndDoesNotWrite() {
        var questions=mock(CyanQuestionMapper.class); var events=mock(QuestionReviewMapper.class);
        var service=new QuestionReviewService(questions,events);
        var q=new CyanQuestion(); q.setRevision(1); q.setReviewState("REVIEW");
        when(questions.lockQuestion(1L)).thenReturn(q);
        assertThrows(ServiceException.class,()->service.decide(1L,new QuestionReviewService.Decision("REJECT",1," ")));
        verifyNoInteractions(events);
    }
}
