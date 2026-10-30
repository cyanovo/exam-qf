package com.cyan.exam.system.service;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.domain.ExamSession;
import java.util.*;

public final class ExamPolicy {
    private ExamPolicy() {}
    public record Entry(String kind, Long id, String section, String description, String hint) {}
    public record Policy(String instructions, String tieRule, Long tieTaskId, String releasePolicy,
                         int retentionDays, List<Entry> entries) {}
    public static Policy read(CyanExamInfo exam) {
        if (exam == null || exam.getPolicyJson() == null || exam.getPolicyJson().isBlank())
            return new Policy("", "SHARED", null, "DETAIL", 365, List.of());
        return JSON.parseObject(exam.getPolicyJson(), Policy.class);
    }
    public static void validate(Policy p) {
        if (p == null || p.instructions() == null || p.instructions().length() > 10000
                || p.tieRule() == null || p.releasePolicy() == null
                || !Set.of("SHARED", "TASK_SCORE", "SUBMITTED_AT").contains(p.tieRule())
                || !Set.of("DETAIL", "TOTAL", "TOTAL_RANK").contains(p.releasePolicy())
                || p.retentionDays() < 30 || p.retentionDays() > 3650 || p.entries() == null
                || p.entries().size() > 300 || ("TASK_SCORE".equals(p.tieRule()) && p.tieTaskId() == null))
            throw new ServiceException("考试策略参数无效");
        Set<String> seen = new HashSet<>();
        for (Entry e : p.entries()) {
            if (e == null || e.kind() == null || !Set.of("QUESTION", "ONSITE").contains(e.kind())
                    || e.id() == null || !seen.add(e.kind() + ":" + e.id())
                    || e.section() == null || e.section().length() > 100
                    || e.description() == null || e.description().length() > 3000
                    || e.hint() == null || e.hint().length() > 1000)
                throw new ServiceException("编排条目无效或重复");
        }
    }
    public static Date deadline(CyanExamInfo exam, ExamSession session) {
        if (exam.getDurationMinutes() == null || exam.getDurationMinutes() <= 0 || session == null)
            return exam.getEndTime();
        return new Date(Math.min(exam.getEndTime().getTime(),
                session.getStartedAt().getTime() + exam.getDurationMinutes() * 60000L));
    }
    public static String state(CyanExamInfo exam) {
        if (!"ACTIVE".equals(exam.getLifecycle())) return exam.getLifecycle();
        if (exam.getPublishedAt() == null) return "DRAFT";
        long now = System.currentTimeMillis();
        if (now >= exam.getEndTime().getTime()) return "CLOSED";
        if (now >= exam.getStartTime().getTime()) return "RUNNING";
        if (now >= exam.getStartTime().getTime() - exam.getEarlyEntryMinutes() * 60000L) return "READY";
        return "PUBLISHED";
    }
}
