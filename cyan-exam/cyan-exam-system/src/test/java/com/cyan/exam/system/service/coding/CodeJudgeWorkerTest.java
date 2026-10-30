package com.cyan.exam.system.service.coding;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.mapper.CodeSubmissionMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodeJudgeWorkerTest {
    final CodeSubmissionMapper mapper = mock(CodeSubmissionMapper.class);
    final CodeSubmissionService service = mock(CodeSubmissionService.class);
    final Judge0Client runner = mock(Judge0Client.class);
    final CodeJudgeWorker worker = new CodeJudgeWorker(mapper, service, runner);
    final String config = "{\"cases\":["
            + "{\"input\":\"public\",\"expected\":\"ok\",\"public\":true,\"weight\":0,\"comparison\":\"EXACT\"},"
            + "{\"input\":\"secret\",\"expected\":\"ok\",\"public\":false,\"weight\":10,\"comparison\":\"EXACT\"}]}";

    Map<String, Object> setup(String mode) {
        var row = new HashMap<String, Object>(Map.of("examId", 1L, "questionId", 2L,
                "mode", mode, "source", "print('ok')", "snapshotHash", CodingRules.hash(config)));
        CyanQuestion q = new CyanQuestion();
        q.setAnswer(config); q.setMaxScore(10);
        when(service.question(1L, 2L)).thenReturn(q);
        when(mapper.get(3L)).thenReturn(row);
        return row;
    }

    @Test void sampleRunsOnlyPublicCases() throws Exception {
        setup("SAMPLE");
        when(runner.submit(anyString(), anyString())).thenReturn("runner-token");
        worker.process(3L, "lease");
        verify(runner).submit("print('ok')", "public");
        verify(runner, never()).submit(anyString(), eq("secret"));
    }

    @Test void formalRunsOnlyHiddenCases() throws Exception {
        setup("FORMAL");
        when(runner.submit(anyString(), anyString())).thenReturn("runner-token");
        worker.process(3L, "lease");
        verify(runner).submit("print('ok')", "secret");
        verify(runner, never()).submit(anyString(), eq("public"));
    }

    @Test void infrastructureFailureDoesNotBecomeStudentZero() throws Exception {
        var row = setup("FORMAL");
        row.put("progress", "{\"token\":\"token\",\"started\":" + System.currentTimeMillis() + "}");
        when(runner.enabled()).thenReturn(true);
        when(mapper.ready()).thenReturn(List.of(3L));
        when(mapper.claim(eq(3L), anyString())).thenReturn(1);
        when(runner.poll("token")).thenReturn(JSON.parseObject("{\"status\":{\"id\":13}}"));
        worker.tick();
        verify(mapper).fail(eq(3L), anyString());
        verify(mapper, never()).complete(anyLong(), anyString(), anyInt(), anyString());
    }

    @Test void staleSnapshotNeverExecutes() {
        setup("FORMAL").put("snapshotHash", "stale");
        assertThrows(IllegalStateException.class, () -> worker.process(3L, "lease"));
        verifyNoInteractions(runner);
    }

    @Test void synchronousFormalFeedbackDoesNotRetainHiddenOutput() throws Exception {
        setup("FORMAL");
        when(runner.synchronous()).thenReturn(true);
        when(runner.run(anyString(), eq("secret"))).thenReturn(JSON.parseObject(
                "{\"status\":{\"id\":3},\"stdout\":\"b2s=\",\"stderr\":\"c2VjcmV0\",\"verdict\":\"Accepted\"}"));
        worker.process(3L, "lease");
        var progress = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mapper).progress(eq(3L), eq("lease"), progress.capture());
        var point = JSON.parseObject(progress.getValue()).getJSONArray("feedback").getJSONObject(0);
        assertEquals(10, point.getIntValue("score"));
        assertFalse(point.containsKey("stdout"));
        assertFalse(point.containsKey("stderr"));
        assertFalse(progress.getValue().contains("secret"));
    }
}
