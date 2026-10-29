package com.cyan.exam.system.mapper;

import java.util.List;
import com.cyan.exam.system.domain.CyanStudent;

/**
 * 学生扩展信息Mapper接口
 * 
 * @author cyan
 * @date 2026-06-01
 */
public interface CyanStudentMapper 
{
    /**
     * 查询学生扩展信息
     * 
     * @param studentId 学生扩展信息主键
     * @return 学生扩展信息
     */
    public CyanStudent selectCyanStudentByStudentId(Long studentId);

    /**
     * 查询学生扩展信息列表
     * 
     * @param cyanStudent 学生扩展信息
     * @return 学生扩展信息集合
     */
    public List<CyanStudent> selectCyanStudentList(CyanStudent cyanStudent);

    /**
     * 新增学生扩展信息
     * 
     * @param cyanStudent 学生扩展信息
     * @return 结果
     */
    public int insertCyanStudent(CyanStudent cyanStudent);

    /**
     * 修改学生扩展信息
     * 
     * @param cyanStudent 学生扩展信息
     * @return 结果
     */
    public int updateCyanStudent(CyanStudent cyanStudent);

    /**
     * 删除学生扩展信息
     * 
     * @param studentId 学生扩展信息主键
     * @return 结果
     */
    public int deleteCyanStudentByStudentId(Long studentId);

    /**
     * 批量删除学生扩展信息
     * 
     * @param studentIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteCyanStudentByStudentIds(Long[] studentIds);
}
