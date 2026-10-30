package com.cyan.exam.system.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface ExamDossierMapper {
    String INCIDENT="id,exam_id AS examId,task_id AS taskId,user_id AS userId,created_by AS createdBy,category,title,description,state,revision,created_at AS createdAt,updated_at AS updatedAt";
    String FILE="id,exam_id AS examId,task_id AS taskId,user_id AS userId,incident_id AS incidentId,uploaded_by AS uploadedBy,purpose,original_name AS name,storage_key AS storageKey,media_type AS mediaType,byte_size AS size,sha256,retain_until AS retainUntil,CAST(deleted AS SIGNED) AS deleted,created_at AS createdAt";
    @Select("SELECT "+INCIDENT+" FROM exam_incident WHERE exam_id=#{id} ORDER BY id DESC")
    List<Map<String,Object>> incidents(Long id);
    @Select("SELECT "+INCIDENT+" FROM exam_incident WHERE id=#{id} FOR UPDATE")
    Map<String,Object> incident(Long id);
    @Insert("INSERT INTO exam_incident(exam_id,task_id,user_id,created_by,category,title,description) VALUES(#{examId},#{taskId},#{userId},#{actor},#{category},#{title},#{description})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertIncident(Map<String,Object> row);
    @Update("UPDATE exam_incident SET state=#{state},revision=revision+1,updated_at=NOW(3) WHERE id=#{id} AND revision=#{revision}")
    int state(@Param("id") Long id,@Param("revision") int revision,@Param("state") String state);
    @Insert("INSERT INTO exam_incident_event(incident_id,actor_id,action,opinion,before_state,after_state) VALUES(#{id},#{actor},#{action},#{opinion},#{before},#{after})")
    int event(@Param("id") Long id,@Param("actor") Long actor,@Param("action") String action,@Param("opinion") String opinion,@Param("before") String before,@Param("after") String after);
    @Select("SELECT id,actor_id AS actorId,action,opinion,before_state AS beforeState,after_state AS afterState,created_at AS createdAt FROM exam_incident_event WHERE incident_id=#{id} ORDER BY id")
    List<Map<String,Object>> events(Long id);
    @Insert("INSERT INTO exam_file_asset(exam_id,task_id,user_id,incident_id,uploaded_by,purpose,original_name,storage_key,media_type,byte_size,sha256,retain_until) "
            +"VALUES(#{examId},#{taskId},#{userId},#{incidentId},#{actor},#{purpose},#{name},#{storageKey},#{mediaType},#{size},#{sha256},#{retainUntil})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertFile(Map<String,Object> row);
    @Select("SELECT "+FILE+" FROM exam_file_asset WHERE exam_id=#{id} ORDER BY id DESC")
    List<Map<String,Object>> files(Long id);
    @Select("SELECT "+FILE+" FROM exam_file_asset WHERE id=#{id}")
    Map<String,Object> file(Long id);
    @Update("UPDATE exam_file_asset SET deleted=1 WHERE id=#{id} AND deleted=0")
    int deleteFile(Long id);
    @Select("SELECT COUNT(*) FROM exam_incident WHERE exam_id=#{id} AND state<>'APPROVED'")
    int openIncidents(Long id);
}
