package com.cyan.exam.system.service;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.mapper.*;
import com.cyan.exam.system.service.coding.CodingRules;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PracticalCodeService {
    public record Input(String source, String key) {}
    private final OnsiteMapper mapper;
    private final CyanExamInfoMapper exams;
    private final ExamStudentMapper students;
    private final ExamSessionMapper sessions;
    private final ExamAccessScope scope;

    public PracticalCodeService(OnsiteMapper mapper, CyanExamInfoMapper exams, ExamStudentMapper students,
            ExamSessionMapper sessions, ExamAccessScope scope) {
        this.mapper=mapper; this.exams=exams; this.students=students; this.sessions=sessions; this.scope=scope;
    }

    private Map<String,Object> task(Long examId, Long taskId) {
        var task=mapper.task(taskId);
        if(task==null || !examId.equals(OnsiteService.number(task,"examId")))
            throw new ServiceException("题目不属于该考试",404);
        return task;
    }

    public List<Map<String,Object>> history(Long examId, Long taskId, Long userId) {
        var task=task(examId,taskId);
        var exam=exams.selectCyanExamInfoByExamId(examId);
        Long actor=scope.actorId();
        boolean manage=exam!=null && (SecurityUtils.isAdmin(actor) || actor.equals(exam.getOwnerUserId())
                || (exam.getPublishedAt()!=null && !"DRAFT".equals(task.get("state")) && mapper.canJudge(actor)));
        if(!manage && (!actor.equals(userId) || !students.selectExamIdsByUserId(actor).contains(examId)))
            throw new ServiceException("无权查看该考生代码",403);
        return mapper.codeHistory(taskId,userId);
    }

    @Transactional
    public Object save(Long examId, Long taskId, Input input, boolean submit) {
        Long user=scope.actorId();
        if(input==null || input.source()==null || input.source().getBytes(StandardCharsets.UTF_8).length>60000)
            throw new ServiceException("代码不能为空值或超过60KB");
        if(!students.selectExamIdsByUserId(user).contains(examId))
            throw new ServiceException("您没有权限参加该考试",403);
        // Serialize code versions with judge scoring, then with session completion.
        var exam=exams.lockByExamId(examId);
        var task=task(examId,taskId);
        if(submit) {
            if(input.source().isBlank() || input.key()==null || !input.key().matches("[0-9a-fA-F-]{36}"))
                throw new ServiceException("代码或提交标识无效");
            var duplicate=mapper.codeRequest(taskId,user,input.key());
            if(duplicate!=null) {
                if(!input.source().equals(duplicate.get("source"))) throw new ServiceException("提交标识已用于其他代码");
                return duplicate;
            }
        }
        var session=sessions.lockByExamAndUser(examId,user);
        Date now=new Date();
        if(exam==null || exam.getPublishedAt()==null || !"0".equals(exam.getStatus())
                || !"OPEN".equals(task.get("state")) || "ARCHIVED".equals(exam.getLifecycle())
                || "TERMINATED".equals(exam.getLifecycle()) || exam.getStartTime()==null || exam.getEndTime()==null
                || session==null || !"IN_PROGRESS".equals(session.getStatus())
                || now.before(exam.getStartTime()) || !now.before(ExamPolicy.deadline(exam,session)))
            throw new ServiceException("考试未开始、已截止或已交卷，不能修改代码");
        if(submit && mapper.scores(taskId).stream().anyMatch(s->user.equals(OnsiteService.number(s,"userId"))))
            throw new ServiceException("裁判已开始评分，不能更换正式代码");
        if(!submit) {
            mapper.saveCodeDraft(taskId,user,input.source());
            return Map.of("saved",true);
        }
        var value=new HashMap<String,Object>();
        value.put("task",taskId); value.put("user",user); value.put("source",input.source());
        value.put("hash",CodingRules.hash(input.source())); value.put("key",input.key());
        mapper.submitCode(value);
        mapper.event(taskId,user,user,"CODE_SUBMIT","提交机器人实操代码",
                com.alibaba.fastjson.JSON.toJSONString(Map.of("submissionId",value.get("id"),"sourceHash",value.get("hash"))));
        return mapper.codeRequest(taskId,user,input.key());
    }
}
