package com.cyan.exam.system.service.coding;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cyan.exam.common.exception.ServiceException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class CodingRules {
    private CodingRules() {}
    private static final ObjectMapper STRICT_JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Case(String input, String expected, int weight, boolean publicCase, String comparison, BigDecimal tolerance) {}
    public record Rules(List<Case> cases) {}

    public static Rules parse(String json, int maxScore) {
        try {
            JSONObject config = JSON.parseObject(json);
            var raw = config.getJSONArray("cases");
            if (raw == null || raw.isEmpty() || raw.size() > 30
                    || json.getBytes(StandardCharsets.UTF_8).length > 60000) throw new IllegalArgumentException();
            List<Case> cases = new ArrayList<>();
            int total = 0; int samples = 0; int hidden = 0;
            for (Object entry : raw) {
                JSONObject item = (JSONObject) entry;
                String input = item.getString("input"), expected = item.getString("expected");
                Integer weight = item.getInteger("weight");
                String comparison = item.getString("comparison");
                boolean publicCase = Boolean.TRUE.equals(item.getBoolean("public"));
                BigDecimal tolerance = item.getBigDecimal("tolerance");
                if (input == null || expected == null || input.length() > 16000 || expected.length() > 16000
                        || weight == null || weight < 0 || weight > 100
                        || !Set.of("EXACT", "NUMERIC", "JSON").contains(comparison)) throw new IllegalArgumentException();
                if ("NUMERIC".equals(comparison)) {
                    if (tolerance == null || tolerance.signum() < 0 || tolerance.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException();
                    number(expected);
                }
                if ("JSON".equals(comparison)) jsonValue(expected);
                if (publicCase) { samples++; if (weight != 0) throw new IllegalArgumentException(); }
                else { hidden++; if (weight < 1) throw new IllegalArgumentException(); total += weight; }
                cases.add(new Case(input, expected, weight, publicCase, comparison, tolerance));
            }
            if (samples < 1 || hidden < 1 || total != maxScore) throw new IllegalArgumentException();
            return new Rules(List.copyOf(cases));
        } catch (RuntimeException ex) {
            throw new ServiceException("编程用例无效：需公开样例（0分）和隐藏用例，隐藏用例分值合计须等于题目满分");
        }
    }

    public static boolean matches(Case test, String actual) {
        if (actual == null) actual = "";
        try {
            return switch (test.comparison()) {
                case "EXACT" -> test.expected().replace("\r\n", "\n").equals(actual.replace("\r\n", "\n"));
                case "NUMERIC" -> number(actual).subtract(number(test.expected())).abs()
                        .compareTo(test.tolerance()) <= 0;
                case "JSON" -> Objects.equals(jsonValue(test.expected()), jsonValue(actual));
                default -> false;
            };
        } catch (RuntimeException ex) { return false; }
    }

    private static BigDecimal number(String text) {
        if (text.length() > 512) throw new IllegalArgumentException();
        BigDecimal number = new BigDecimal(text.trim());
        if (Math.abs((long) number.scale()) > 512 || number.precision() > 512) throw new IllegalArgumentException();
        return number;
    }

    private static Object jsonValue(String text) {
        try {
            var value = STRICT_JSON.readTree(text);
            if (value == null || value.isMissingNode()) throw new IllegalArgumentException();
            return value;
        } catch (java.io.IOException ex) { throw new IllegalArgumentException(ex); }
    }

    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
