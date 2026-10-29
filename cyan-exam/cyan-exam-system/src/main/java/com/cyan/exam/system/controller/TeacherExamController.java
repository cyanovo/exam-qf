package com.cyan.exam.web.controller.exam;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.cyan.exam.common.core.controller.BaseController;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.system.service.IExamGradingService;
import com.cyan.exam.system.domain.ExamScoreSubmission;
import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.common.utils.SecurityUtils;

@RestController
@RequestMapping("/exam/grading")
public class TeacherExamController extends BaseController {

    @Autowired
    private IExamGradingService gradingService;

    /**
     * 获取当前用户资源权限范围内可批改的考试列表
     */
    @PreAuthorize("@ss.hasPermi('exam:grading:list')")
    @GetMapping("/exams")
    public AjaxResult examList() {
        return success(gradingService.getExamListForGrading());
    }

    /**
     * 获取某考试下的学生列表及批改进度
     */
    @PreAuthorize("@ss.hasPermi('exam:grading:list')")
    @GetMapping("/students/{examId}")
    public AjaxResult studentProgress(@PathVariable Long examId) {
        return success(gradingService.getStudentProgress(examId));
    }

    /**
     * 获取某个学生某场考试的答题详情（用于批改）
     */
    @PreAuthorize("@ss.hasPermi('exam:grading:detail')")
    @GetMapping("/detail/{examId}/{userId}")
    public AjaxResult getAnswerDetail(@PathVariable Long examId, @PathVariable Long userId) {
        return success(gradingService.getStudentAnswerDetail(examId, userId));
    }

    /**
     * 提交批改分数（批量保存）
     * 请求体包含 examId、userId 和 scores。
     */
    @PreAuthorize("@ss.hasPermi('exam:grading:edit')")
    @Log(title = "考试批改", businessType = BusinessType.UPDATE)
    @PostMapping("/submitScore")
    public AjaxResult submitScore(@RequestBody ExamScoreSubmission submission) {
        gradingService.submitScores(submission, SecurityUtils.getUserId());
        return success();
    }
}
