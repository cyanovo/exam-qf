package com.cyan.exam.system.service;

import com.cyan.exam.system.mapper.*;
import java.util.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="CYAN_PRACTICAL_TEST_DB",matches="cyan_exam_practical_verify_20261029")
class PracticalCodeDatabaseTest {
    @Test void blankDeletionGuardsAndCleanupRoundTripWithoutLeavingData() throws Exception {
        var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://127.0.0.1:3306/cyan_exam_practical_verify_20261029?serverTimezone=Asia/Shanghai",
                "root",System.getenv("CYAN_PRACTICAL_TEST_PASSWORD"));
        var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        config.addMapper(OnsiteMapper.class); config.addMapper(ExamManagementMapper.class);
        try(var sql=new SqlSessionFactoryBuilder().build(config).openSession(false)) {
            try(var statement=sql.getConnection().createStatement()) {
                var management=sql.getMapper(ExamManagementMapper.class);
                var onsite=sql.getMapper(OnsiteMapper.class);
                long exam=900002L;
                assertFalse(management.hasProtectedRecords(exam), "Fixture exam must be unused");
                statement.executeUpdate("INSERT INTO exam_session(exam_id,user_id,status,started_at) VALUES(900002,9,'AUTO_SUBMITTED',NOW())");
                statement.executeUpdate("INSERT INTO exam_answer_record(exam_id,user_id,question_id,answer,score) VALUES(900002,9,1,'  ',0)");
                statement.executeUpdate("INSERT INTO exam_score_revision(record_id,exam_id,user_id,question_id,new_score,grader_id,reason,changed_at) SELECT record_id,exam_id,user_id,question_id,0,0,'auto',NOW() FROM exam_answer_record WHERE exam_id=900002");
                sql.clearCache();
                assertTrue(management.hasProtectedRecords(exam));
                assertFalse(management.hasNonblankRecords(exam));
                statement.executeUpdate("UPDATE exam_answer_record SET answer='real answer' WHERE exam_id=900002");
                sql.clearCache();
                assertTrue(management.hasNonblankRecords(exam));
                statement.executeUpdate("UPDATE exam_answer_record SET answer='' WHERE exam_id=900002");
                statement.executeUpdate("UPDATE exam_score_revision SET grader_id=8 WHERE exam_id=900002");
                sql.clearCache();
                assertTrue(management.hasNonblankRecords(exam));
                statement.executeUpdate("UPDATE exam_score_revision SET grader_id=0 WHERE exam_id=900002");
                var task=new HashMap<String,Object>(Map.of("examId",exam,"title","Blank fixture",
                        "description","Empty","durationMinutes",10,"judgeId",8L,
                        "rubric","[{\"title\":\"Step\",\"maxScore\":10}]","maxScore",10,"actor",7L));
                onsite.insert(task);
                Long taskId=((Number)task.get("id")).longValue();
                onsite.saveCodeDraft(taskId,9L,"print(1)");
                assertTrue(management.hasNonblankRecords(exam));
                onsite.saveCodeDraft(taskId,9L," \n\t");
                assertFalse(management.hasNonblankRecords(exam));
                assertEquals(1,management.deleteBlankRevisions(exam));
                assertEquals(1,management.deleteBlankAnswers(exam));
                assertEquals(1,management.deleteBlankSessions(exam));
                assertEquals(1,management.deleteBlankCodeDrafts(exam));
                management.clearSnapshots(exam);
                assertFalse(management.hasNonblankRecords(exam));
                Map<String,Object> score=new HashMap<>(Map.of("taskId",taskId,"userId",9L,"judgeId",8L,
                        "points","[0]","note","blank","total",0,"state","SUBMITTED"));
                score.put("submissionId",null);
                onsite.save(score);
                assertTrue(management.hasNonblankRecords(exam), "Explicit manual zero must remain protected");
                assertNull(onsite.scores(taskId).get(0).get("submissionId"));
            } finally { sql.rollback(true); }
        }
    }

    @Test void codeVersionsDraftsAndScoreBindingRoundTripWithoutLeavingData() throws Exception {
        var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://127.0.0.1:3306/cyan_exam_practical_verify_20261029?serverTimezone=Asia/Shanghai",
                "root",System.getenv("CYAN_PRACTICAL_TEST_PASSWORD"));
        var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        config.addMapper(OnsiteMapper.class); config.addMapper(ExamManagementMapper.class);
        try(var sql=new SqlSessionFactoryBuilder().build(config).openSession(false)) {
            try {
                var mapper=sql.getMapper(OnsiteMapper.class);
                var task=new HashMap<String,Object>(Map.of("examId",900001L,"title","Robot fixture",
                        "description","Forward and stop","durationMinutes",10,"judgeId",8L,
                        "rubric","[{\"title\":\"Forward\",\"maxScore\":10}]","maxScore",10,"actor",7L));
                mapper.insert(task);
                Long id=((Number)task.get("id")).longValue();
                mapper.saveCodeDraft(id,9L,"robot.forward()");
                mapper.saveCodeDraft(id,9L,"robot.stop()");
                assertEquals("robot.stop()",mapper.codeDraft(id,9L));
                var submission=new HashMap<String,Object>(Map.of("task",id,"user",9L,"source","robot.stop()",
                        "hash","a".repeat(64),"key",UUID.randomUUID().toString()));
                mapper.submitCode(submission);
                var saved=mapper.codeRequest(id,9L,(String)submission.get("key"));
                assertEquals(((Number)submission.get("id")).longValue(),((Number)saved.get("id")).longValue());
                assertEquals("robot.stop()",saved.get("source"));
                assertEquals(1,mapper.codeHistory(id,9L).size());
                assertTrue(mapper.codeHistory(id,10L).isEmpty());
                mapper.save(Map.of("taskId",id,"userId",9L,"judgeId",8L,"points","[8]","note","observed",
                        "total",8,"state","DRAFT","submissionId",saved.get("id")));
                mapper.save(Map.of("taskId",id,"userId",9L,"judgeId",8L,"points","[9]","note","checked",
                        "total",9,"state","SUBMITTED","submissionId",saved.get("id")));
                var score=mapper.scores(id).get(0);
                assertEquals(saved.get("id"),score.get("submissionId"));
                assertEquals(2,((Number)score.get("revision")).intValue());
                assertTrue(sql.getMapper(ExamManagementMapper.class).hasProtectedRecords(900001L));
                assertEquals("robot.stop()",mapper.codeHistory(id,9L).get(0).get("source"));
            } finally { sql.rollback(); }
        }
    }
}
