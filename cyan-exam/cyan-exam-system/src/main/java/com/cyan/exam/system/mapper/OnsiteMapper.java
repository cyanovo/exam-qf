package com.cyan.exam.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

public interface OnsiteMapper {
    String TASK = "t.id,t.exam_id AS examId,t.title,t.description,t.duration_minutes AS durationMinutes,"
            + "t.judge_id AS judgeId,u.nick_name AS judgeName,t.rubric,t.max_score AS maxScore,t.state";

    @Select("SELECT " + TASK + " FROM exam_onsite_task t LEFT JOIN sys_user u ON u.user_id=t.judge_id WHERE t.id=#{id}")
    Map<String, Object> task(Long id);

    @Select("SELECT " + TASK + " FROM exam_onsite_task t LEFT JOIN sys_user u ON u.user_id=t.judge_id "
            + "WHERE t.exam_id=#{examId} ORDER BY t.id")
    List<Map<String, Object>> tasks(Long examId);

    @Select("SELECT EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id "
            + "LEFT JOIN sys_role_menu rm ON rm.role_id=r.role_id LEFT JOIN sys_menu m ON m.menu_id=rm.menu_id "
            + "WHERE ur.user_id=#{actor} AND r.status='0' AND r.del_flag='0' "
            + "AND m.status='0' AND m.perms='exam:onsite:score')")
    boolean canJudge(Long actor);

    @Select("SELECT DISTINCT user_id FROM exam_onsite_code_submission WHERE task_id=#{taskId}")
    List<Long> submittedUsers(Long taskId);

    @Select("SELECT DISTINCT e.exam_id AS examId,e.exam_name AS examName FROM exam_info e "
            + "LEFT JOIN exam_onsite_task t ON t.exam_id=e.exam_id "
            + "WHERE #{admin}=true OR e.owner_user_id=#{actor} "
            + "OR (#{judge}=true AND e.published_at IS NOT NULL AND t.state<>'DRAFT') ORDER BY e.exam_id DESC")
    List<Map<String, Object>> exams(@Param("actor") Long actor, @Param("admin") boolean admin, @Param("judge") boolean judge);

    @Insert("INSERT INTO exam_onsite_task(exam_id,title,description,duration_minutes,judge_id,rubric,max_score,created_by) "
            + "VALUES(#{examId},#{title},#{description},#{durationMinutes},#{judgeId},#{rubric},#{maxScore},#{actor})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> task);

    @Update("UPDATE exam_onsite_task SET state=#{state} WHERE id=#{id}")
    int state(@Param("id") Long id, @Param("state") String state);

    @Select("SELECT user_id AS userId,nick_name AS name FROM sys_user WHERE status='0' AND del_flag='0' "
            + "AND user_type != '01' ORDER BY user_id LIMIT 300")
    List<Map<String, Object>> users();

    @Select("SELECT s.user_id AS userId,u.nick_name AS name FROM exam_student s JOIN sys_user u ON u.user_id=s.user_id "
            + "WHERE s.exam_id=#{examId} ORDER BY s.user_id")
    List<Map<String, Object>> roster(Long examId);

    @Select("SELECT s.user_id AS userId,s.judge_id AS judgeId,u.nick_name AS judgeName,s.points,s.note,s.total,s.state,s.revision,"
            + "s.submission_id AS submissionId,s.updated_at AS updatedAt "
            + "FROM exam_onsite_score s LEFT JOIN sys_user u ON u.user_id=s.judge_id WHERE s.task_id=#{taskId} ORDER BY s.user_id")
    List<Map<String, Object>> scores(Long taskId);

    @Insert("INSERT INTO exam_onsite_score(task_id,user_id,judge_id,points,note,total,state,revision,submission_id) "
            + "VALUES(#{taskId},#{userId},#{judgeId},#{points},#{note},#{total},#{state},1,#{submissionId}) "
            + "ON DUPLICATE KEY UPDATE judge_id=VALUES(judge_id),points=VALUES(points),note=VALUES(note),"
            + "total=VALUES(total),state=VALUES(state),submission_id=VALUES(submission_id),revision=revision+1,updated_at=CURRENT_TIMESTAMP(3)")
    int save(Map<String, Object> score);

    @Update("UPDATE exam_onsite_score SET state=#{state},revision=revision+1,updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE task_id=#{taskId} AND user_id=#{userId}")
    int scoreState(@Param("taskId") Long taskId, @Param("userId") Long userId, @Param("state") String state);

    @Insert("INSERT INTO exam_onsite_event(task_id,user_id,actor_id,action,reason,payload) "
            + "VALUES(#{taskId},#{userId},#{actor},#{action},#{reason},#{payload})")
    int event(@Param("taskId") Long taskId, @Param("userId") Long userId, @Param("actor") Long actor,
              @Param("action") String action, @Param("reason") String reason, @Param("payload") String payload);

    @Select("SELECT user_id AS userId,actor_id AS actorId,action,reason,payload,created_at AS createdAt "
            + "FROM exam_onsite_event WHERE task_id=#{id} ORDER BY id DESC LIMIT 200")
    List<Map<String, Object>> events(Long id);

    @Select("SELECT COUNT(*) FROM exam_onsite_event v JOIN exam_onsite_task t ON t.id=v.task_id "
            + "WHERE t.exam_id=#{examId} AND v.actor_id=#{actor} AND v.action IN ('SAVE','SUBMIT')")
    int hasGraded(@Param("examId") Long examId, @Param("actor") Long actor);

    @Select("SELECT source FROM exam_onsite_code_draft WHERE task_id=#{task} AND user_id=#{user}")
    String codeDraft(@Param("task") Long task, @Param("user") Long user);

    @Insert("INSERT INTO exam_onsite_code_draft(task_id,user_id,source) VALUES(#{task},#{user},#{source}) "
            + "ON DUPLICATE KEY UPDATE source=VALUES(source)")
    int saveCodeDraft(@Param("task") Long task, @Param("user") Long user, @Param("source") String source);

    String CODE = "id,source,source_hash AS sourceHash,received_at AS receivedAt,'FORMAL' AS mode,'SUBMITTED' AS state";

    @Select("SELECT " + CODE + " FROM exam_onsite_code_submission WHERE task_id=#{task} AND user_id=#{user} ORDER BY id DESC LIMIT 100")
    List<Map<String,Object>> codeHistory(@Param("task") Long task, @Param("user") Long user);

    @Select("SELECT " + CODE + " FROM exam_onsite_code_submission WHERE task_id=#{task} AND user_id=#{user} AND request_key=#{key}")
    Map<String,Object> codeRequest(@Param("task") Long task, @Param("user") Long user, @Param("key") String key);

    @Insert("INSERT INTO exam_onsite_code_submission(task_id,user_id,source,source_hash,request_key) "
            + "VALUES(#{task},#{user},#{source},#{hash},#{key})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int submitCode(Map<String,Object> value);
}
