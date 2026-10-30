package com.cyan.exam.system.domain;

import java.util.Date;
import com.cyan.exam.common.annotation.Excel;
import com.cyan.exam.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 考试信息对象 exam_info
 *
 * @author cyan
 * @date 2026-06-02
 */
public class CyanExamInfo extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long examId;
    private Long ownerUserId;
    private String policyJson;
    private Integer durationMinutes = 0;
    private Integer earlyEntryMinutes = 30;
    private String lifecycle = "ACTIVE";
    public String getPolicyJson() { return policyJson; }
    public void setPolicyJson(String value) { policyJson = value; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer value) { durationMinutes = value; }
    public Integer getEarlyEntryMinutes() { return earlyEntryMinutes; }
    public void setEarlyEntryMinutes(Integer value) { earlyEntryMinutes = value; }
    public String getLifecycle() { return lifecycle; }
    public void setLifecycle(String value) { lifecycle = value; }

    @Excel(name = "考试名称")
    private String examName;

    @Excel(name = "开始时间", dateFormat = "yyyy-MM-dd HH:mm")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date startTime;

    @Excel(name = "结束时间", dateFormat = "yyyy-MM-dd HH:mm")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date endTime;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedAt;

    // 扩展字段（不映射数据库，用于接收前端穿梭框参数）
    private Long[] studentIds;
    private Long[] questionIds;

    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getExamName() { return examName; }
    public void setExamName(String examName) { this.examName = examName; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Date publishedAt) { this.publishedAt = publishedAt; }
    public Long[] getStudentIds() { return studentIds; }
    public void setStudentIds(Long[] studentIds) { this.studentIds = studentIds; }
    public Long[] getQuestionIds() { return questionIds; }
    public void setQuestionIds(Long[] questionIds) { this.questionIds = questionIds; }

    @Override
    public String toString() {
        return "ExamInfo{" +
                "examId=" + examId +
                ", examName='" + examName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", status='" + status + '\'' +
                '}';
    }
}
