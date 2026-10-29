package com.cyan.exam.system.domain.dto;

public class CyanStudentImportDTO {
    private String prefix;      // 账号前缀
    private Integer startNo;    // 起始编号
    private Integer count;      // 创建数量
    private String password;    // 默认密码
    private String className;   // 班级
    private String grade;       // 年级

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public Integer getStartNo() { return startNo; }
    public void setStartNo(Integer startNo) { this.startNo = startNo; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
}