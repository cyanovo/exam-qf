package com.cyan.exam.system.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

public interface CodeSubmissionMapper {
    String COLUMNS = "id,exam_id AS examId,user_id AS userId,question_id AS questionId,mode,idem_key AS idemKey,"
            + "source,source_hash AS sourceHash,snapshot_hash AS snapshotHash,state,score,attempts,progress,feedback,received_at AS receivedAt,"
            + "run_version AS runVersion,completed_at AS completedAt";
    @Select("SELECT " + COLUMNS + " FROM exam_code_submission WHERE id=#{id}")
    Map<String, Object> get(Long id);
    @Select("SELECT " + COLUMNS + " FROM exam_code_submission WHERE exam_id=#{examId} AND user_id=#{userId} AND idem_key=#{key}")
    Map<String, Object> byKey(@Param("examId") Long examId, @Param("userId") Long userId, @Param("key") String key);
    @Select("SELECT " + COLUMNS + " FROM exam_code_submission WHERE exam_id=#{examId} AND user_id=#{userId} "
            + "AND question_id=#{questionId} ORDER BY id DESC LIMIT 30")
    List<Map<String, Object>> history(@Param("examId") Long examId, @Param("userId") Long userId, @Param("questionId") Long questionId);
    @Select("SELECT " + COLUMNS + " FROM exam_code_submission WHERE exam_id=#{examId} AND user_id=#{userId} "
            + "AND question_id=#{questionId} AND mode='FORMAL' ORDER BY id DESC LIMIT 1")
    Map<String, Object> latestFormal(@Param("examId") Long examId, @Param("userId") Long userId, @Param("questionId") Long questionId);

    @Select("SELECT COUNT(*) FROM exam_code_submission WHERE user_id=#{userId} AND "
            + "(state IN ('QUEUED','RUNNING','RETRY') OR received_at > NOW()-INTERVAL 10 SECOND)")
    int pendingOrRecent(Long userId);
    @Select("SELECT COUNT(*) FROM exam_code_submission WHERE state IN ('QUEUED','RUNNING','RETRY')")
    int queueSize();
    @Insert("INSERT INTO exam_code_submission(exam_id,user_id,question_id,mode,idem_key,source,source_hash,snapshot_hash) "
            + "SELECT #{examId},#{userId},#{questionId},#{mode},#{key},#{source},#{sourceHash},#{snapshotHash} "
            + "FROM exam_info e JOIN exam_session s ON s.exam_id=e.exam_id AND s.user_id=#{userId} "
            + "WHERE e.exam_id=#{examId} AND e.status='0' AND e.published_at IS NOT NULL "
            + "AND e.start_time<=NOW(3) AND e.end_time>NOW(3) AND s.status='IN_PROGRESS' "
            + "AND (e.duration_minutes=0 OR DATE_ADD(s.started_at,INTERVAL e.duration_minutes MINUTE)>NOW(3))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> submission);

    @Select("SELECT id FROM exam_code_submission WHERE state IN ('QUEUED','RUNNING','RETRY') "
            + "AND (lease_until IS NULL OR lease_until < NOW(3)) ORDER BY id LIMIT 8")
    List<Long> ready();
    @Update("UPDATE exam_code_submission SET state='RUNNING',lease_token=#{token},lease_until=NOW(3)+INTERVAL 90 SECOND "
            + "WHERE id=#{id} AND state IN ('QUEUED','RUNNING','RETRY') AND (lease_until IS NULL OR lease_until < NOW(3))")
    int claim(@Param("id") Long id, @Param("token") String token);
    @Update("UPDATE exam_code_submission SET progress=#{progress},state='QUEUED',lease_token=NULL,"
            + "lease_until=NOW(3)+INTERVAL 2 SECOND WHERE id=#{id} AND lease_token=#{token}")
    int progress(@Param("id") Long id, @Param("token") String token, @Param("progress") String progress);
    @Update("UPDATE exam_code_submission SET state='SUCCEEDED',score=#{score},feedback=#{feedback},completed_at=NOW(3),"
            + "lease_token=NULL,lease_until=NULL WHERE id=#{id} AND lease_token=#{token}")
    int complete(@Param("id") Long id, @Param("token") String token, @Param("score") int score, @Param("feedback") String feedback);
    @Update("UPDATE exam_code_submission SET state=IF(attempts>=2,'FAILED','RETRY'),attempts=attempts+1,"
            + "progress=JSON_REMOVE(COALESCE(progress,'{}'),'$.token','$.started'),"
            + "lease_token=NULL,lease_until=NOW(3)+INTERVAL 30 SECOND WHERE id=#{id} AND lease_token=#{token}")
    int fail(@Param("id") Long id, @Param("token") String token);
    @Update("UPDATE exam_code_submission SET state='RETRY',attempts=0,lease_until=NULL WHERE id=#{id} AND state='FAILED'")
    int retry(Long id);

    @Select("SELECT " + COLUMNS + " FROM exam_code_submission WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lock(Long id);

    @Insert("INSERT INTO exam_code_evaluation_history(submission_id,run_version,state,score,attempts,feedback,progress,"
            + "source_hash,snapshot_hash,completed_at,actor_id,reason) "
            + "SELECT id,run_version,state,score,attempts,feedback,progress,source_hash,snapshot_hash,completed_at,#{actor},#{reason} "
            + "FROM exam_code_submission WHERE id=#{id} AND run_version=#{version} AND state IN ('FAILED','SUCCEEDED')")
    int archive(@Param("id") Long id, @Param("version") int version, @Param("actor") Long actor, @Param("reason") String reason);

    @Update("UPDATE exam_code_submission SET state='QUEUED',score=NULL,attempts=0,progress=NULL,feedback=NULL,"
            + "completed_at=NULL,lease_token=NULL,lease_until=NULL,run_version=run_version+1 "
            + "WHERE id=#{id} AND run_version=#{version} AND state IN ('FAILED','SUCCEEDED')")
    int regrade(@Param("id") Long id, @Param("version") int version);

    @Select("SELECT run_version AS runVersion,state,score,attempts,feedback,source_hash AS sourceHash,"
            + "snapshot_hash AS snapshotHash,completed_at AS completedAt,actor_id AS actorId,reason,archived_at AS archivedAt "
            + "FROM exam_code_evaluation_history WHERE submission_id=#{id} ORDER BY run_version DESC")
    List<Map<String, Object>> evaluations(Long id);
}
