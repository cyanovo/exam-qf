package com.cyan.exam.system.service.impl;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanQuestion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectiveGradingRulesTest {
    CyanQuestion question(String type, String rules) {
        CyanQuestion q = new CyanQuestion();
        q.setQuestionType(type); q.setMaxScore(10); q.setAnswer("A,B");
        q.setSubTitle("[{\"label\":\"A\"},{\"label\":\"B\"},{\"label\":\"C\"}]");
        q.setGradingRules(rules);
        return q;
    }
    @Test void multiChoicePartialOnlyForNonemptyProperSubset() {
        var q = question("MULTI_CHOICE", "{\"version\":1,\"mode\":\"PARTIAL\",\"partialScore\":3}");
        assertEquals(10, ExamAutoGradingService.calculateScore(q, "B,A"));
        assertEquals(3, ExamAutoGradingService.calculateScore(q, "A"));
        for (String value : new String[] {"", "A,C", "A,A", "D", "A,B,C"})
            assertEquals(0, ExamAutoGradingService.calculateScore(q, value));
    }
    @Test void fillNormalizationAlternativesAndPerBlankScoring() {
        var q = question("FILL_BLANK", """
                {"version":1,"mode":"TEXT","scoring":"PER_BLANK","whitespace":"REMOVE",
                 "ignoreCase":true,"normalizeWidth":true,"accepted":[["Python","py"],["hello world"]]}
                """);
        assertEquals(10, ExamAutoGradingService.calculateScore(q, " ＰＹ ; hello\tworld "));
        assertEquals(5, ExamAutoGradingService.calculateScore(q, "Python;wrong"));
        assertEquals(0, ExamAutoGradingService.calculateScore(q, "Python;hello world;extra"));
    }
    @Test void numericToleranceIsBoundedAndMalformedInputIsZero() {
        var q = question("FILL_BLANK", """
                {"version":1,"mode":"NUMERIC","scoring":"ALL","whitespace":"TRIM",
                 "tolerance":0.01,"accepted":[["3.14"]]}
                """);
        assertEquals(10, ExamAutoGradingService.calculateScore(q, "3.15"));
        assertEquals(0, ExamAutoGradingService.calculateScore(q, "3.151"));
        assertEquals(0, ExamAutoGradingService.calculateScore(q, "1e999999"));
        assertEquals(0, ExamAutoGradingService.calculateScore(q, "NaN"));
    }
    @Test void rejectsInvalidVersionsOverflowAndLossyScoreSplits() {
        for (String rules : new String[] {"{}", "{\"version\":2,\"mode\":\"EXACT\"}",
                "{\"version\":1,\"mode\":\"PARTIAL\",\"partialScore\":10}",
                "{\"version\":1,\"mode\":\"PARTIAL\",\"partialScore\":1.5}"})
            assertThrows(ServiceException.class, () -> ObjectiveGradingRules.parse(question("MULTI_CHOICE", rules)));
        var q = question("FILL_BLANK", """
                {"version":1,"mode":"TEXT","scoring":"PER_BLANK","whitespace":"TRIM","accepted":[["a"],["b"],["c"]]}
                """);
        assertThrows(ServiceException.class, () -> ObjectiveGradingRules.parse(q));
    }
    @Test void existingFillQuestionsStayManualUntilExplicitlyConfigured() {
        assertNull(ExamAutoGradingService.calculateScore(question("FILL_BLANK", null), "answer"));
        assertNull(ExamAutoGradingService.calculateScore(question("FILL_BLANK",
                "{\"version\":1,\"mode\":\"MANUAL\"}"), "answer"));
    }
}
