package com.cyan.exam.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

public interface ExamResultMapper {
    @Select("SELECT id, exam_id AS examId, version_no AS version, state, payload, requested_by AS requestedBy, "
            + "reviewed_by AS reviewedBy, released_at AS releasedAt FROM exam_result_version "
            + "WHERE exam_id=#{examId} ORDER BY version_no DESC LIMIT 1")
    Map<String, Object> latest(Long examId);

    @Select("SELECT id, exam_id AS examId, version_no AS version, payload, released_at AS releasedAt "
            + "FROM exam_result_version WHERE exam_id=#{examId} AND released_at IS NOT NULL "
            + "ORDER BY version_no DESC LIMIT 1")
    Map<String, Object> published(Long examId);

    @Select("SELECT DISTINCT v.exam_id AS examId, e.exam_name AS examName FROM exam_result_version v "
            + "JOIN exam_info e ON e.exam_id=v.exam_id JOIN exam_student s ON s.exam_id=v.exam_id "
            + "WHERE s.user_id=#{userId} AND v.released_at IS NOT NULL ORDER BY v.exam_id DESC")
    List<Map<String, Object>> myResults(Long userId);

    @Select("SELECT COUNT(*) FROM exam_reviewer WHERE exam_id=#{examId} AND user_id=#{userId}")
    int isReviewer(@Param("examId") Long examId, @Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO exam_reviewer(exam_id,user_id,assigned_by) VALUES(#{examId},#{userId},#{actorId})")
    int assign(@Param("examId") Long examId, @Param("userId") Long userId, @Param("actorId") Long actorId);

    @Select("SELECT r.user_id AS userId, u.nick_name AS name FROM exam_reviewer r "
            + "JOIN sys_user u ON u.user_id=r.user_id WHERE r.exam_id=#{examId}")
    List<Map<String, Object>> reviewers(Long examId);

    @Insert("INSERT INTO exam_result_version(exam_id,version_no,state,payload,requested_by,reason) "
            + "VALUES(#{examId},#{version},'REVIEW',#{payload},#{actorId},#{reason})")
    int insert(Map<String, Object> version);

    @Update("UPDATE exam_result_version SET state=#{state}, reviewed_by=#{actorId}, "
            + "released_at=CASE WHEN #{state}='RELEASED' THEN COALESCE(released_at,CURRENT_TIMESTAMP(3)) ELSE released_at END "
            + "WHERE id=#{id} AND state=#{previous}")
    int transition(@Param("id") Long id, @Param("previous") String previous,
                   @Param("state") String state, @Param("actorId") Long actorId);

    @Insert("INSERT INTO exam_result_event(version_id,actor_id,old_state,new_state,reason) "
            + "VALUES(#{id},#{actorId},#{previous},#{state},#{reason})")
    int event(@Param("id") Long id, @Param("actorId") Long actorId,
              @Param("previous") String previous, @Param("state") String state, @Param("reason") String reason);

    @Select("SELECT actor_id AS actorId, old_state AS oldState, new_state AS newState, reason, "
            + "created_at AS createdAt FROM exam_result_event WHERE version_id=#{id} ORDER BY id")
    List<Map<String, Object>> events(Long id);

    @Select("SELECT COUNT(*) FROM exam_score_revision WHERE exam_id=#{examId} AND grader_id=#{userId}")
    int hasGraded(@Param("examId") Long examId, @Param("userId") Long userId);

    @Update("UPDATE exam_session SET status='GRADED' WHERE exam_id=#{examId} AND status IN ('SUBMITTED','AUTO_SUBMITTED')")
    int markGraded(Long examId);

    @Select("SELECT e.exam_id AS examId,e.exam_name AS examName FROM exam_info e JOIN exam_reviewer r "
            + "ON r.exam_id=e.exam_id WHERE r.user_id=#{userId} AND e.published_at IS NOT NULL ORDER BY e.exam_id DESC")
    List<Map<String, Object>> reviewExams(Long userId);
}
