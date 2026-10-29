package com.cyan.exam.system.service;

import java.util.List;
import com.cyan.exam.system.domain.CyanQuestion;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 题库Service接口
 *
 * @author cyan
 * @date 2026-05-26
 */
public interface ICyanQuestionService {

    /** 查询题库列表 */
    List<CyanQuestion> selectCyanQuestionList(CyanQuestion cyanQuestion);

    /** 根据ID查询 */
    CyanQuestion selectCyanQuestionByQuestionId(Long questionId);

    /** 新增 */
    int insertCyanQuestion(CyanQuestion cyanQuestion);

    /** 修改 */
    int updateCyanQuestion(CyanQuestion cyanQuestion);

    /** 批量删除 */
    int deleteCyanQuestionByQuestionIds(Long[] questionIds);

    /** 导入Excel */
    String importQuestions(MultipartFile file);

    /** 导出导入模板 */
    void exportImportTemplate(jakarta.servlet.http.HttpServletResponse response);

    String importQuestionsFromJson(String json);
}