package com.cyan.exam.system.controller;

import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.service.PracticalCodeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam/practical/{examId}/{taskId}")
public class PracticalCodeController {
    private final PracticalCodeService service;
    public PracticalCodeController(PracticalCodeService service) { this.service=service; }

    @GetMapping("/history")
    public AjaxResult history(@PathVariable Long examId,@PathVariable Long taskId,
            @RequestParam(required=false) Long userId) {
        return AjaxResult.success(service.history(examId,taskId,userId==null?SecurityUtils.getUserId():userId));
    }
    @PutMapping("/draft")
    public AjaxResult draft(@PathVariable Long examId,@PathVariable Long taskId,
            @RequestBody PracticalCodeService.Input input) {
        return AjaxResult.success(service.save(examId,taskId,input,false));
    }
    @PostMapping("/submit")
    public AjaxResult submit(@PathVariable Long examId,@PathVariable Long taskId,
            @RequestBody PracticalCodeService.Input input) {
        return AjaxResult.success(service.save(examId,taskId,input,true));
    }
}
