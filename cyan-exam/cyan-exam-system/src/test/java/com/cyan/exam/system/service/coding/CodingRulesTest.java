package com.cyan.exam.system.service.coding;

import com.cyan.exam.common.exception.ServiceException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodingRulesTest {
    static final String CONFIG = "{\"cases\":[{\"input\":\"1\\n\",\"expected\":\"1\\n\",\"public\":true,\"weight\":0,\"comparison\":\"EXACT\"},"
            + "{\"input\":\"2\\n\",\"expected\":\"2\\n\",\"public\":false,\"weight\":10,\"comparison\":\"EXACT\"}]}";
    @Test void weightsMustMatchAndSamplesCannotAwardPoints() {
        assertEquals(2, CodingRules.parse(CONFIG, 10).cases().size());
        assertThrows(ServiceException.class, () -> CodingRules.parse(CONFIG, 20));
        assertThrows(ServiceException.class, () -> CodingRules.parse(CONFIG.replace("\"weight\":0", "\"weight\":1"), 10));
    }
    @Test void publicationRejectsConstantOutputAndDuplicateInputsButPreservesExistingSnapshots() {
        String diverse = "{\"cases\":["
                + "{\"input\":\"1\",\"expected\":\"2\",\"public\":true,\"weight\":0,\"comparison\":\"EXACT\"},"
                + "{\"input\":\"2\",\"expected\":\"4\",\"public\":false,\"weight\":3,\"comparison\":\"EXACT\"},"
                + "{\"input\":\"3\",\"expected\":\"6\",\"public\":false,\"weight\":3,\"comparison\":\"EXACT\"},"
                + "{\"input\":\"4\",\"expected\":\"8\",\"public\":false,\"weight\":4,\"comparison\":\"EXACT\"}]}";
        assertEquals(4, CodingRules.validateForPublication(diverse, 10).cases().size());
        int constantScore = CodingRules.validateForPublication(diverse, 10).cases().stream()
                .filter(c -> !c.publicCase() && CodingRules.matches(c, "4")).mapToInt(CodingRules.Case::weight).sum();
        assertTrue(constantScore < 10);
        assertTrue(assertThrows(ServiceException.class,
                () -> CodingRules.validateForPublication(diverse.replace("\"expected\":\"6\"", "\"expected\":\"4\"")
                        .replace("\"expected\":\"8\"", "\"expected\":\"4\""), 10)).getMessage().contains("3种"));
        assertTrue(assertThrows(ServiceException.class,
                () -> CodingRules.validateForPublication(diverse.replace("\"input\":\"3\"", "\"input\":\"2\""), 10))
                .getMessage().contains("输入不能重复"));
        assertEquals(2, CodingRules.parse(CONFIG, 10).cases().size());
        assertThrows(ServiceException.class, () -> CodingRules.validateForPublication(CONFIG, 10));
    }
    @Test void publicationErrorsIdentifyMissingCasesAndScoreMismatch() {
        assertTrue(assertThrows(ServiceException.class, () -> CodingRules.parse(
                CONFIG.replace("\"public\":true", "\"public\":false").replace("\"weight\":0", "\"weight\":1"), 11))
                .getMessage().contains("缺少公开样例"));
        assertTrue(assertThrows(ServiceException.class, () -> CodingRules.parse(
                CONFIG.replace("\"public\":false", "\"public\":true").replace("\"weight\":10", "\"weight\":0"), 10))
                .getMessage().contains("缺少隐藏测试点"));
        assertTrue(assertThrows(ServiceException.class, () -> CodingRules.parse(CONFIG, 20))
                .getMessage().contains("10 分"));
    }
    @Test void exactOnlyNormalizesWindowsNewlines() {
        var c = new CodingRules.Case("", "hi\n", 10, false, "EXACT", null);
        assertTrue(CodingRules.matches(c, "hi\r\n"));
        assertFalse(CodingRules.matches(c, "hi"));
        assertFalse(CodingRules.matches(c, "hi \n"));
    }
    @Test void numericToleranceRejectsMalformedAndNonFiniteNumbers() {
        var c = new CodingRules.Case("", "1.0", 10, false, "NUMERIC", new BigDecimal("0.01"));
        assertTrue(CodingRules.matches(c, "1.01"));
        assertFalse(CodingRules.matches(c, "1.0101"));
        assertFalse(CodingRules.matches(c, "NaN"));
        assertFalse(CodingRules.matches(c, "Infinity"));
    }
    @Test void jsonComparisonIgnoresObjectKeyOrderButNotArrays() {
        var c = new CodingRules.Case("", "{\"a\":[1,2],\"b\":3}", 10, false, "JSON", null);
        assertTrue(CodingRules.matches(c, "{\"b\":3,\"a\":[1,2]}"));
        assertFalse(CodingRules.matches(c, "{\"b\":3,\"a\":[2,1]}"));
        assertFalse(CodingRules.matches(c, "bad json"));
    }
    @Test void hashesAreStableAndSensitiveToWhitespace() {
        assertEquals(64, CodingRules.hash("print(1)").length());
        assertNotEquals(CodingRules.hash("print(1)"), CodingRules.hash("print(1)\n"));
    }
}
