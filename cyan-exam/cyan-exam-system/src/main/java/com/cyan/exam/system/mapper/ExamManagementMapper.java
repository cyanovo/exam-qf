package com.cyan.exam.system.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

public interface ExamManagementMapper {
    @Select("SELECT storage_key FROM exam_file_asset WHERE exam_id=#{id}")
    List<String> fileKeys(Long id);
    @Delete("DELETE h FROM exam_code_evaluation_history h JOIN exam_code_submission s ON s.id=h.submission_id WHERE s.exam_id=#{id}")
    int deleteCodeHistory(Long id);
    @Delete("DELETE FROM exam_code_submission WHERE exam_id=#{id}")
    int deleteCodeSubmissions(Long id);
    @Delete("DELETE e FROM exam_result_event e JOIN exam_result_version v ON v.id=e.version_id WHERE v.exam_id=#{id}")
    int deleteResultEvents(Long id);
    @Delete("DELETE FROM exam_result_version WHERE exam_id=#{id}")
    int deleteResultVersions(Long id);
    @Delete("DELETE e FROM exam_incident_event e JOIN exam_incident i ON i.id=e.incident_id WHERE i.exam_id=#{id}")
    int deleteIncidentEvents(Long id);
    @Delete("DELETE FROM exam_file_asset WHERE exam_id=#{id}")
    int deleteFiles(Long id);
    @Delete("DELETE FROM exam_incident WHERE exam_id=#{id}")
    int deleteIncidents(Long id);
    @Delete("DELETE s FROM exam_onsite_score s JOIN exam_onsite_task t ON t.id=s.task_id WHERE t.exam_id=#{id}")
    int deleteOnsiteScores(Long id);
    @Delete("DELETE c FROM exam_onsite_code_submission c JOIN exam_onsite_task t ON t.id=c.task_id WHERE t.exam_id=#{id}")
    int deleteOnsiteSubmissions(Long id);
    @Delete("DELETE FROM exam_management_event WHERE exam_id=#{id}")
    int deleteManagementHistory(Long id);
    @Delete("DELETE FROM exam_legacy_recovery WHERE exam_id=#{id}")
    int deleteLegacyRecovery(Long id);
    @Delete("DELETE FROM exam_student WHERE exam_id=#{id}")
    int deleteExamStudents(Long id);
    @Select("SELECT EXISTS(SELECT 1 FROM exam_session WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_answer_record WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_score_revision WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_question_snapshot WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_result_version WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_incident WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_file_asset WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_code_submission WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_onsite_code_submission c JOIN exam_onsite_task t ON t.id=c.task_id WHERE t.exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_onsite_code_draft c JOIN exam_onsite_task t ON t.id=c.task_id WHERE t.exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_onsite_task WHERE exam_id=#{id} AND state<>'DRAFT') "
            + "OR EXISTS(SELECT 1 FROM exam_onsite_score s JOIN exam_onsite_task t ON t.id=s.task_id WHERE t.exam_id=#{id})")
    boolean hasProtectedRecords(Long id);
    @Select("SELECT EXISTS(SELECT 1 FROM exam_answer_record WHERE exam_id=#{id} "
            + "AND (COALESCE(answer,'') NOT REGEXP '^[[:space:]]*$' OR COALESCE(score,0)<>0)) "
            + "OR EXISTS(SELECT 1 FROM exam_score_revision WHERE exam_id=#{id} "
            + "AND (grader_id<>0 OR new_score<>0 OR COALESCE(previous_score,0)<>0)) "
            + "OR EXISTS(SELECT 1 FROM exam_result_version WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_incident WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_file_asset WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_code_submission WHERE exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_onsite_code_submission c JOIN exam_onsite_task t ON t.id=c.task_id WHERE t.exam_id=#{id}) "
            + "OR EXISTS(SELECT 1 FROM exam_onsite_code_draft c JOIN exam_onsite_task t ON t.id=c.task_id "
            + "WHERE t.exam_id=#{id} AND COALESCE(c.source,'') NOT REGEXP '^[[:space:]]*$') "
            + "OR EXISTS(SELECT 1 FROM exam_onsite_score s JOIN exam_onsite_task t ON t.id=s.task_id WHERE t.exam_id=#{id})")
    boolean hasNonblankRecords(Long id);
    @Delete("DELETE FROM exam_score_revision WHERE exam_id=#{id}")
    int deleteBlankRevisions(Long id);
    @Delete("DELETE FROM exam_answer_record WHERE exam_id=#{id}")
    int deleteBlankAnswers(Long id);
    @Delete("DELETE FROM exam_session WHERE exam_id=#{id}")
    int deleteBlankSessions(Long id);
    @Delete("DELETE c FROM exam_onsite_code_draft c JOIN exam_onsite_task t ON t.id=c.task_id WHERE t.exam_id=#{id}")
    int deleteBlankCodeDrafts(Long id);
    @Delete("DELETE v FROM exam_onsite_event v JOIN exam_onsite_task t ON t.id=v.task_id WHERE t.exam_id=#{id}")
    int deleteDraftTaskEvents(Long id);
    @Delete("DELETE FROM exam_onsite_task WHERE exam_id=#{id}")
    int deleteDraftTasks(Long id);
    @Delete("DELETE FROM exam_reviewer WHERE exam_id=#{id}")
    int deleteReviewers(Long id);
    @Select("SELECT question_id AS questionId,title,category,difficulty,question_type AS questionType FROM cyan_question WHERE status='0' AND question_type<>'ROBOT_PRACTICAL' ORDER BY question_id")
    List<Map<String,Object>> questionOptions();
    @Select("SELECT question_id AS questionId,title,category,difficulty,question_type AS questionType FROM cyan_question WHERE status='0' AND question_type='ROBOT_PRACTICAL' ORDER BY question_id")
    List<Map<String,Object>> robotOptions();
    @Select("SELECT u.user_id AS userId,u.user_name AS userName,u.nick_name AS nickName,"
            + "(SELECT MAX(s.student_no) FROM cyan_student s WHERE s.user_id=u.user_id) AS studentNo "
            + "FROM sys_user u WHERE u.status='0' AND u.del_flag='0' AND EXISTS "
            + "(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id "
            + "WHERE ur.user_id=u.user_id AND r.role_key='student' AND r.status='0' AND r.del_flag='0') ORDER BY u.user_id")
    List<Map<String,Object>> candidates();
    @Update("UPDATE exam_info SET policy_json=#{policy},duration_minutes=#{duration},early_entry_minutes=#{early} WHERE exam_id=#{id}")
    int policy(@Param("id") Long id, @Param("policy") String policy, @Param("duration") int duration, @Param("early") int early);
    @Update("UPDATE exam_info SET published_at=NULL WHERE exam_id=#{id}")
    int withdraw(Long id);
    @Delete("DELETE FROM exam_question_snapshot WHERE exam_id=#{id}")
    int clearSnapshots(Long id);
    @Update("UPDATE exam_info SET lifecycle='TERMINATED',end_time=NOW() WHERE exam_id=#{id}")
    int terminate(Long id);
    @Update("UPDATE exam_info SET lifecycle='ARCHIVED' WHERE exam_id=#{id}")
    int archive(Long id);
    @Update("UPDATE exam_session SET status='AUTO_SUBMITTED',submitted_at=NOW(3) WHERE exam_id=#{id} AND status='IN_PROGRESS'")
    int finish(Long id);
    @Insert("INSERT INTO exam_management_event(exam_id,actor_id,action,reason,payload) VALUES(#{exam},#{actor},#{action},#{reason},#{payload})")
    int event(@Param("exam") Long exam, @Param("actor") Long actor, @Param("action") String action,
              @Param("reason") String reason, @Param("payload") String payload);
    @Select("SELECT id,actor_id AS actorId,action,reason,payload,created_at AS createdAt FROM exam_management_event WHERE exam_id=#{id} ORDER BY id DESC")
    List<Map<String,Object>> history(Long id);
    @Select("SELECT COUNT(*) FROM exam_session WHERE exam_id=#{id}")
    int sessions(Long id);
}
