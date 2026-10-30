package com.cyan.exam.system.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanQuestion;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Rules are stored with the published question, never read from the mutable question bank. */
public final class ObjectiveGradingRules {
    private ObjectiveGradingRules() {}

    public static JSONObject parse(CyanQuestion question) {
        String raw = question.getGradingRules();
        if (raw == null || raw.isBlank()) return new JSONObject();
        try {
            if (raw.length() > 16000) throw new IllegalArgumentException();
            JSONObject rules = JSON.parseObject(raw);
            if (rules == null || rules.getIntValue("version") != 1) throw new IllegalArgumentException();
            if ("MULTI_CHOICE".equals(question.getQuestionType())) {
                if (!Set.of("EXACT", "PARTIAL").contains(rules.getString("mode"))) throw new IllegalArgumentException();
                if ("PARTIAL".equals(rules.getString("mode"))) {
                    Object points = rules.get("partialScore");
                    if (!(points instanceof Integer) || (Integer) points < 1 || (Integer) points > 100
                            || question.getMaxScore() != null && (Integer) points >= question.getMaxScore())
                        throw new IllegalArgumentException();
                }
            } else if ("FILL_BLANK".equals(question.getQuestionType())) {
                if (!Set.of("MANUAL", "TEXT", "NUMERIC").contains(rules.getString("mode")))
                    throw new IllegalArgumentException();
                if ("MANUAL".equals(rules.getString("mode"))) return rules;
                if (!Set.of("ALL", "PER_BLANK").contains(rules.getString("scoring"))
                        || !Set.of("EXACT", "TRIM", "REMOVE").contains(rules.getString("whitespace")))
                    throw new IllegalArgumentException();
                var blanks = rules.getJSONArray("accepted");
                if (blanks == null || blanks.isEmpty() || blanks.size() > 20) throw new IllegalArgumentException();
                for (int i = 0; i < blanks.size(); i++) {
                    var alternatives = blanks.getJSONArray(i);
                    if (alternatives == null || alternatives.isEmpty() || alternatives.size() > 20)
                        throw new IllegalArgumentException();
                    for (Object value : alternatives) {
                        if (!(value instanceof String text) || text.isBlank() || text.length() > 512
                                || text.contains(";")) throw new IllegalArgumentException();
                        if ("NUMERIC".equals(rules.getString("mode"))) number(text);
                    }
                }
                if ("NUMERIC".equals(rules.getString("mode"))) {
                    BigDecimal tolerance = rules.getBigDecimal("tolerance");
                    if (tolerance == null || tolerance.signum() < 0 || tolerance.compareTo(BigDecimal.ONE) > 0)
                        throw new IllegalArgumentException();
                }
                // Scores are integer throughout the existing system; reject lossy per-blank division.
                if (question.getMaxScore() != null && "PER_BLANK".equals(rules.getString("scoring"))
                        && question.getMaxScore() % blanks.size() != 0) throw new IllegalArgumentException();
            } else throw new IllegalArgumentException();
            return rules;
        } catch (RuntimeException ex) {
            throw new ServiceException("评分规则无效：少选分须低于满分；填空需有效答案集，按空计分时满分须整除空数");
        }
    }

    static Integer fill(CyanQuestion question, String answer, JSONObject rules) {
        if (rules.isEmpty() || "MANUAL".equals(rules.getString("mode"))) return null;
        var blanks = rules.getJSONArray("accepted");
        String[] actual = answer.split(";", -1);
        if (actual.length != blanks.size()) return 0;
        int passed = 0;
        for (int i = 0; i < blanks.size(); i++) {
            final String value = actual[i];
            boolean match = blanks.getJSONArray(i).stream().anyMatch(expected -> {
                try {
                    if ("NUMERIC".equals(rules.getString("mode")))
                        return number(value).subtract(number((String) expected)).abs()
                                .compareTo(rules.getBigDecimal("tolerance")) <= 0;
                    return normalize(value, rules).equals(normalize((String) expected, rules));
                } catch (RuntimeException ex) { return false; }
            });
            if (match) passed++;
        }
        return "PER_BLANK".equals(rules.getString("scoring"))
                ? passed * (question.getMaxScore() / blanks.size())
                : passed == blanks.size() ? question.getMaxScore() : 0;
    }

    private static String normalize(String value, JSONObject rules) {
        if (rules.getBooleanValue("normalizeWidth")) value = Normalizer.normalize(value, Normalizer.Form.NFKC);
        if ("TRIM".equals(rules.getString("whitespace"))) value = value.strip();
        if ("REMOVE".equals(rules.getString("whitespace"))) value = value.replaceAll("(?U)\\s+", "");
        return rules.getBooleanValue("ignoreCase") ? value.toLowerCase(Locale.ROOT) : value;
    }

    private static BigDecimal number(String text) {
        if (text.length() > 512) throw new IllegalArgumentException();
        BigDecimal value = new BigDecimal(text.strip());
        if (Math.abs((long) value.scale()) > 512 || value.precision() > 512) throw new IllegalArgumentException();
        return value;
    }
}
