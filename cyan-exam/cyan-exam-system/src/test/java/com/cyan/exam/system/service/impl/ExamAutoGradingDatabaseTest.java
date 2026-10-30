package com.cyan.exam.system.service.impl;

import com.cyan.exam.system.domain.ExamSession;
import com.cyan.exam.system.domain.ExamAnswerRecord;
import com.cyan.exam.system.mapper.*;
import java.io.InputStream;
import java.sql.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CYAN_GRADING_TEST_DB", matches = "cyan_exam_autograding_verify")
class ExamAutoGradingDatabaseTest {
    @Test
    void restoredMysqlSchemaSupportsGradingRetryAndPreservesManualScores() throws Exception {
        String url = "jdbc:mysql://127.0.0.1:3306/cyan_exam_autograding_verify?useUnicode=true"
                + "&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url,
                System.getenv("CYAN_GRADING_TEST_USER"), System.getenv("CYAN_GRADING_TEST_PASSWORD"));
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.getTypeAliasRegistry().registerAlias("ExamSession", ExamSession.class);
        config.getTypeAliasRegistry().registerAlias("ExamAnswerRecord", ExamAnswerRecord.class);
        config.getTypeAliasRegistry().registerAlias("CyanExamInfo", com.cyan.exam.system.domain.CyanExamInfo.class);
        config.addMapper(ExamResultMapper.class);
        config.addMapper(CodeSubmissionMapper.class);
        for (String name : new String[] {"CyanExamInfoMapper", "ExamSessionMapper", "ExamAnswerRecordMapper", "ExamQuestionMapper"}) {
            String resource = "mapper/system/" + name + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        try (SqlSession sql = new SqlSessionFactoryBuilder().build(config).openSession(false)) {
            try {
                try (Statement statement = sql.getConnection().createStatement()) {
                    statement.executeUpdate("INSERT INTO exam_info (exam_id,exam_name,start_time,end_time,published_at)"
                            + " VALUES (900001,'auto-grade-test',NOW()-INTERVAL 2 HOUR,NOW()-INTERVAL 1 HOUR,NOW())");
                    statement.executeUpdate("INSERT INTO exam_session (exam_id,user_id,started_at,status)"
                            + " VALUES (900001,900002,NOW()-INTERVAL 2 HOUR,'IN_PROGRESS')");
                    statement.executeUpdate("INSERT INTO exam_question_snapshot"
                            + " (exam_id,question_id,question_type,title,sub_title,answer,max_score) VALUES"
                            + " (900001,1,'MULTI_CHOICE','multi','[{\"label\":\"A\"},{\"label\":\"B\"}]','A,B',10),"
                            + " (900001,2,'SINGLE_CHOICE','blank','[{\"label\":\"A\"}]','A',10),"
                            + " (900001,3,'SHORT_ANSWER','manual',NULL,'reference',10),"
                            + " (900001,4,'SHORT_ANSWER','whitespace',NULL,'reference',10)");
                    statement.executeUpdate("INSERT INTO exam_answer_record"
                            + " (exam_id,user_id,question_id,answer,score,graded_at) VALUES"
                            + " (900001,900002,1,'B,A',NULL,NULL),"
                            + " (900001,900002,3,'teacher reviewed',0,NOW()),"
                            + " (900001,900002,4,CHAR(9,10,13),NULL,NULL)");
                }
                var sessions = sql.getMapper(ExamSessionMapper.class);
                var answers = sql.getMapper(ExamAnswerRecordMapper.class);
                var results = new com.cyan.exam.system.service.ExamResultService(
                        sql.getMapper(CyanExamInfoMapper.class), sql.getMapper(ExamResultMapper.class),
                        null, null, null, null, null, null);
                var scorer = new ExamAutoGradingService(sessions, sql.getMapper(ExamQuestionMapper.class), answers, results,
                        sql.getMapper(CodeSubmissionMapper.class));
                scorer.gradeSubmitted(900001L, 900002L);
                assertEquals(3, answers.selectByExamAndUser(900001L, 900002L).size());
                sessions.autoFinishExpired();
                assertTrue(sessions.selectPendingAutoGrades(0L).stream().anyMatch(s -> s.getExamId() == 900001L));
                scorer.gradeSubmitted(900001L, 900002L);
                scorer.gradeSubmitted(900001L, 900002L);
                var records = answers.selectByExamAndUser(900001L, 900002L);
                assertEquals(4, records.size());
                assertEquals(10, records.stream().mapToInt(ExamAnswerRecord::getScore).sum());
                assertTrue(records.stream().allMatch(r -> r.getGradedAt() != null));
                assertEquals(3, answers.selectScoreRevisions(900001L, 900002L).size());
                try (Statement statement = sql.getConnection().createStatement()) {
                    statement.executeUpdate("UPDATE exam_answer_record SET graded_at=NULL,score=NULL"
                            + " WHERE exam_id=900001 AND question_id=4");
                }
                sql.clearCache();
                assertTrue(sessions.selectPendingAutoGrades(0L).stream().anyMatch(s -> s.getExamId() == 900001L));
                scorer.gradeSubmitted(900001L, 900002L);
                assertFalse(sessions.selectPendingAutoGrades(0L).stream().anyMatch(s -> s.getExamId() == 900001L));
                verifyResultWorkflow(sql);
                verifyCodeQueue(sql);
            } finally {
                sql.rollback();
            }
        }
    }

