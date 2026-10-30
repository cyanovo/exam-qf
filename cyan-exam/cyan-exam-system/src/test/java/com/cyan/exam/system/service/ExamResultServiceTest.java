package com.cyan.exam.system.service;

import com.alibaba.fastjson.JSON;
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
class ExamResultServiceTest {
    @Mock CyanExamInfoMapper exams;
    @Mock ExamResultMapper results;
    @Mock ExamStudentMapper students;
    @Mock ExamSessionMapper sessions;
    @Mock ExamQuestionMapper questions;
    @Mock ExamAnswerRecordMapper answers;
    @Mock SysUserMapper users;
    @Mock ExamAccessScope scope;
    @Mock OnsiteMapper onsite;
    @InjectMocks ExamResultService service;

    @org.junit.jupiter.api.BeforeEach void injectOnsite() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "onsite", onsite);
    }

    Map<String, Object> version(String state) {
        return new HashMap<>(Map.of("id", 10L, "version", 1, "state", state, "requestedBy", 9L));
    }

    @Test void lockedStatesBlockEveryGradeWriter() {
        when(exams.lockByExamId(1L)).thenReturn(new CyanExamInfo());
        for (String state : List.of("REVIEW", "APPROVED", "RELEASED", "CORRECTION")) {
            when(results.latest(1L)).thenReturn(version(state));
            assertThrows(ServiceException.class, () -> service.lockForGrading(1L));
        }
        for (String state : List.of("REJECTED", "REOPENED")) {
            when(results.latest(1L)).thenReturn(version(state));
            assertNotNull(service.lockForGrading(1L));
        }
    }

    @Test void terminatedExamDoesNotWaitForRoundedEndTime() {
        var exam = new CyanExamInfo();
        exam.setPublishedAt(new Date());
        exam.setEndTime(new Date(System.currentTimeMillis() + 60000));
        exam.setLifecycle("TERMINATED");
        when(exams.lockByExamId(1L)).thenReturn(exam);
        when(results.latest(1L)).thenReturn(null);
        // Reaching reviewer validation proves termination takes precedence over the clock.
        var error = assertThrows(ServiceException.class, () -> service.requestReview(1L, "terminated"));
        assertEquals("请先绑定复核员", error.getMessage());
        verify(results).reviewers(1L);
    }

    @Test void competitionRankingPreservesTiesAndExcludesAbsentees() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new HashMap<>(Map.of("userId", 4L)));
        rows.add(new HashMap<>(Map.of("userId", 3L, "total", 70)));
        rows.add(new HashMap<>(Map.of("userId", 2L, "total", 90)));
        rows.add(new HashMap<>(Map.of("userId", 1L, "total", 90)));
        ExamResultService.rank(rows);
        assertEquals(List.of(1L, 2L, 3L, 4L), rows.stream().map(r -> r.get("userId")).toList());
        assertEquals(1, rows.get(0).get("rank")); assertEquals(1, rows.get(1).get("rank"));
        assertEquals(3, rows.get(2).get("rank")); assertNull(rows.get(3).get("rank"));
        assertEquals(true, rows.get(0).get("tied")); assertEquals(false, rows.get(2).get("tied"));
    }

    @Test void requesterAndGraderCannotApproveTheirOwnResults() {
        when(exams.lockByExamId(1L)).thenReturn(new CyanExamInfo());
        when(results.latest(1L)).thenReturn(version("REVIEW"));
        when(scope.actorId()).thenReturn(9L);
        when(results.isReviewer(1L, 9L)).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.transition(1L, "approve", "checked"));
        when(scope.actorId()).thenReturn(8L);
        when(results.isReviewer(1L, 8L)).thenReturn(1);
        when(results.hasGraded(1L, 8L)).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.transition(1L, "approve", "checked"));
        verify(results, never()).transition(any(), any(), any(), any());
    }

    @Test void superAdminCanReviewOwnResultsWithAuditedReasonButCannotSkipStages() {
        when(exams.lockByExamId(1L)).thenReturn(new CyanExamInfo());
        when(scope.actorId()).thenReturn(1L);
        var own = version("REVIEW");
        own.put("requestedBy", 1L);
        when(results.latest(1L)).thenReturn(own);
        when(results.transition(10L, "REVIEW", "APPROVED", 1L)).thenReturn(1);
        service.transition(1L, "approve", "已核对原始评分");
        verify(results).event(10L, 1L, "REVIEW", "APPROVED", "[超级管理员复核] 已核对原始评分");
        verify(results, never()).isReviewer(anyLong(), anyLong());
        assertThrows(ServiceException.class, () -> service.transition(1L, "release", "未通过不能发布"));
        assertThrows(ServiceException.class, () -> service.transition(1L, "approve", " "));
    }

    @Test void releaseRequiresApprovedStateAndBoundReviewer() {
        when(exams.lockByExamId(1L)).thenReturn(new CyanExamInfo());
        when(scope.actorId()).thenReturn(8L);
        when(results.latest(1L)).thenReturn(version("REVIEW"));
        assertThrows(ServiceException.class, () -> service.transition(1L, "release", "checked"));
        when(results.isReviewer(1L, 8L)).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.transition(1L, "release", "checked"));
        when(results.latest(1L)).thenReturn(version("APPROVED"));
        when(results.transition(10L, "APPROVED", "RELEASED", 8L)).thenReturn(1);
        service.transition(1L, "release", "checked");
        verify(results).event(10L, 8L, "APPROVED", "RELEASED", "checked");
    }

    @Test void correctionRequiresApprovalAndKeepsOldPayload() {
        when(exams.lockByExamId(1L)).thenReturn(new CyanExamInfo());
        when(scope.actorId()).thenReturn(8L);
        when(results.isReviewer(1L, 8L)).thenReturn(1);
        when(results.latest(1L)).thenReturn(version("CORRECTION"));
        when(results.transition(10L, "CORRECTION", "REOPENED", 8L)).thenReturn(1);
        service.transition(1L, "allowCorrection", "recheck");
        verify(results).event(10L, 8L, "CORRECTION", "REOPENED", "recheck");
        verify(results, never()).insert(any());
    }

    @Test void studentOnlyReceivesOwnPublishedRowNotRanksOrOthers() {
        when(scope.actorId()).thenReturn(2L);
        when(students.selectUserIdsByExamId(1L)).thenReturn(List.of(2L, 3L));
        var payload = Map.of("examName", "Exam", "rows", List.of(
                Map.of("userId", 2L, "total", 50, "scores", List.of()),
                Map.of("userId", 3L, "studentName", "SECRET_OTHER_STUDENT", "total", 100)));
        when(results.published(1L)).thenReturn(Map.of("payload", JSON.toJSONString(payload), "version", 1, "releasedAt", new Date()));
        String response = JSON.toJSONString(service.myResult(1L));
        assertFalse(response.contains("SECRET_OTHER_STUDENT"));
        assertFalse(response.contains("\"rows\""));
        assertTrue(response.contains("\"total\":50"));
        when(students.selectUserIdsByExamId(1L)).thenReturn(List.of(3L));
        assertThrows(ServiceException.class, () -> service.myResult(1L));
    }

    @Test void missingGradesAndLiveExamCannotBeSubmittedForReview() {
        when(results.latest(1L)).thenReturn(null);
        CyanExamInfo exam = new CyanExamInfo();
        exam.setPublishedAt(new Date()); exam.setEndTime(new Date(System.currentTimeMillis() + 10000));
        when(exams.lockByExamId(1L)).thenReturn(exam);
        assertThrows(ServiceException.class, () -> service.requestReview(1L, "done"));
        exam.setEndTime(new Date(0));
        when(results.reviewers(1L)).thenReturn(List.of(Map.of("userId", 8L)));
        CyanQuestion question = new CyanQuestion(); question.setQuestionId(3L); question.setMaxScore(10);
        when(questions.selectSnapshots(1L)).thenReturn(List.of(question));
        when(students.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        ExamSession session = new ExamSession(); session.setStatus("SUBMITTED");
        when(sessions.lockByExamAndUser(1L, 2L)).thenReturn(session);
        assertThrows(ServiceException.class, () -> service.requestReview(1L, "done"));
        verify(results, never()).insert(any());
    }

    @Test void malformedResultStateFailsClosed() {
        when(exams.lockByExamId(1L)).thenReturn(new CyanExamInfo());
        when(results.latest(1L)).thenReturn(new HashMap<>());
        assertThrows(ServiceException.class, () -> service.lockForGrading(1L));
        assertTrue(service.isLocked(1L));
    }

    @Test void onsiteJudgeCannotReviewOwnScores() {
        when(exams.lockByExamId(1L)).thenReturn(new CyanExamInfo());
        when(results.latest(1L)).thenReturn(version("REVIEW"));
        when(scope.actorId()).thenReturn(8L);
        when(results.isReviewer(1L, 8L)).thenReturn(1);
        when(onsite.hasGraded(1L, 8L)).thenReturn(1);
        assertThrows(ServiceException.class, () -> service.transition(1L, "approve", "checked"));
        verify(results, never()).transition(any(), any(), any(), any());
    }

    @Test void onsiteScoresAreIncludedInImmutableResultSnapshot() {
        var exam = new CyanExamInfo(); exam.setPublishedAt(new Date(0)); exam.setEndTime(new Date(0));
        exam.setExamName("combined");
        when(exams.lockByExamId(1L)).thenReturn(exam);
        when(results.reviewers(1L)).thenReturn(List.of(Map.of("userId", 8L)));
        var q = new CyanQuestion(); q.setQuestionId(3L); q.setMaxScore(10);
        when(questions.selectSnapshots(1L)).thenReturn(List.of(q));
        when(onsite.tasks(1L)).thenReturn(List.of(Map.of("id", 4L, "state", "LOCKED", "maxScore", 20, "title", "robot")));
        when(onsite.scores(4L)).thenReturn(List.of(Map.of("userId", 2L, "total", 15, "state", "SUBMITTED")));
        when(students.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        var session = new ExamSession(); session.setStatus("SUBMITTED");
        when(sessions.lockByExamAndUser(1L, 2L)).thenReturn(session);
        var answer = new ExamAnswerRecord(); answer.setQuestionId(3L); answer.setScore(8); answer.setGradedAt(new Date());
        when(answers.selectByExamAndUser(1L, 2L)).thenReturn(List.of(answer));
        when(results.latest(1L)).thenReturn(null, null, Map.of("id", 1L));
        service.requestReview(1L, "done");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(results).insert(captor.capture());
        var payload = JSON.parseObject((String) captor.getValue().get("payload"));
        assertEquals(30, payload.getIntValue("maxScore"));
        assertEquals(23, payload.getJSONArray("rows").getJSONObject(0).getIntValue("total"));
        assertEquals("ONSITE", payload.getJSONArray("rows").getJSONObject(0).getJSONArray("scores").getJSONObject(1).getString("kind"));
    }
}
