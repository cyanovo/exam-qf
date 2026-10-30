package com.cyan.exam.system.controller;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.system.service.ExamResultService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam/results")
public class ExamResultController {
    private final ExamResultService service;
    public ExamResultController(ExamResultService service) { this.service = service; }
    public record Action(String reason) {}
    public record Assignment(Long userId) {}

    @GetMapping("/review-exams")
    @PreAuthorize("@ss.hasPermi('exam:results:review')")
    public AjaxResult reviewExams() { return AjaxResult.success(service.reviewExams()); }

    @GetMapping("/{examId}")
    @PreAuthorize("@ss.hasAnyPermi('exam:grading:list,exam:results:review')")
    public AjaxResult detail(@PathVariable Long examId) { return AjaxResult.success(service.detail(examId)); }
    @GetMapping("/{examId}/history")
    @PreAuthorize("@ss.hasAnyPermi('exam:grading:list,exam:results:review')")
    public AjaxResult history(@PathVariable Long examId) { return AjaxResult.success(service.history(examId)); }

    @PostMapping("/{examId}/reviewers")
    @PreAuthorize("@ss.hasPermi('exam:results:assign')")
    @Log(title = "绑定成绩复核员", businessType = BusinessType.UPDATE)
    public AjaxResult assign(@PathVariable Long examId, @RequestBody Assignment body) {
        service.assign(examId, body.userId()); return AjaxResult.success();
    }

    @PostMapping("/{examId}/request")
    @PreAuthorize("@ss.hasPermi('exam:grading:edit')")
    @Log(title = "提交成绩复核", businessType = BusinessType.UPDATE)
    public AjaxResult request(@PathVariable Long examId, @RequestBody Action body) {
        service.requestReview(examId, body.reason()); return AjaxResult.success();
    }

    @PostMapping("/{examId}/correction")
    @PreAuthorize("@ss.hasPermi('exam:grading:edit')")
    @Log(title = "申请成绩更正", businessType = BusinessType.UPDATE)
    public AjaxResult correction(@PathVariable Long examId, @RequestBody Action body) {
        service.transition(examId, "requestCorrection", body.reason()); return AjaxResult.success();
    }

    @PostMapping("/{examId}/review/{action}")
    @PreAuthorize("@ss.hasPermi('exam:results:review')")
    @Log(title = "成绩复核与发布", businessType = BusinessType.UPDATE)
    public AjaxResult review(@PathVariable Long examId, @PathVariable String action, @RequestBody Action body) {
        if ("requestCorrection".equals(action)) return AjaxResult.error("请使用更正申请入口");
        service.transition(examId, action, body.reason()); return AjaxResult.success();
    }

    @GetMapping("/mine")
    public AjaxResult mine() { return AjaxResult.success(service.myResults()); }
    @GetMapping("/mine/{examId}")
    public AjaxResult mine(@PathVariable Long examId) { return AjaxResult.success(service.myResult(examId)); }

    @PostMapping("/{examId}/export")
    @PreAuthorize("@ss.hasPermi('exam:results:export')")
    @Log(title = "导出锁定成绩", businessType = BusinessType.EXPORT)
    public void export(@PathVariable Long examId, HttpServletResponse response) throws IOException {
        Map<String, Object> detail = service.detail(examId);
        if (!java.util.Set.of("APPROVED", "RELEASED", "CORRECTION").contains(detail.get("state"))) {
            throw new com.cyan.exam.common.exception.ServiceException("只能导出复核通过的锁定成绩");
        }
        var snapshot = JSON.parseObject(JSON.toJSONString(detail.get("snapshot")));
        boolean identity = com.cyan.exam.common.utils.SecurityUtils.hasPermi("exam:results:exportIdentity");
        StringBuilder csv = new StringBuilder("\uFEFFexamId,examName,version,candidateRef,");
        if (identity) csv.append("name,");
        csv.append("questionId,score,maxScore,total,rank,tied,status,kind,taskId,title\r\n");
        int candidate = 0;
        for (Object value : snapshot.getJSONArray("rows")) {
            candidate++;
            var row = (com.alibaba.fastjson.JSONObject) value;
            var scores = row.getJSONArray("scores");
            if (scores.isEmpty()) scores.add(new com.alibaba.fastjson.JSONObject());
            for (Object scoreValue : scores) {
                var score = (com.alibaba.fastjson.JSONObject) scoreValue;
                java.util.List<Object> cells = new java.util.ArrayList<>();
                java.util.Collections.addAll(cells,examId,snapshot.get("examName"),detail.get("version"),
                    identity ? row.get("userId") : "C"+candidate);
                if (identity) cells.add(row.get("studentName"));
                java.util.Collections.addAll(cells, score.get("questionId"), score.get("score"), score.get("maxScore"),
                    row.get("total"), row.get("rank"), row.get("tied"), row.get("status"),
                    score.getOrDefault("kind", "THEORY"), score.get("taskId"), score.get("title"));
                for (int i = 0; i < cells.size(); i++) {
                    if (i > 0) csv.append(',');
                    csv.append(csvCell(cells.get(i)));
                }
                csv.append("\r\n");
            }
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=exam-" + examId + "-v" + detail.get("version") + ".csv");
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.matches("(?s)^\\s*[=+@-].*") || text.startsWith("\t") || text.startsWith("\r")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
