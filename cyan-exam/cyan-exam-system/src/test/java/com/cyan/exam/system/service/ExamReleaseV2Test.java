package com.cyan.exam.system.service;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamReleaseV2Test {
    ExamPolicy.Policy policy(String tie,Long task) {
        return new ExamPolicy.Policy("Instructions",tie,task,"DETAIL",365,List.of());
    }
    @Test void policyRoundTripsAndRejectsNullRules() {
        var exam=new CyanExamInfo();
        exam.setPolicyJson(JSON.toJSONString(policy("TASK_SCORE",9L)));
        assertEquals(policy("TASK_SCORE",9L),ExamPolicy.read(exam));
        assertThrows(ServiceException.class,()->ExamPolicy.validate(policy(null,null)));
        assertThrows(ServiceException.class,()->ExamPolicy.validate(policy("TASK_SCORE",null)));
        var entry=new ExamPolicy.Entry("ONSITE",9L,"section","description","hint");
        assertThrows(ServiceException.class,()->ExamPolicy.validate(new ExamPolicy.Policy("","SHARED",null,"TOTAL",365,List.of(entry,entry))));
    }
    @Test void personalDeadlineNeverExceedsExamEnd() {
        var e=new CyanExamInfo();e.setDurationMinutes(10);e.setEndTime(new Date(2_000_000));
        var s=new ExamSession();s.setStartedAt(new Date(1_000_000));
        assertEquals(1_600_000,ExamPolicy.deadline(e,s).getTime());
        e.setEndTime(new Date(1_300_000));
        assertEquals(1_300_000,ExamPolicy.deadline(e,s).getTime());
        e.setDurationMinutes(0);
        assertEquals(e.getEndTime(),ExamPolicy.deadline(e,s));
    }
    Map<String,Object> row(long id,int task,Date submitted) {
        var r=new HashMap<String,Object>();r.put("userId",id);r.put("total",80);r.put("submittedAt",submitted);
        r.put("scores",List.of(Map.of("taskId",9L,"score",task,"maxScore",20,"title","Task")));
        return r;
    }
    @Test void tieRulesAndTaskRates() {
        var a=row(1,12,new Date(200));var b=row(2,18,new Date(100));var c=row(3,18,new Date(100));
        var rows=new ArrayList<>(List.of(a,b,c));
        ExamResultService.rank(rows,policy("TASK_SCORE",9L));
        assertEquals(2L,rows.get(0).get("userId"));assertEquals(1,rows.get(1).get("rank"));
        assertEquals(3,a.get("rank"));assertEquals(true,b.get("tied"));
        assertEquals(80.0,ExamResultService.taskStatistics(rows).get(0).get("rate"));
        ExamResultService.rank(rows,policy("SUBMITTED_AT",null));
        assertEquals(3,a.get("rank"));
        ExamResultService.rank(rows,policy("SHARED",null));
        assertTrue(rows.stream().allMatch(r->r.get("rank").equals(1)));
    }
    @Test void fileAllowlistRejectsActiveAndMalformedContent() throws Exception {
        assertThrows(ServiceException.class,()->ExamDossierService.sanitize("x.svg","<svg/>".getBytes()));
        assertThrows(ServiceException.class,()->ExamDossierService.sanitize("x.png","not an image".getBytes()));
        assertThrows(ServiceException.class,()->ExamDossierService.sanitize("x.json","{} {}".getBytes()));
        assertThrows(ServiceException.class,()->ExamDossierService.sanitize("x.json"," ".getBytes()));
        assertThrows(ServiceException.class,()->ExamDossierService.sanitize("x.txt",new byte[]{0}));
        assertThrows(ServiceException.class,()->ExamDossierService.sanitize("x.txt",new byte[]{(byte)0xff}));
        byte[] code="print('safe as stored text')".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(code,ExamDossierService.sanitize("answer.py",code));
    }
    @Test void lifecycleUsesEntryWindowAndPersistentTerminalStates() {
        long now=System.currentTimeMillis();
        var e=new CyanExamInfo();e.setStartTime(new Date(now+600_000));e.setEndTime(new Date(now+1_200_000));
        assertEquals("DRAFT",ExamPolicy.state(e));e.setPublishedAt(new Date());
        e.setEarlyEntryMinutes(5);assertEquals("PUBLISHED",ExamPolicy.state(e));
        e.setEarlyEntryMinutes(20);assertEquals("READY",ExamPolicy.state(e));
        e.setStartTime(new Date(now-1000));assertEquals("RUNNING",ExamPolicy.state(e));
        e.setLifecycle("TERMINATED");assertEquals("TERMINATED",ExamPolicy.state(e));
        e.setLifecycle("ARCHIVED");assertEquals("ARCHIVED",ExamPolicy.state(e));
    }
}
