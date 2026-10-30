package com.cyan.exam.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

public interface QuestionReviewMapper {
    @Update("UPDATE cyan_question SET review_state=#{state},status=#{status},revision=revision+1,update_by=#{actor},update_time=NOW() WHERE question_id=#{id}")
    int transition(@Param("id") Long id, @Param("state") String state, @Param("status") String status, @Param("actor") String actor);

    @Insert("INSERT INTO exam_question_review_event(question_id,revision,action,from_state,to_state,actor,reason,snapshot_json) VALUES(#{id},#{revision},#{action},#{from},#{to},#{actor},#{reason},#{snapshot})")
    int event(@Param("id") Long id, @Param("revision") int revision, @Param("action") String action,
              @Param("from") String from, @Param("to") String to, @Param("actor") String actor,
              @Param("reason") String reason, @Param("snapshot") String snapshot);

    @Select("SELECT revision,action,from_state AS fromState,to_state AS toState,actor,reason,created_at AS createdAt FROM exam_question_review_event WHERE question_id=#{id} ORDER BY revision DESC LIMIT 200")
    List<Map<String,Object>> history(Long id);
}
