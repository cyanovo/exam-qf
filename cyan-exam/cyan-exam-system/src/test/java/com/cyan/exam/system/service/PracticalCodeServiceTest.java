package com.cyan.exam.system.service;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.*;
import com.cyan.exam.system.mapper.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticalCodeServiceTest {
    @Mock OnsiteMapper mapper;
    @Mock CyanExamInfoMapper exams;
    @Mock ExamStudentMapper students;
    @Mock ExamSessionMapper sessions;
    @Mock ExamAccessScope scope;
    @InjectMocks PracticalCodeService service;
    CyanExamInfo exam;
    ExamSession session;
    Map<String,Object> task;
    final String key="12345678-1234-1234-1234-123456789012";
    PracticalCodeService.Input input() { return new PracticalCodeService.Input("robot.forward()",key); }

    @BeforeEach void setup() {
        exam=new CyanExamInfo(); exam.setExamId(1L); exam.setOwnerUserId(7L); exam.setStatus("0");
        exam.setPublishedAt(new Date()); exam.setLifecycle("ACTIVE");
        exam.setStartTime(new Date(System.currentTimeMillis()-60000));
        exam.setEndTime(new Date(System.currentTimeMillis()+600000));
        session=new ExamSession(); session.setStatus("IN_PROGRESS"); session.setStartedAt(new Date());
        task=new HashMap<>(Map.of("id",3L,"examId",1L,"judgeId",8L,"state","OPEN"));
        lenient().when(scope.actorId()).thenReturn(9L);
        lenient().when(students.selectExamIdsByUserId(9L)).thenReturn(List.of(1L));
        lenient().when(exams.lockByExamId(1L)).thenReturn(exam);
        lenient().when(exams.selectCyanExamInfoByExamId(1L)).thenReturn(exam);
        lenient().when(mapper.task(3L)).thenReturn(task);
        lenient().when(mapper.codeRequest(anyLong(),anyLong(),anyString())).thenReturn(null);
        lenient().when(sessions.lockByExamAndUser(1L,9L)).thenReturn(session);
    }
    @Test void draftIsScopedAndDoesNotSubmitOrRun() {
        service.save(1L,3L,new PracticalCodeService.Input("robot.forward()",null),false);
        verify(mapper).saveCodeDraft(3L,9L,"robot.forward()");
        verify(mapper,never()).submitCode(any());
    }
    @Test void formalCreatesImmutableCodeAndAudit() {
        doAnswer(invocation->{ ((Map<String,Object>)invocation.getArgument(0)).put("id",15L); return 1; })
                .when(mapper).submitCode(any());
        service.save(1L,3L,input(),true);
        verify(mapper).submitCode(argThat(v->v.get("source").equals("robot.forward()") && v.get("user").equals(9L)));
        verify(mapper).event(eq(3L),eq(9L),eq(9L),eq("CODE_SUBMIT"),anyString(),contains("15"));
    }
    @Test void retryReturnsSameSubmissionEvenAfterFinishButRejectsKeyReuse() {
        var saved=Map.<String,Object>of("id",15L,"source","robot.forward()");
        when(mapper.codeRequest(3L,9L,key)).thenReturn(saved);
        session.setStatus("SUBMITTED");
        assertEquals(saved,service.save(1L,3L,input(),true));
        assertThrows(ServiceException.class,()->service.save(1L,3L,new PracticalCodeService.Input("other",key),true));
        verify(mapper,never()).submitCode(any());
    }
    @Test void cannotChangeFormalCodeAfterJudgeStarts() {
        when(mapper.scores(3L)).thenReturn(List.of(Map.of("userId",9L,"state","DRAFT")));
        assertThrows(ServiceException.class,()->service.save(1L,3L,input(),true));
        verify(mapper,never()).submitCode(any());
    }
    @Test void rejectsFinishedExpiredAndPersonalDeadline() {
        session.setStatus("SUBMITTED");
        assertThrows(ServiceException.class,()->service.save(1L,3L,input(),true));
        session.setStatus("IN_PROGRESS");
        exam.setEndTime(new Date(System.currentTimeMillis()-1));
        assertThrows(ServiceException.class,()->service.save(1L,3L,input(),false));
        exam.setEndTime(new Date(System.currentTimeMillis()+600000)); exam.setDurationMinutes(1);
        session.setStartedAt(new Date(System.currentTimeMillis()-120000));
        assertThrows(ServiceException.class,()->service.save(1L,3L,input(),true));
        verify(mapper,never()).submitCode(any()); verify(mapper,never()).saveCodeDraft(any(),any(),any());
    }
    @Test void rejectsForeignTaskAndUnassignedStudent() {
        task.put("examId",2L);
        assertThrows(ServiceException.class,()->service.save(1L,3L,input(),true));
        task.put("examId",1L);
        when(students.selectExamIdsByUserId(9L)).thenReturn(List.of());
        assertThrows(ServiceException.class,()->service.save(1L,3L,input(),false));
    }
    @Test void studentsCannotReadOthersCodeButAnyAuthorizedJudgeCan() {
        assertThrows(ServiceException.class,()->service.history(1L,3L,10L));
        verify(mapper,never()).codeHistory(any(),any());
        when(scope.actorId()).thenReturn(8L);
        when(mapper.canJudge(8L)).thenReturn(true);
        service.history(1L,3L,9L);
        when(scope.actorId()).thenReturn(10L);
        when(mapper.canJudge(10L)).thenReturn(true);
        service.history(1L,3L,9L);
        verify(mapper,times(2)).codeHistory(3L,9L);
        exam.setPublishedAt(null);
        assertThrows(ServiceException.class,()->service.history(1L,3L,9L));
    }
    @Test void limitsBytesAndRejectsBlankFormalCode() {
        assertThrows(ServiceException.class,()->service.save(1L,3L,new PracticalCodeService.Input("a".repeat(60001),key),false));
        assertThrows(ServiceException.class,()->service.save(1L,3L,new PracticalCodeService.Input("  ",key),true));
        assertThrows(ServiceException.class,()->service.save(1L,3L,new PracticalCodeService.Input("x",null),true));
    }
}
