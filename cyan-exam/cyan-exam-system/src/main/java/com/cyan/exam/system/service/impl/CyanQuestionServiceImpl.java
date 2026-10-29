package com.cyan.exam.system.service.impl;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.utils.StringUtils;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.mapper.CyanQuestionMapper;
import com.cyan.exam.system.service.ICyanQuestionService;
import com.cyan.exam.common.utils.poi.ExcelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class CyanQuestionServiceImpl implements ICyanQuestionService {

    private static final Logger log = LoggerFactory.getLogger(CyanQuestionServiceImpl.class);

    @Autowired
    private CyanQuestionMapper cyanQuestionMapper;

    @Override
    public List<CyanQuestion> selectCyanQuestionList(CyanQuestion cyanQuestion) {
        return cyanQuestionMapper.selectCyanQuestionList(cyanQuestion);
    }

    @Override
    public CyanQuestion selectCyanQuestionByQuestionId(Long questionId) {
        return cyanQuestionMapper.selectCyanQuestionByQuestionId(questionId);
    }

    @Override
    public int insertCyanQuestion(CyanQuestion cyanQuestion) {
        return cyanQuestionMapper.insertCyanQuestion(cyanQuestion);
    }

    @Override
    public int updateCyanQuestion(CyanQuestion cyanQuestion) {
        return cyanQuestionMapper.updateCyanQuestion(cyanQuestion);
    }

    @Override
    public int deleteCyanQuestionByQuestionIds(Long[] questionIds) {
        return cyanQuestionMapper.deleteCyanQuestionByQuestionIds(questionIds);
    }

    /**
     * Excel 文件导入
     */
    @Override
    public String importQuestions(MultipartFile file) {
        ExcelUtil<CyanQuestion> util = new ExcelUtil<>(CyanQuestion.class);
        List<CyanQuestion> list;
        try (InputStream inputStream = file.getInputStream()) {
            list = util.importExcel(inputStream);
        } catch (Exception e) {
            log.error("导入Excel异常", e);
            return "文件解析失败，请检查模板格式";
        }
        return importFromList(list);
    }

    /**
     * JSON 粘贴导入
     */
    @Override
    public String importQuestionsFromJson(String json) {
        List<CyanQuestion> list;
        try {
            list = JSON.parseArray(json, CyanQuestion.class);
        } catch (Exception e) {
            log.error("JSON解析失败", e);
            return "JSON格式错误，请检查";
        }
        if (CollectionUtils.isEmpty(list)) {
            return "未解析到题目数据";
        }
        return importFromList(list);
    }

    /**
     * 通用导入校验与保存逻辑
     */
    private String importFromList(List<CyanQuestion> list) {
        StringBuilder errorMsg = new StringBuilder();
        int successCount = 0;

        for (int i = 0; i < list.size(); i++) {
            CyanQuestion q = list.get(i);
            int rowNum = i + 1;

            // 必填校验
            if (StringUtils.isEmpty(q.getCategory())) {
                errorMsg.append("第").append(rowNum).append("条：题目分类不能为空；");
                continue;
            }
            if (q.getDifficulty() == null) {
                errorMsg.append("第").append(rowNum).append("条：难易程度不能为空；");
                continue;
            }
            if (StringUtils.isEmpty(q.getQuestionType())) {
                errorMsg.append("第").append(rowNum).append("条：题目类型不能为空；");
                continue;
            }
            if (StringUtils.isEmpty(q.getTitle())) {
                errorMsg.append("第").append(rowNum).append("条：题干不能为空；");
                continue;
            }

            String type = q.getQuestionType();

            // 选择题/判断题的选项处理
            if ("SINGLE_CHOICE".equals(type) || "MULTI_CHOICE".equals(type) || "JUDGE".equals(type)) {
                if (StringUtils.isEmpty(q.getSubTitle())) {
                    errorMsg.append("第").append(rowNum).append("条：选择题/判断题必须填写副题干；");
                    continue;
                }

                // 如果是文本格式（包含分号和竖线），转换为 JSON
                if (q.getSubTitle().contains(";") && q.getSubTitle().contains("|")) {
                    String json = parseSubTitleToJson(q.getSubTitle(), type, errorMsg, rowNum);
                    if (json == null) {
                        continue;
                    }
                    q.setSubTitle(json);
                }

                // 答案为空时自动从选项提取
                if (StringUtils.isEmpty(q.getAnswer())) {
                    try {
                        List<Map<String, Object>> opts = (List<Map<String, Object>>) (List) JSON.parseArray(q.getSubTitle(), Map.class);
                        if (opts != null && !opts.isEmpty()) {
                            if ("JUDGE".equals(type) || "SINGLE_CHOICE".equals(type)) {
                                opts.stream()
                                        .filter(o -> "1".equals(String.valueOf(o.get("isAnswer"))))
                                        .findFirst()
                                        .ifPresent(o -> q.setAnswer(String.valueOf(o.get("label"))));
                            } else if ("MULTI_CHOICE".equals(type)) {
                                String answers = opts.stream()
                                        .filter(o -> "1".equals(String.valueOf(o.get("isAnswer"))))
                                        .map(o -> String.valueOf(o.get("label")))
                                        .collect(Collectors.joining(","));
                                q.setAnswer(answers);
                            }
                        }
                    } catch (Exception e) {
                        log.error("选项JSON解析失败", e);
                    }
                }
            }

            if (StringUtils.isEmpty(q.getAnswer())) {
                errorMsg.append("第").append(rowNum).append("条：答案不能为空；");
                continue;
            }

            if (StringUtils.isEmpty(q.getStatus())) {
                q.setStatus("0");
            }

            insertCyanQuestion(q);
            successCount++;
        }

        if (errorMsg.length() > 0) {
            return "成功导入 " + successCount + " 条，失败原因：\n" + errorMsg.toString();
        }
        return null;
    }

    /**
     * 将文本格式的选项转换为 JSON 数组
     * 格式：A:内容1;B:内容2;... | 答案标签
     */
    private String parseSubTitleToJson(String subTitleText, String questionType, StringBuilder errorMsg, int rowNum) {
        if (StringUtils.isEmpty(subTitleText)) {
            return null;
        }
        String[] parts = subTitleText.split("\\|");
        if (parts.length == 0) {
            errorMsg.append("第").append(rowNum).append("条：选项格式错误，缺少竖线分隔符；");
            return null;
        }
        String optionsPart = parts[0].trim();
        String answers = parts.length > 1 ? parts[1].trim() : "";

        String[] optionItems = optionsPart.split(";");
        List<Map<String, Object>> optionList = new ArrayList<>();
        for (String item : optionItems) {
            if (item.trim().isEmpty()) continue;
            String[] kv = item.split(":", 2);
            if (kv.length < 2) {
                errorMsg.append("第").append(rowNum).append("条：选项格式错误，缺少冒号；");
                return null;
            }
            String label = kv[0].trim();
            String content = kv[1].trim();

            int isAnswer = 0;
            if (StringUtils.isNotEmpty(answers)) {
                String[] answerLabels = answers.split(",");
                for (String ans : answerLabels) {
                    if (ans.trim().equalsIgnoreCase(label)) {
                        isAnswer = 1;
                        break;
                    }
                }
            }

            Map<String, Object> opt = new HashMap<>();
            opt.put("label", label);
            opt.put("content", content);
            opt.put("isAnswer", isAnswer);
            optionList.add(opt);
        }

        if (optionList.isEmpty()) {
            errorMsg.append("第").append(rowNum).append("条：未解析到有效选项；");
            return null;
        }
        return JSON.toJSONString(optionList);
    }

    /**
     * 导出导入模板（含示例数据）
     */
    @Override
    public void exportImportTemplate(HttpServletResponse response) {
        ExcelUtil<CyanQuestion> util = new ExcelUtil<>(CyanQuestion.class);
        List<CyanQuestion> exampleList = new ArrayList<>();
        CyanQuestion example = new CyanQuestion();
        example.setCategory("JAVA");
        example.setDifficulty(1);
        example.setQuestionType("SINGLE_CHOICE");
        example.setTitle("Java 属于什么类型的语言？");
        example.setSubTitle("A: 编译型语言; B: 解释型语言; C: 编译与解释结合; D: 以上都对 | C");
        example.setAnswer("C");
        exampleList.add(example);
        util.exportExcel(response, exampleList, "题库导入模板");
    }
}