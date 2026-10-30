package com.cyan.exam.system.controller;

import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.system.service.OnsiteService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam/onsite")
public class OnsiteController {
    private final OnsiteService service;
    public OnsiteController(OnsiteService service) { this.service = service; }
    public record Action(String reason) {}
    public record BankTask(Long questionId, Integer durationMinutes, Long judgeId) {}

    // The service authorizes managers and the shared pool of permitted judges.
    @GetMapping("/exams")
    public AjaxResult exams() { return AjaxResult.success(service.exams()); }
    @GetMapping("/{examId}")
    public AjaxResult detail(@PathVariable Long examId) { return AjaxResult.success(service.detail(examId)); }
    @PostMapping("/{examId}/tasks")
    @Log(title = "创建现场任务", businessType = BusinessType.INSERT)
    public AjaxResult create(@PathVariable Long examId, @RequestBody OnsiteService.TaskInput input) {
        return AjaxResult.success(service.create(examId, input));
    }
    @PostMapping("/{examId}/tasks/from-bank")
    @Log(title = "从题库创建现场任务", businessType = BusinessType.INSERT)
    public AjaxResult createFromBank(@PathVariable Long examId, @RequestBody BankTask input) {
        return AjaxResult.success(service.createFromBank(examId, input.questionId(),
                input.durationMinutes(), input.judgeId()));
    }
    @PostMapping("/{examId}/tasks/{taskId}/{action}")
    @Log(title = "现场任务状态", businessType = BusinessType.UPDATE)
    public AjaxResult action(@PathVariable Long examId, @PathVariable Long taskId, @PathVariable String action,
            @RequestBody Action body) {
        service.taskAction(examId, taskId, action, body.reason()); return AjaxResult.success();
    }
    @PutMapping("/{examId}/tasks/{taskId}/scores/{userId}")
    @Log(title = "现场评分", businessType = BusinessType.UPDATE)
    public AjaxResult score(@PathVariable Long examId, @PathVariable Long taskId, @PathVariable Long userId,
            @RequestBody OnsiteService.ScoreInput input) {
        service.score(examId, taskId, userId, input); return AjaxResult.success();
    }
    @PostMapping("/{examId}/tasks/{taskId}/scores/{userId}/{action}")
    @Log(title = "现场评分撤回审批", businessType = BusinessType.UPDATE)
    public AjaxResult withdraw(@PathVariable Long examId, @PathVariable Long taskId, @PathVariable Long userId,
            @PathVariable String action, @RequestBody Action body) {
        service.withdrawal(examId, taskId, userId, action, body.reason()); return AjaxResult.success();
    }
}
