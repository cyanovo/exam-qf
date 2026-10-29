package com.cyan.exam.system.service.impl;

import com.cyan.exam.system.domain.ExamSession;
import com.cyan.exam.system.domain.ExamAnswerRecord;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamAutoGradingMapperTest {
    @Test
    void realMappersBindSubmittedGuardsAndCursor() throws Exception {
        Configuration config = new Configuration();
        config.getTypeAliasRegistry().registerAlias("ExamSession", ExamSession.class);
        config.getTypeAliasRegistry().registerAlias("ExamAnswerRecord", ExamAnswerRecord.class);
        for (String name : new String[] {"ExamSessionMapper", "ExamAnswerRecordMapper"}) {
            String resource = "mapper/system/" + name + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        String prefix = "com.cyan.exam.system.mapper.";
        var insert = config.getMappedStatement(prefix + "ExamAnswerRecordMapper.insertMissingSubmittedAnswers")
                .getBoundSql(Map.of("examId", 1L, "userId", 2L));
        assertTrue(insert.getSql().contains("NOT EXISTS"));
        assertTrue(insert.getSql().contains("sess.status IN ('SUBMITTED', 'AUTO_SUBMITTED', 'GRADED')"));
        var pending = config.getMappedStatement(prefix + "ExamSessionMapper.selectPendingAutoGrades")
                .getBoundSql(Map.of("afterId", 100L));
        assertEquals("afterId", pending.getParameterMappings().get(0).getProperty());
        assertTrue(pending.getSql().contains("a.graded_at IS NULL"));
        assertTrue(pending.getSql().contains("LIMIT 100"));
    }
}
