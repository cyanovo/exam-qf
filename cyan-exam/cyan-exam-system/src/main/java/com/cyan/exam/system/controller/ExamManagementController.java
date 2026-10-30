package com.cyan.exam.system.controller;

import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.system.service.ExamManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam/manage")
public class ExamManagementController {
    private final ExamManagementService service;
    public ExamManagementController(ExamManagementService service) { this.service=service; }
    public record Action(String reason) {}
    public record ForceDeletion(String examName, String reason) {}
    @DeleteMapping("/{id}/force")
    @PreAuthorize("@ss.hasPermi('exam:exam:remove')")
    @Log(title="强制删除考试",businessType=BusinessType.DELETE)
    public AjaxResult forceDelete(@PathVariable Long id, @RequestBody ForceDeletion body) {
        service.forceDelete(id, body == null ? null : body.examName(), body == null ? null : body.reason());
        return AjaxResult.success();
    }
    @GetMapping("/{id}/questions")
    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    public AjaxResult questionOptions(@PathVariable Long id) { return AjaxResult.success(service.questionOptions(id)); }
    @GetMapping("/{id}/robot-questions")
    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    public AjaxResult robotOptions(@PathVariable Long id) { return AjaxResult.success(service.robotOptions(id)); }
    @GetMapping("/{id}/students")
    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    public AjaxResult candidates(@PathVariable Long id) { return AjaxResult.success(service.candidates(id)); }
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('exam:exam:list')")
    public AjaxResult detail(@PathVariable Long id) { return AjaxResult.success(service.detail(id)); }
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    @Log(title="考试配置",businessType=BusinessType.UPDATE)
    public AjaxResult configure(@PathVariable Long id,@RequestBody ExamManagementService.Configuration body) {
        service.configure(id,body); return AjaxResult.success();
    }
    @PostMapping("/{id}/{action}")
    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    @Log(title="场次状态",businessType=BusinessType.UPDATE)
    public AjaxResult action(@PathVariable Long id,@PathVariable String action,@RequestBody Action body) {
        service.action(id,action,body.reason()); return AjaxResult.success();
    }
}
