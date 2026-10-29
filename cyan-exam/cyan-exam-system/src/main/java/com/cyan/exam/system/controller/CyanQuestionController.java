package com.cyan.exam.system.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.utils.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.core.controller.BaseController;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.common.enums.BusinessType;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.service.ICyanQuestionService;
import com.cyan.exam.common.utils.poi.ExcelUtil;
import com.cyan.exam.common.core.page.TableDataInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@RestController
@RequestMapping("/question/question")
public class CyanQuestionController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(CyanQuestionController.class);

    @Autowired
    private ICyanQuestionService cyanQuestionService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('question:question:list')")
    public TableDataInfo list(CyanQuestion cyanQuestion) {
        startPage();
        List<CyanQuestion> list = cyanQuestionService.selectCyanQuestionList(cyanQuestion);
        return getDataTable(list);
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermi('question:question:export')")
    @Log(title = "题库", businessType = BusinessType.EXPORT)
    public void export(HttpServletResponse response, CyanQuestion cyanQuestion) {
        List<CyanQuestion> list = cyanQuestionService.selectCyanQuestionList(cyanQuestion);
        // 将 subTitle JSON 转换为可读文本
        for (CyanQuestion q : list) {
            q.setSubTitle(convertJsonToSubTitleText(q.getSubTitle()));
        }
        ExcelUtil<CyanQuestion> util = new ExcelUtil<>(CyanQuestion.class);
        util.exportExcel(response, list, "题库数据");
    }

    @GetMapping("/{questionId}")
    @PreAuthorize("@ss.hasPermi('question:question:query')")
    public AjaxResult getInfo(@PathVariable("questionId") Long questionId) {
        return success(cyanQuestionService.selectCyanQuestionByQuestionId(questionId));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('question:question:add')")
    @Log(title = "题库", businessType = BusinessType.INSERT)
    public AjaxResult add(@RequestBody CyanQuestion cyanQuestion) {
        return toAjax(cyanQuestionService.insertCyanQuestion(cyanQuestion));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermi('question:question:edit')")
    @Log(title = "题库", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@RequestBody CyanQuestion cyanQuestion) {
        return toAjax(cyanQuestionService.updateCyanQuestion(cyanQuestion));
    }

    @DeleteMapping("/{questionIds}")
    @PreAuthorize("@ss.hasPermi('question:question:remove')")
    @Log(title = "题库", businessType = BusinessType.DELETE)
    public AjaxResult remove(@PathVariable Long[] questionIds) {
        return toAjax(cyanQuestionService.deleteCyanQuestionByQuestionIds(questionIds));
    }

    @PostMapping("/import")
    @PreAuthorize("@ss.hasPermi('question:question:import')")
    @Log(title = "题库", businessType = BusinessType.IMPORT)
    public AjaxResult importData(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return AjaxResult.error("上传文件不能为空");
        }
        String result = cyanQuestionService.importQuestions(file);
        return StringUtils.isEmpty(result) ? AjaxResult.success("导入成功") : AjaxResult.error(result);
    }

    @PostMapping("/importJson")
    @PreAuthorize("@ss.hasPermi('question:question:import')")
    @Log(title = "题库", businessType = BusinessType.IMPORT)
    public AjaxResult importJson(@RequestBody Map<String, String> params) {
        String json = params.get("json");
        if (StringUtils.isEmpty(json)) {
            return AjaxResult.error("JSON数据不能为空");
        }
        String result = cyanQuestionService.importQuestionsFromJson(json);
        if (StringUtils.isEmpty(result)) {
            return AjaxResult.success("导入成功");
        } else {
            return AjaxResult.error(result);
        }

    }

    @GetMapping("/importTemplate")
    @PreAuthorize("@ss.hasPermi('question:question:import')")
    public void importTemplate(HttpServletResponse response) {
        cyanQuestionService.exportImportTemplate(response);
    }



    @SuppressWarnings("unchecked")
    private String convertJsonToSubTitleText(String json) {
        if (StringUtils.isEmpty(json)) {
            return "";
        }
        try {
            // 强制转换，确保类型安全
            List<Map<String, Object>> options = (List<Map<String, Object>>) (List) JSON.parseArray(json, Map.class);
            StringBuilder sb = new StringBuilder();
            List<String> answers = new ArrayList<>();

            for (Map<String, Object> opt : options) {
                String label = String.valueOf(opt.get("label"));
                String content = String.valueOf(opt.get("content"));
                sb.append(label).append(": ").append(content).append("; ");
                if ("1".equals(String.valueOf(opt.get("isAnswer")))) {
                    answers.add(label);
                }
            }

            if (sb.length() > 0) {
                sb.setLength(sb.length() - 2);
            }
            sb.append(" | ").append(String.join(",", answers));
            return sb.toString();
        } catch (Exception e) {
            log.error("解析选项JSON失败", e);
            return json;
        }
    }
}