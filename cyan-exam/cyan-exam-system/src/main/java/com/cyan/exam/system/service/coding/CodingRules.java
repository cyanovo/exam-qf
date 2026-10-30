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
            if (raw == null || raw.isEmpty()) throw new ServiceException("请添加公开样例和隐藏测试点");
            if (raw.size() > 30 || json.getBytes(StandardCharsets.UTF_8).length > 60000)
                throw new ServiceException("测试点不能超过30个，配置不能超过60KB");
            List<Case> cases = new ArrayList<>();
            int total = 0; int samples = 0; int hidden = 0;
            for (Object entry : raw) {
                int index = cases.size() + 1;
                JSONObject item = (JSONObject) entry;
                String input = item.getString("input"), expected = item.getString("expected");
                Integer weight = item.getInteger("weight");
                String comparison = item.getString("comparison");
                boolean publicCase = Boolean.TRUE.equals(item.getBoolean("public"));
                BigDecimal tolerance = item.getBigDecimal("tolerance");
                if (input == null || expected == null || input.length() > 16000 || expected.length() > 16000
                        || weight == null || weight < 0 || weight > 100
                        || !Set.of("EXACT", "NUMERIC", "JSON").contains(comparison))
                    throw new ServiceException("测试点 " + index + " 的输入、输出、分值或比对方式无效");
                if ("NUMERIC".equals(comparison)) {
                    if (tolerance == null || tolerance.signum() < 0 || tolerance.compareTo(BigDecimal.ONE) > 0)
                        throw new ServiceException("测试点 " + index + " 的数值容差无效");
                    try { number(expected); }
                    catch (RuntimeException ex) { throw new ServiceException("测试点 " + index + " 的预期输出必须是数值"); }
                }
                if ("JSON".equals(comparison)) {
                    try { jsonValue(expected); }
                    catch (RuntimeException ex) { throw new ServiceException("测试点 " + index + " 的预期输出必须是有效 JSON"); }
                }
                if (publicCase) {
                    samples++;
                    if (weight != 0) throw new ServiceException("公开样例 " + index + " 必须为0分");
                } else {
                    hidden++;
                    if (weight < 1) throw new ServiceException("隐藏测试点 " + index + " 分值必须大于0");
                    total += weight;
                }
                cases.add(new Case(input, expected, weight, publicCase, comparison, tolerance));
            }
            if (samples < 1) throw new ServiceException("缺少公开样例");
            if (hidden < 1) throw new ServiceException("缺少隐藏测试点");
            if (total != maxScore) throw new ServiceException("隐藏测试点合计 " + total + " 分，题目满分 " + maxScore + " 分，请调整分值");
            return new Rules(List.copyOf(cases));
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ServiceException("编程用例 JSON 格式无效");
        }
    }

    public static Rules validateForPublication(String json, int maxScore) {
        Rules rules = parse(json, maxScore);
        List<Case> hidden = rules.cases().stream().filter(c -> !c.publicCase()).toList();
        if (hidden.size() < 3) throw new ServiceException("至少需要3个隐藏测试点，覆盖不同输入与边界情况");
        if (hidden.stream().map(Case::input).distinct().count() != hidden.size())
            throw new ServiceException("隐藏测试点的输入不能重复");
        if (hidden.stream().map(CodingRules::normalizedExpected).distinct().count() < 3)
            throw new ServiceException("隐藏测试点至少需要3种不同的预期输出，避免固定输出获得满分");
        return rules;
    }

    private static String normalizedExpected(Case c) {
        return switch (c.comparison()) {
            case "NUMERIC" -> number(c.expected()).stripTrailingZeros().toPlainString();
            case "JSON" -> jsonValue(c.expected()).toString();
            default -> c.expected().replace("\r\n", "\n");
        };
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
