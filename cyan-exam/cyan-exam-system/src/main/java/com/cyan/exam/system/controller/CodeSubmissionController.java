package com.cyan.exam.system.controller;

import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.system.service.coding.CodeSubmissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam/code")
public class CodeSubmissionController {
    private final CodeSubmissionService service;
    public CodeSubmissionController(CodeSubmissionService service) { this.service = service; }
    @PostMapping("/{examId}")
    @Log(title = "编程提交", businessType = BusinessType.INSERT, isSaveRequestData = false)
    public AjaxResult submit(@PathVariable Long examId, @RequestBody CodeSubmissionService.Request body) {
        return AjaxResult.success(service.submit(examId, body));
    }
    @GetMapping("/{examId}/history/{questionId}")
    public AjaxResult history(@PathVariable Long examId, @PathVariable Long questionId) { return AjaxResult.success(service.history(examId, questionId)); }
    @GetMapping("/submission/{id}")
    public AjaxResult submission(@PathVariable Long id) { return AjaxResult.success(service.get(id)); }
    @PostMapping("/submission/{id}/retry")
    @PreAuthorize("@ss.hasPermi('exam:grading:edit')")
    @Log(title = "重试系统评测失败", businessType = BusinessType.UPDATE)
    public AjaxResult retry(@PathVariable Long id) { service.retry(id); return AjaxResult.success(); }
}
