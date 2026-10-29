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
