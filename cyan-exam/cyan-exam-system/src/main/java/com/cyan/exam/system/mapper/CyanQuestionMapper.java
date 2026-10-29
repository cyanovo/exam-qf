package com.cyan.exam.system.mapper;

import java.util.List;
import com.cyan.exam.system.domain.CyanQuestion;

/**
 * 题库Mapper接口
 *
 * @author cyan
 */
public interface CyanQuestionMapper
{
    /**
     * 查询题库列表
     */
    public List<CyanQuestion> selectCyanQuestionList(CyanQuestion cyanQuestion);

    /**
     * 根据题目ID查询题目
     */
    public CyanQuestion selectCyanQuestionByQuestionId(Long questionId);

    /**
     * 新增题目
     */
    public int insertCyanQuestion(CyanQuestion cyanQuestion);

    /**
     * 修改题目
     */
    public int updateCyanQuestion(CyanQuestion cyanQuestion);

    /**
     * 删除题目
     */
    public int deleteCyanQuestionByQuestionId(Long questionId);

    /**
     * 批量删除题目
     */
    public int deleteCyanQuestionByQuestionIds(Long[] questionIds);

    /**
     * 根据题目ID数组批量查询题目（新增）
     */
    public List<CyanQuestion> selectCyanQuestionByQuestionIds(Long[] questionIds);
}