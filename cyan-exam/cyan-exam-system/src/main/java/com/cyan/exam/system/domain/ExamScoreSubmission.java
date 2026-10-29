package com.cyan.exam.system.domain;

import java.util.List;

public class ExamScoreSubmission {
    private Long examId;
    private Long userId;
    private String reason;
    private List<Item> scores;

    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<Item> getScores() { return scores; }
    public void setScores(List<Item> scores) { this.scores = scores; }

    public static class Item {
        private Long recordId;
        private Integer score;
        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
    }
}
