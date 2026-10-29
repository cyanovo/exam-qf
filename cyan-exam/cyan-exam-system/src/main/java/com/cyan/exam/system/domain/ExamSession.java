package com.cyan.exam.system.domain;

import java.util.Date;

public class ExamSession {
    private Long sessionId;
    private Long examId;
    private Long userId;
    private Date startedAt;
    private Date submittedAt;
    private String status;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }
    public Date getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
