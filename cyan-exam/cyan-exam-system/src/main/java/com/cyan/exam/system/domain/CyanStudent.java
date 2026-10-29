package com.cyan.exam.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.cyan.exam.common.annotation.Excel;
import com.cyan.exam.common.core.domain.BaseEntity;

/**
 * 学生扩展信息对象 cyan_student
 * 
 * @author cyan
 * @date 2026-06-01
 */
public class CyanStudent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 学生ID */
    private Long studentId;

    /** 关联sys_user.user_id */
    @Excel(name = "关联sys_user.user_id")
    private Long userId;

    /** 学号 */
    @Excel(name = "学号")
    private String studentNo;

    /** 班级 */
    @Excel(name = "班级")
    private String className;

    /** 年级 */
    @Excel(name = "年级")
    private String grade;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    private String userName;   // 账号
    private String nickName;   // 姓名
    private String status;     // 账号状态（来自 sys_user）

    public void setStudentId(Long studentId) 
    {
        this.studentId = studentId;
    }

    public Long getStudentId() 
    {
        return studentId;
    }

    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }

    public void setStudentNo(String studentNo) 
    {
        this.studentNo = studentNo;
    }

    public String getStudentNo() 
    {
        return studentNo;
    }

    public void setClassName(String className) 
    {
        this.className = className;
    }

    public String getClassName() 
    {
        return className;
    }

    public void setGrade(String grade) 
    {
        this.grade = grade;
    }

    public String getGrade() 
    {
        return grade;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("studentId", getStudentId())
            .append("userId", getUserId())
            .append("studentNo", getStudentNo())
            .append("className", getClassName())
            .append("grade", getGrade())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
