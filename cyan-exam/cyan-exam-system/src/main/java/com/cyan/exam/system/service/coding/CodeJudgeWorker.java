package com.cyan.exam.system.service.coding;

import com.alibaba.fastjson.*;
import com.cyan.exam.system.mapper.CodeSubmissionMapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CodeJudgeWorker {
    private static final Logger LOG = LoggerFactory.getLogger(CodeJudgeWorker.class);
    private final CodeSubmissionMapper submissions;
    private final CodeSubmissionService service;
    private final Judge0Client runner;
    public CodeJudgeWorker(CodeSubmissionMapper submissions, CodeSubmissionService service, Judge0Client runner) {
        this.submissions = submissions; this.service = service; this.runner = runner;
    }

    @Scheduled(fixedDelay = 1500)
    public void tick() {
        if (!runner.enabled()) return;
        for (Long id : submissions.ready()) {
            String lease = UUID.randomUUID().toString();
            if (submissions.claim(id, lease) != 1) continue;
            try { process(id, lease); }
            catch (Exception ex) {
                submissions.fail(id, lease);
                LOG.warn("Judge infrastructure failure for submission {} ({})", id, ex.getClass().getSimpleName());
            }
        }
    }

    void process(Long id, String lease) throws Exception {
        Map<String, Object> row = submissions.get(id);
        var q = service.question(((Number) row.get("examId")).longValue(), ((Number) row.get("questionId")).longValue());
        if (!CodingRules.hash(q.getAnswer()).equals(row.get("snapshotHash"))) throw new IllegalStateException("Snapshot changed");
        boolean sample = "SAMPLE".equals(row.get("mode"));
        var cases = CodingRules.parse(q.getAnswer(), q.getMaxScore()).cases().stream().filter(c -> c.publicCase() == sample).toList();
        JSONObject progress = row.get("progress") == null ? new JSONObject() : JSON.parseObject((String) row.get("progress"));
        int index = progress.getIntValue("index");
        int score = progress.getIntValue("score");
        JSONArray feedback = progress.getJSONArray("feedback");
        if (feedback == null) feedback = new JSONArray();
        if (index >= cases.size()) {
            submissions.complete(id, lease, score, JSON.toJSONString(feedback));
            return;
        }
        CodingRules.Case test = cases.get(index);
        String token = progress.getString("token");
        if (token == null) {
            progress.put("token", runner.submit((String) row.get("source"), test.input()));
            progress.put("started", System.currentTimeMillis());
        } else {
            if (System.currentTimeMillis() - progress.getLongValue("started") > 120000) throw new IllegalStateException("Runner timeout");
            JSONObject result = runner.poll(token);
            int status = result.getJSONObject("status").getIntValue("id");
            if (status >= 3) {
                if (status == 13 || status == 14 || status > 14) throw new IllegalStateException("Runner internal error");
                String output = Judge0Client.decode(result.getString("stdout"));
                boolean passed = status == 3 && CodingRules.matches(test, output);
                if (passed) score += test.weight();
                if (sample) {
                    feedback.add(Map.of("case", index + 1, "passed", passed, "stdout", output,
                            "stderr", Judge0Client.decode(result.getString("stderr")),
                            "compileOutput", Judge0Client.decode(result.getString("compile_output")), "status", status));
                }
                progress.put("index", index + 1); progress.put("score", score);
                progress.put("feedback", feedback); progress.remove("token");
            }
        }
        submissions.progress(id, lease, progress.toJSONString());
    }
}
