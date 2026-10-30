package com.cyan.exam.system.controller;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.core.controller.BaseController;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.service.ICyanExamInfoService;
import com.cyan.exam.common.utils.poi.ExcelUtil;
import com.cyan.exam.common.core.page.TableDataInfo;

@RestController
@RequestMapping("/exam/exam")
public class CyanExamInfoController extends BaseController {

    @Autowired
    private ICyanExamInfoService cyanExamInfoService;

    @PreAuthorize("@ss.hasPermi('exam:exam:list')")
    @GetMapping("/list")
    public TableDataInfo list(CyanExamInfo examInfo) {
        startPage();
        List<CyanExamInfo> list = cyanExamInfoService.selectCyanExamInfoList(examInfo);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('exam:exam:export')")
    @Log(title = "考试信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CyanExamInfo examInfo) {
        List<CyanExamInfo> list = cyanExamInfoService.selectCyanExamInfoList(examInfo);
        ExcelUtil<CyanExamInfo> util = new ExcelUtil<>(CyanExamInfo.class);
        util.exportExcel(response, list, "考试信息数据");
    }

    @PreAuthorize("@ss.hasPermi('exam:exam:query')")
    @GetMapping("/{examId}")
    public AjaxResult getInfo(@PathVariable Long examId) {
        return success(cyanExamInfoService.selectCyanExamInfoByExamId(examId));
    }

    @PreAuthorize("@ss.hasPermi('exam:exam:add')")
    @Log(title = "考试信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CyanExamInfo examInfo) {
        return toAjax(cyanExamInfoService.insertCyanExamInfo(examInfo));
    }

    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    @Log(title = "考试信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CyanExamInfo examInfo) {
        return toAjax(cyanExamInfoService.updateCyanExamInfo(examInfo));
    }
    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    @Log(title = "考试发布", businessType = BusinessType.UPDATE)
    @PostMapping("/{examId}/publish")
    public AjaxResult publish(@PathVariable Long examId) {
        cyanExamInfoService.publish(examId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('exam:exam:remove')")
    @Log(title = "考试信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{examIds}")
    public AjaxResult remove(@PathVariable Long[] examIds) {
        return toAjax(cyanExamInfoService.deleteCyanExamInfoByExamIds(examIds));
    }

    // 获取考试已分配的学生ID列表
    @PreAuthorize("@ss.hasAnyPermi('exam:exam:query,exam:exam:edit')")
    @GetMapping("/{examId}/studentIds")
    public AjaxResult getStudentIds(@PathVariable Long examId) {
        List<Long> ids = cyanExamInfoService.selectStudentIdsByExamId(examId);
        return success(ids);
    }

    // 保存考试的学生分配
    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    @Log(title = "考试学生分配", businessType = BusinessType.UPDATE)
    @PutMapping("/{examId}/students")
    public AjaxResult saveStudents(@PathVariable Long examId, @RequestBody Long[] studentIds) {
        cyanExamInfoService.saveExamStudents(examId,
                studentIds != null ? java.util.Arrays.asList(studentIds) : new java.util.ArrayList<>());
        return success();
    }

    // 获取考试已选择的试题ID列表
    @PreAuthorize("@ss.hasAnyPermi('exam:exam:query,exam:exam:edit')")
    @GetMapping("/{examId}/questionIds")
    public AjaxResult getQuestionIds(@PathVariable Long examId) {
        List<Long> ids = cyanExamInfoService.selectQuestionIdsByExamId(examId);
        return success(ids);
    }
    @PreAuthorize("@ss.hasAnyPermi('exam:exam:query,exam:exam:edit')")
    @GetMapping("/{examId}/questionPoints")
    public AjaxResult getQuestionPoints(@PathVariable Long examId) {
        return success(cyanExamInfoService.selectAssignedQuestions(examId));
    }

    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    @Log(title = "考试题目分值", businessType = BusinessType.UPDATE)
    @PutMapping("/{examId}/questionPoints")
    public AjaxResult saveQuestionPoints(@PathVariable Long examId, @RequestBody Map<Long, Integer> points) {
        cyanExamInfoService.saveQuestionPoints(examId, points);
        return success();
    }

    // 保存考试的试题选择
    @PreAuthorize("@ss.hasPermi('exam:exam:edit')")
    @Log(title = "考试试题选择", businessType = BusinessType.UPDATE)
    @PutMapping("/{examId}/questions")
    public AjaxResult saveQuestions(@PathVariable Long examId, @RequestBody Long[] questionIds) {
        cyanExamInfoService.saveExamQuestions(examId,
                questionIds != null ? java.util.Arrays.asList(questionIds) : new java.util.ArrayList<>());
        return success();
    }
}