    private void verifyResultWorkflow(SqlSession sql) {
        var mapper = sql.getMapper(ExamResultMapper.class);
        String payload = "{\"rows\":[{\"userId\":900002,\"total\":10}]}";
        assertEquals(1, mapper.insert(java.util.Map.of("examId", 900001L, "version", 1,
                "payload", payload, "actorId", 1L, "reason", "integration test")));
        var version = mapper.latest(900001L);
        Long id = ((Number) version.get("id")).longValue();
        assertNull(mapper.published(900001L));
        assertEquals(0, mapper.transition(id, "APPROVED", "RELEASED", 2L));
        assertEquals(1, mapper.transition(id, "REVIEW", "APPROVED", 2L));
        assertEquals(1, mapper.transition(id, "APPROVED", "RELEASED", 2L));
        assertEquals(1, mapper.event(id, 2L, "APPROVED", "RELEASED", "checked"));
        var released = mapper.published(900001L);
        assertNotNull(released.get("releasedAt"));
        assertEquals(1, mapper.events(id).size());
        assertEquals(1, mapper.transition(id, "RELEASED", "CORRECTION", 1L));
        assertEquals(1, mapper.transition(id, "CORRECTION", "REOPENED", 2L));
        assertEquals(released.get("payload"), mapper.published(900001L).get("payload"));
        assertEquals(1, mapper.insert(java.util.Map.of("examId", 900001L, "version", 2,
                "payload", "{\"rows\":[]}", "actorId", 1L, "reason", "correction")));
        assertEquals(2, ((Number) mapper.latest(900001L).get("version")).intValue());
        assertEquals(1, ((Number) mapper.published(900001L).get("version")).intValue());
    }

    private void verifyCodeQueue(SqlSession sql) throws SQLException {
        try (Statement statement = sql.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE exam_info SET end_time=NOW()+INTERVAL 1 HOUR,status='0' WHERE exam_id=900001");
            statement.executeUpdate("UPDATE exam_session SET status='IN_PROGRESS' WHERE exam_id=900001");
        }
        var mapper = sql.getMapper(CodeSubmissionMapper.class);
        var row = new java.util.HashMap<String, Object>(java.util.Map.of(
                "examId", 900001L, "userId", 900002L, "questionId", 1L, "mode", "FORMAL",
                "key", "integration-key-0001", "source", "print(1)", "sourceHash", "a".repeat(64),
                "snapshotHash", "b".repeat(64)));
        assertEquals(1, mapper.insert(row));
        Long id = ((Number) row.get("id")).longValue();
        assertEquals(id, ((Number) mapper.byKey(900001L, 900002L, "integration-key-0001").get("id")).longValue());
        assertEquals(1, mapper.claim(id, "lease-a"));
        assertEquals(0, mapper.claim(id, "lease-b"));
        assertEquals(0, mapper.complete(id, "lease-b", 10, "[]"));
        assertEquals(1, mapper.progress(id, "lease-a", "{\"index\":0,\"score\":0}"));
        assertEquals(0, mapper.complete(id, "lease-a", 10, "[]"));
        for (int attempt = 0; attempt < 3; attempt++) {
            try (Statement statement = sql.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE exam_code_submission SET lease_until=NULL WHERE id=" + id);
            }
            assertEquals(1, mapper.claim(id, "retry-" + attempt));
            assertEquals(1, mapper.fail(id, "retry-" + attempt));
        }
        assertEquals("FAILED", mapper.get(id).get("state"));
        assertNull(mapper.get(id).get("score"));
        assertEquals(1, mapper.retry(id));
        assertEquals(1, mapper.claim(id, "final"));
        assertEquals(1, mapper.complete(id, "final", 10, "[]"));
        assertEquals("SUCCEEDED", mapper.latestFormal(900001L, 900002L, 1L).get("state"));
        assertEquals(0, mapper.retry(id));
        assertEquals(1, mapper.archive(id, 1, 42L, "Verified regrade"));
        assertEquals(1, mapper.regrade(id, 1));
        assertEquals(0, mapper.regrade(id, 1));
        assertEquals(0, mapper.complete(id, "final", 99, "[]"));
        assertEquals(2, ((Number) mapper.get(id).get("runVersion")).intValue());
        assertNull(mapper.get(id).get("score"));
        assertEquals(10, ((Number) mapper.evaluations(id).get(0).get("score")).intValue());
        assertEquals("Verified regrade", mapper.evaluations(id).get(0).get("reason"));
        assertEquals(1, mapper.claim(id, "version-2"));
        assertEquals(1, mapper.complete(id, "version-2", 7, "[]"));
        assertEquals(10, ((Number) mapper.evaluations(id).get(0).get("score")).intValue());
        assertEquals(1, sql.getMapper(ExamAnswerRecordMapper.class).clearCodeGrade(900001L, 900002L, 1L));
        assertNull(sql.getMapper(ExamAnswerRecordMapper.class).selectByExamAndUser(900001L, 900002L)
                .stream().filter(answer -> answer.getQuestionId() == 1L).findFirst().orElseThrow().getGradedAt());
        try (Statement statement = sql.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE exam_info SET end_time=NOW()-INTERVAL 1 SECOND WHERE exam_id=900001");
        }
        row.put("key", "integration-key-0002");
        assertEquals(0, mapper.insert(row));
    }
}
