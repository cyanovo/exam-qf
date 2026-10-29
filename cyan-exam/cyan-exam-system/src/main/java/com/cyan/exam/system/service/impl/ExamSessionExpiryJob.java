package com.cyan.exam.system.service.impl;

import com.cyan.exam.system.mapper.ExamSessionMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cyan.exam.system.domain.ExamSession;
import java.util.List;

@Component
public class ExamSessionExpiryJob {
    private final ExamSessionMapper sessionMapper;
    private final ExamAutoGradingService autoGrading;
    private static final Logger log = LoggerFactory.getLogger(ExamSessionExpiryJob.class);

    public ExamSessionExpiryJob(ExamSessionMapper sessionMapper, ExamAutoGradingService autoGrading) {
        this.sessionMapper = sessionMapper;
        this.autoGrading = autoGrading;
    }

    @Scheduled(fixedDelay = 30000)
    public void sealExpiredSessions() {
        sessionMapper.autoFinishExpired();
        long afterId = 0;
        List<ExamSession> pending;
        do {
            pending = sessionMapper.selectPendingAutoGrades(afterId);
            for (ExamSession session : pending) {
                afterId = session.getSessionId();
                try {
                    autoGrading.gradeSubmitted(session.getExamId(), session.getUserId());
                } catch (RuntimeException ex) {
                    log.error("Auto grading failed for session {}", session.getSessionId(), ex);
                }
            }
        } while (pending.size() == 100);
    }
}
