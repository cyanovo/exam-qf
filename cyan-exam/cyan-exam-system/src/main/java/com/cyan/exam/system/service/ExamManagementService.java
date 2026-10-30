package com.cyan.exam.system.service;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.config.RuoYiConfig;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.mapper.*;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ExamManagementService {
    private final CyanExamInfoMapper exams;
    private final ExamManagementMapper mapper;
    private final ExamQuestionMapper questions;
    private final OnsiteMapper onsite;
    private final ExamResultMapper results;
    private final ExamAccessScope scope;
    private final ExamDossierMapper dossier;
    public ExamManagementService(CyanExamInfoMapper exams, ExamManagementMapper mapper, ExamQuestionMapper questions,
            OnsiteMapper onsite, ExamResultMapper results, ExamAccessScope scope, ExamDossierMapper dossier) {
        this.exams=exams; this.mapper=mapper; this.questions=questions; this.onsite=onsite; this.results=results; this.scope=scope;
        this.dossier=dossier;
    }
    public record Configuration(int durationMinutes, int earlyEntryMinutes, ExamPolicy.Policy policy) {}
    @Transactional
    public void forceDelete(Long id, String name, String reason) {
        if (!SecurityUtils.isAdmin(scope.actorId())) throw new ServiceException("仅超级管理员可以强制删除考试", 403);
        var exam = exams.lockByExamId(id);
        scope.requireAccess(exam);
        if (name == null || !name.equals(exam.getExamName())
                || reason == null || reason.trim().length() < 5 || reason.length() > 500)
            throw new ServiceException("请准确输入考试名称并填写至少5字的删除原因");

        Path root = Path.of(RuoYiConfig.getProfile() + "-exam-private").toAbsolutePath().normalize();
        List<Path> files = mapper.fileKeys(id).stream().map(key -> {
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root) || target.equals(root)) throw new ServiceException("材料文件路径无效");
            return target;
        }).toList();
        mapper.deleteCodeHistory(id);
        mapper.deleteCodeSubmissions(id);
        mapper.deleteResultEvents(id);
        mapper.deleteResultVersions(id);
        mapper.deleteIncidentEvents(id);
        mapper.deleteFiles(id);
        mapper.deleteIncidents(id);
        mapper.deleteOnsiteScores(id);
        mapper.deleteOnsiteSubmissions(id);
        mapper.deleteBlankCodeDrafts(id);
        mapper.deleteDraftTaskEvents(id);
        mapper.deleteDraftTasks(id);
        mapper.deleteReviewers(id);
        mapper.deleteBlankRevisions(id);
        mapper.deleteBlankAnswers(id);
        mapper.deleteBlankSessions(id);
        mapper.clearSnapshots(id);
        questions.deleteExamQuestionByExamId(id);
        mapper.deleteLegacyRecovery(id);
        // Keep only the minimal deletion audit, without retaining candidate data.
        mapper.deleteManagementHistory(id);
        mapper.event(id, scope.actorId(), "FORCE_DELETE", reason.trim(),
                JSON.toJSONString(Map.of("examName", exam.getExamName(), "examId", id)));
        mapper.deleteExamStudents(id);
        if (exams.deleteCyanExamInfoByExamIds(new Long[]{id}) != 1)
            throw new ServiceException("考试已变化，请刷新后重试");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                for (Path file : files) {
                    try { Files.deleteIfExists(file); }
                    catch (IOException ex) {
                        org.slf4j.LoggerFactory.getLogger(ExamManagementService.class)
                                .warn("Force-delete file cleanup failed for exam {}", id, ex);
                    }
                }
            }
        });
    }
    public void validateDeletion(Long id) {
        if (mapper.hasProtectedRecords(id))
            throw new ServiceException("考试已有作答、评分、异常或材料记录，不能直接删除，请保留记录并按流程归档");
    }
    public void validateBlankDeletion(Long id) {
        if (mapper.hasNonblankRecords(id))
            throw new ServiceException("考试已有实际答案、代码、人工评分、成绩复核或材料记录，不能按白卷删除；请保留并归档");
    }
    public void removeBlankRelations(Long id) {
        mapper.deleteBlankRevisions(id);
        mapper.deleteBlankAnswers(id);
        mapper.deleteBlankSessions(id);
        mapper.deleteBlankCodeDrafts(id);
        mapper.clearSnapshots(id);
        mapper.event(id, scope.actorId(), "DELETE_BLANK", "删除已结束且无实际作答的白卷考试", null);
    }
    public void removeDraftRelations(Long id) {
        mapper.deleteDraftTaskEvents(id);
        mapper.deleteDraftTasks(id);
        mapper.deleteReviewers(id);
        mapper.event(id,scope.actorId(),"DELETE","删除考试及编排关联",null);
    }
    public List<Map<String,Object>> candidates(Long id) {
        scope.requireAccess(exams.selectCyanExamInfoByExamId(id));
        return mapper.candidates();
    }
    public List<Map<String,Object>> questionOptions(Long id) {
        scope.requireAccess(exams.selectCyanExamInfoByExamId(id));
        return mapper.questionOptions();
    }
    public List<Map<String,Object>> robotOptions(Long id) {
        scope.requireAccess(exams.selectCyanExamInfoByExamId(id));
        return mapper.robotOptions();
    }
    public void validateStudents(List<Long> ids) {
        if(ids==null || ids.isEmpty()) return;
        var allowed=mapper.candidates().stream().map(u->((Number)u.get("userId")).longValue()).collect(java.util.stream.Collectors.toSet());
        if(ids.size()!=new HashSet<>(ids).size() || !allowed.containsAll(ids))
            throw new ServiceException("请选择有效、启用且具有学生角色的账号，名单不能重复");
    }
    public Map<String,Object> detail(Long id) {
        var e = exams.selectCyanExamInfoByExamId(id);
        scope.requireAccess(e);
        return Map.of("exam", e, "policy", ExamPolicy.read(e), "state", ExamPolicy.state(e),
                "questions", questions.selectAssignedQuestions(id), "tasks", onsite.tasks(id), "history", mapper.history(id));
    }
    public static void editable(CyanExamInfo e) {
        if (!"ACTIVE".equals(e.getLifecycle()) || e.getPublishedAt()!=null)
            throw new ServiceException("只有未发布且未终止、未归档的考试可以修改");
    }
    public void recordPublication(CyanExamInfo exam) {
        mapper.event(exam.getExamId(),scope.actorId(),"PUBLISH","统一发布考试",
                JSON.toJSONString(Map.of("policy",ExamPolicy.read(exam),
                        "durationMinutes",exam.getDurationMinutes(),"earlyEntryMinutes",exam.getEarlyEntryMinutes())));
    }
    @Transactional
    public void configure(Long id, Configuration c) {
        var e = exams.lockByExamId(id); scope.requireAccess(e); editable(e);
        if (c==null || c.durationMinutes()<0 || c.durationMinutes()>1440 || c.earlyEntryMinutes()<0 || c.earlyEntryMinutes()>1440)
            throw new ServiceException("作答时长或入场窗口无效");
        ExamPolicy.validate(c.policy());
        validateEntries(id, c.policy(), false);
        mapper.policy(id, JSON.toJSONString(c.policy()), c.durationMinutes(), c.earlyEntryMinutes());
        mapper.event(id,scope.actorId(),"CONFIGURE","修改考试配置",JSON.toJSONString(Map.of("before",ExamPolicy.read(e),"after",c)));
    }
    public void validateEntries(Long id, ExamPolicy.Policy policy, boolean complete) {
        var qs=questions.selectAssignedQuestions(id);
        var ts=onsite.tasks(id);
        Set<String> expected=new HashSet<>();
        qs.forEach(q->expected.add("QUESTION:"+q.getQuestionId()));
        ts.forEach(t->expected.add("ONSITE:"+t.get("id")));
        Set<String> actual=new HashSet<>();
        policy.entries().forEach(e->actual.add(e.kind()+":"+e.id()));
        if (!expected.containsAll(actual) || (complete && !actual.isEmpty() && !actual.equals(expected)))
            throw new ServiceException("编排必须包含本考试的全部题目和现场任务，且不能引用其他考试");
        if ("TASK_SCORE".equals(policy.tieRule()) && ts.stream().noneMatch(t->policy.tieTaskId().equals(((Number)t.get("id")).longValue())))
            throw new ServiceException("同分排序任务不属于本考试");
        if ("SUBMITTED_AT".equals(policy.tieRule()) && qs.isEmpty())
            throw new ServiceException("纯现场比赛请使用并列或任务得分同分规则");
    }
    @Transactional
    public void action(Long id,String action,String reason) {
        var e=exams.lockByExamId(id); scope.requireAccess(e);
        if(reason==null || reason.isBlank() || reason.length()>1000) throw new ServiceException("请填写操作原因");
        String state=ExamPolicy.state(e);
        switch(action) {
            case "withdraw" -> {
                if(!Set.of("PUBLISHED","READY").contains(state) || mapper.sessions(id)>0)
                    throw new ServiceException("仅未开始且无作答记录的考试可以撤回");
                mapper.withdraw(id); mapper.clearSnapshots(id);
                for (var task : onsite.tasks(id)) {
                    if (!onsite.scores(((Number)task.get("id")).longValue()).isEmpty())
                        throw new ServiceException("现场任务已有评分，不能撤回考试");
                    onsite.state(((Number)task.get("id")).longValue(),"DRAFT");
                }
            }
            case "terminate" -> {
                if(!"RUNNING".equals(state)) throw new ServiceException("仅进行中的考试可以终止");
                mapper.terminate(id); mapper.finish(id);
            }
            case "archive" -> {
                var r=results.latest(id);
                if(!Set.of("CLOSED","TERMINATED").contains(state) || r==null || !"RELEASED".equals(r.get("state")))
                    throw new ServiceException("成绩发布后才能归档");
                if(dossier.openIncidents(id)>0) throw new ServiceException("尚有未完成审批的异常工单，不能归档");
                mapper.archive(id);
            }
            default -> throw new ServiceException("不支持的操作");
        }
        mapper.event(id,scope.actorId(),action.toUpperCase(Locale.ROOT),reason,JSON.toJSONString(e));
    }
}
