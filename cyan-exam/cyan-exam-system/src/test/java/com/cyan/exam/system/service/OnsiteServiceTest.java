package com.cyan.exam.system.service;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.mapper.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnsiteServiceTest {
    @Mock OnsiteMapper mapper;
    @Mock CyanExamInfoMapper exams;
    @Mock SysUserMapper users;
    @Mock ExamAccessScope scope;
    @Mock ExamResultMapper results;
    @Mock CyanQuestionMapper questionBank;
    @InjectMocks OnsiteService service;
    CyanExamInfo exam;
    Map<String, Object> task;

    @BeforeEach void setup() {
        lenient().when(results.latest(anyLong())).thenReturn(null);
        exam = new CyanExamInfo(); exam.setExamId(1L); exam.setOwnerUserId(7L);
        exam.setPublishedAt(new Date());exam.setStartTime(new Date(System.currentTimeMillis()-60000));
        lenient().when(exams.selectCyanExamInfoByExamId(1L)).thenReturn(exam);
        task = new HashMap<>(Map.of("id", 3L, "examId", 1L, "judgeId", 8L, "state", "OPEN",
                "rubric", "[{\"title\":\"accuracy\",\"maxScore\":10},{\"title\":\"speed\",\"maxScore\":5}]"));
    }
    @Test void bankQuestionBecomesIndependentOnsiteTask() {
        var question = new CyanQuestion();
        question.setQuestionId(21L);
        question.setQuestionType("ROBOT_PRACTICAL");
        question.setReviewState("PUBLISHED");
        question.setStatus("0");
        question.setTitle("热身准备任务");
        question.setSubTitle("运行机器人并观察动作");
        question.setAnswer("[{\"title\":\"正确转动舵机\",\"maxScore\":15}]");
        when(questionBank.selectCyanQuestionByQuestionId(21L)).thenReturn(question);
        exam.setPublishedAt(null);
        exam.setLifecycle("ACTIVE");
        exam.setStartTime(new Date(System.currentTimeMillis() + 3600000));
        when(exams.lockByExamId(1L)).thenReturn(exam);
        when(scope.actorId()).thenReturn(7L);
        when(mapper.insert(anyMap())).thenAnswer(invocation -> {
            ((Map<String, Object>) invocation.getArgument(0)).put("id", 3L);
            return 1;
        });
        assertEquals(3L, service.createFromBank(1L, 21L, 10, null));
        verify(mapper).insert(argThat(value -> "热身准备任务".equals(value.get("title"))
                && ((String) value.get("rubric")).contains("正确转动舵机")
                && Integer.valueOf(15).equals(value.get("maxScore"))
                && Long.valueOf(0).equals(value.get("judgeId"))));
    }

    @Test void ordinaryPythonCannotBecomeRobotTask() {
        var question = new CyanQuestion();
        question.setQuestionType("PYTHON");
        question.setStatus("0");
        when(questionBank.selectCyanQuestionByQuestionId(21L)).thenReturn(question);
        assertThrows(ServiceException.class, () -> service.createFromBank(1L, 21L, 10, 8L));
        verify(mapper, never()).insert(anyMap());
    }
    void writable() {
        when(exams.lockByExamId(1L)).thenReturn(exam);
        when(mapper.task(3L)).thenReturn(task);
    }
    void scoring() {
        writable();
        when(scope.actorId()).thenReturn(8L);
        when(mapper.canJudge(8L)).thenReturn(true);
        when(mapper.roster(1L)).thenReturn(List.of(Map.of("userId", 9L)));
    }
    Map<String, Object> score(String state) {
        return Map.of("userId", 9L, "judgeId", 8L, "state", state, "revision", 1, "total", 12);
    }

    @Test void validatesRubricAndPartialDraft() {
        var items = List.of(new OnsiteService.Item("accuracy", 10), new OnsiteService.Item("speed", 5));
        assertEquals(15, OnsiteService.validateItems(items));
        assertEquals(10, OnsiteService.validatePoints(items, Arrays.asList(10, null), false));
        assertThrows(ServiceException.class, () -> OnsiteService.validatePoints(items, Arrays.asList(10, null), true));
        assertThrows(ServiceException.class, () -> OnsiteService.validatePoints(items, List.of(11, 0), true));
        assertThrows(ServiceException.class, () -> OnsiteService.validatePoints(items, List.of(-1, 5), true));
        assertThrows(ServiceException.class, () -> OnsiteService.validatePoints(items, List.of(1), false));
        assertThrows(ServiceException.class, () -> OnsiteService.validateItems(List.of(new OnsiteService.Item("", 10))));
    }

    @Test void authorizedJudgeSavesDraftAndAudit() {
        scoring();
        when(mapper.codeHistory(3L,9L)).thenReturn(List.of(Map.of("id",15L)));
        service.score(1L, 3L, 9L, new OnsiteService.ScoreInput(Arrays.asList(10, null), "draft", 0, false,15L));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).save(captor.capture());
        assertEquals(10, captor.getValue().get("total"));
        assertEquals("DRAFT", captor.getValue().get("state"));
        assertEquals(15L,captor.getValue().get("submissionId"));
        verify(mapper).event(eq(3L), eq(9L), eq(8L), eq("SAVE"), eq("draft"), contains("after"));
    }

    @Test void scoreRequiresSubmittedCodeAndCurrentVersion() {
        scoring();
        assertThrows(ServiceException.class,()->service.score(1L,3L,9L,
                new OnsiteService.ScoreInput(List.of(10,5),"",0,true,15L)));
        when(mapper.codeHistory(3L,9L)).thenReturn(List.of(Map.of("id",16L)));
        assertThrows(ServiceException.class,()->service.score(1L,3L,9L,
                new OnsiteService.ScoreInput(List.of(10,5),"",0,true,15L)));
        assertThrows(ServiceException.class,()->service.score(1L,3L,9L,
                new OnsiteService.ScoreInput(List.of(10,5),"",0,true)));
        verify(mapper,never()).save(any());
    }
    @Test void endedBlankCanBeSubmittedAsZeroWithoutInventingCode() {
        scoring();
        exam.setEndTime(new Date(System.currentTimeMillis() - 1000));
        service.score(1L,3L,9L,new OnsiteService.ScoreInput(List.of(0,0),"",0,true,null,true));
        ArgumentCaptor<Map<String,Object>> value = ArgumentCaptor.forClass(Map.class);
        verify(mapper).save(value.capture());
        assertEquals(0,value.getValue().get("total"));
        assertEquals("SUBMITTED",value.getValue().get("state"));
        assertNull(value.getValue().get("submissionId"));
        verify(mapper,never()).submitCode(any());
        verify(mapper).event(eq(3L),eq(9L),eq(8L),eq("SUBMIT"),contains("0"),anyString());
    }
    @Test void blankCannotReceivePointsOrBeMarkedBeforeEnd() {
        scoring();
        exam.setEndTime(new Date(System.currentTimeMillis() + 60000));
        assertThrows(ServiceException.class,()->service.score(1L,3L,9L,
                new OnsiteService.ScoreInput(List.of(0,0),"",0,true,null,true)));
        exam.setLifecycle("TERMINATED");
        assertThrows(ServiceException.class,()->service.score(1L,3L,9L,
                new OnsiteService.ScoreInput(List.of(1,0),"",0,true,null,true)));
        assertThrows(ServiceException.class,()->service.score(1L,3L,9L,
                new OnsiteService.ScoreInput(List.of(0,0),"",0,false,null,true)));
        when(mapper.codeHistory(3L,9L)).thenReturn(List.of(Map.of("id",15L)));
        assertThrows(ServiceException.class,()->service.score(1L,3L,9L,
                new OnsiteService.ScoreInput(List.of(0,0),"",0,true,15L,true)));
        verify(mapper,never()).save(any());
    }

    @Test void outsidersCannotScoreEvenAsExamOwner() {
        writable();
        when(scope.actorId()).thenReturn(7L);
        assertThrows(ServiceException.class, () -> service.score(1L, 3L, 9L, new OnsiteService.ScoreInput(List.of(10, 5), "", 0, true)));
        verify(mapper, never()).save(any());
    }

    @Test void rejectsOutOfRosterAndForeignTask() {
        writable(); when(scope.actorId()).thenReturn(8L);
        assertThrows(ServiceException.class, () -> service.score(1L, 3L, 9L, new OnsiteService.ScoreInput(List.of(10, 5), "", 0, true)));
        task.put("examId", 2L);
        assertThrows(ServiceException.class, () -> service.score(1L, 3L, 9L, new OnsiteService.ScoreInput(List.of(10, 5), "", 0, true)));
        verify(mapper, never()).save(any());
    }

    @Test void submittedScoreCannotBeOverwrittenOrDuplicated() {
        scoring();
        when(mapper.scores(3L)).thenReturn(List.of(score("SUBMITTED")));
        assertThrows(ServiceException.class, () -> service.score(1L, 3L, 9L, new OnsiteService.ScoreInput(List.of(10, 5), "", 1, true)));
        verify(mapper, never()).save(any());
    }

    @Test void staleDraftCannotOverwriteNewerRevision() {
        scoring(); when(mapper.scores(3L)).thenReturn(List.of(score("DRAFT")));
        assertThrows(ServiceException.class, () -> service.score(1L, 3L, 9L, new OnsiteService.ScoreInput(List.of(10, 5), "", 0, true)));
        verify(mapper, never()).save(any());
    }

    @Test void anotherAuthorizedJudgeCanScoreUnclaimedSubmission() {
        writable();
        when(scope.actorId()).thenReturn(10L);
        when(mapper.canJudge(10L)).thenReturn(true);
        when(mapper.roster(1L)).thenReturn(List.of(Map.of("userId",9L)));
        when(mapper.codeHistory(3L,9L)).thenReturn(List.of(Map.of("id",15L)));
        service.score(1L,3L,9L,new OnsiteService.ScoreInput(List.of(10,5),"",0,true,15L));
        verify(mapper).save(argThat(s -> Long.valueOf(10L).equals(s.get("judgeId"))));
    }

    @Test void anotherJudgeCannotOverwriteClaimedDraft() {
        writable();
        when(scope.actorId()).thenReturn(10L);
        when(mapper.canJudge(10L)).thenReturn(true);
        when(mapper.roster(1L)).thenReturn(List.of(Map.of("userId",9L)));
        when(mapper.scores(3L)).thenReturn(List.of(score("DRAFT")));
        var error=assertThrows(ServiceException.class,()->service.score(1L,3L,9L,
                new OnsiteService.ScoreInput(List.of(10,5),"",1,true,15L)));
        assertEquals(409,error.getCode());
        verify(mapper,never()).save(any());
    }

    @Test void publicTaskVisibleToMultipleJudgesButNotStudentsOrBeforePublication() {
        exam.setExamName("Public grading");
        when(mapper.tasks(1L)).thenReturn(List.of(task));
        when(scope.actorId()).thenReturn(10L);
        when(mapper.canJudge(10L)).thenReturn(true);
        when(mapper.scores(3L)).thenReturn(List.of(score("DRAFT")));
        when(mapper.submittedUsers(3L)).thenReturn(List.of(9L));
        var response=service.detail(1L);
        var tasks=(List<Map<String,Object>>)response.get("tasks");
        assertEquals(1,tasks.size());
        assertEquals(true,tasks.get(0).get("canScore"));
        assertEquals(List.of(9L),tasks.get(0).get("submittedUsers"));
        assertEquals(false,((List<Map<String,Object>>)tasks.get(0).get("scores")).get(0).get("canEdit"));
        when(scope.actorId()).thenReturn(8L);
        when(mapper.canJudge(8L)).thenReturn(true);
        tasks=(List<Map<String,Object>>)service.detail(1L).get("tasks");
        assertEquals(true,((List<Map<String,Object>>)tasks.get(0).get("scores")).get(0).get("canEdit"));
        exam.setPublishedAt(null);
        assertThrows(ServiceException.class,()->service.detail(1L));
        exam.setPublishedAt(new Date());
        when(scope.actorId()).thenReturn(9L);
        assertThrows(ServiceException.class,()->service.detail(1L));
    }

    @Test void onlyActualScorerCanRequestWithdrawal() {
        writable();
        when(scope.actorId()).thenReturn(10L);
        when(mapper.canJudge(10L)).thenReturn(true);
        when(mapper.scores(3L)).thenReturn(List.of(score("SUBMITTED")));
        assertThrows(ServiceException.class,()->service.withdrawal(1L,3L,9L,"request","fix"));
        when(scope.actorId()).thenReturn(8L);
        when(mapper.canJudge(8L)).thenReturn(true);
        service.withdrawal(1L,3L,9L,"request","fix");
        verify(mapper).scoreState(3L,9L,"WITHDRAWAL");
    }

    @Test void resultLockBlocksOnsiteWrites() {
        when(exams.lockByExamId(1L)).thenReturn(exam);
        when(results.latest(1L)).thenReturn(Map.of("state", "REVIEW"));
        assertThrows(ServiceException.class, () -> service.score(1L, 3L, 9L, new OnsiteService.ScoreInput(List.of(10, 5), "", 1, true)));
        verify(mapper, never()).save(any());
    }

    @Test void taskCannotLockUntilEveryStudentSubmitted() {
        writable(); when(exams.selectCyanExamInfoByExamId(1L)).thenReturn(exam);
        when(mapper.roster(1L)).thenReturn(List.of(Map.of("userId", 9L)));
        when(mapper.scores(3L)).thenReturn(List.of(score("DRAFT")));
        assertThrows(ServiceException.class, () -> service.taskAction(1L, 3L, "lock", "checked"));
        verify(mapper, never()).state(any(), any());
    }

    @Test void withdrawalRequiresIndependentApproval() {
        writable(); when(exams.selectCyanExamInfoByExamId(1L)).thenReturn(exam);
        when(scope.actorId()).thenReturn(8L);
        when(mapper.scores(3L)).thenReturn(List.of(score("WITHDRAWAL")));
        assertThrows(ServiceException.class, () -> service.withdrawal(1L, 3L, 9L, "approve", "correct"));
        verify(mapper, never()).scoreState(any(), any(), any());
        when(scope.actorId()).thenReturn(7L);
        service.withdrawal(1L, 3L, 9L, "approve", "correct");
        verify(mapper).scoreState(3L, 9L, "DRAFT");
        verify(mapper).event(eq(3L), eq(9L), eq(7L), eq("WITHDRAW_APPROVE"), eq("correct"), contains("WITHDRAWAL"));
    }
}
