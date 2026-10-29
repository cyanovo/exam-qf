package com.cyan.exam.system.domain;

import com.cyan.exam.common.core.domain.BaseEntity;
import java.util.Date;

public class ExamAnswerRecord extends BaseEntity {
    private Long recordId;
    private Long examId;
    private Long userId;
    private Long questionId;
    private String answer;
    private Integer score;
    private Date gradedAt;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Date getGradedAt() { return gradedAt; }
    public void setGradedAt(Date gradedAt) { this.gradedAt = gradedAt; }
}
