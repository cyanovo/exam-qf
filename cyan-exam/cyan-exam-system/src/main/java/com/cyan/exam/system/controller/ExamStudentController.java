package com.cyan.exam.system.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.cyan.exam.common.core.controller.BaseController;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.mapper.CyanExamInfoMapper;
import com.cyan.exam.system.mapper.ExamStudentMapper;
import com.cyan.exam.system.service.IExamStudentService;
import com.cyan.exam.system.domain.ExamSession;
import com.cyan.exam.common.annotation.Log;
import com.cyan.exam.common.enums.BusinessType;

@RestController
@RequestMapping("/exam/student")
public class ExamStudentController extends BaseController {

    @Autowired
    private IExamStudentService examStudentService;

    @Autowired
    private ExamStudentMapper examStudentMapper;

    @Autowired
    private CyanExamInfoMapper examInfoMapper;
    @Autowired
    private com.cyan.exam.system.mapper.OnsiteMapper onsiteMapper;

    @PostMapping("/{examId}/session")
    public AjaxResult startSession(@PathVariable Long examId) {
        return success(examStudentService.startSession(examId, SecurityUtils.getUserId()));
    }

    @Log(title = "考试交卷", businessType = BusinessType.UPDATE)
    @PostMapping("/{examId}/finish")
    public AjaxResult finishSession(@PathVariable Long examId) {
        return success(examStudentService.finishSession(examId, SecurityUtils.getUserId()));
    }

    /**
     * 学生考试等待信息
     */
    @GetMapping("/waitInfo")
    public AjaxResult waitInfo() {
        Long userId = SecurityUtils.getUserId();
        Map<String, Object> data = examStudentService.getWaitInfo(userId);
        return success(data);
    }

    /**
     * 获取考试题目和答题记录（带校验）
     */
    @GetMapping("/questions")
    public AjaxResult questions(@RequestParam Long examId) {
        Long userId = SecurityUtils.getUserId();

        // 校验学生是否分配到此考试
        List<Long> assignedExams = examStudentMapper.selectExamIdsByUserId(userId);
        if (!assignedExams.contains(examId)) {
            return error("您没有权限参加该考试");
        }

        // 获取考试信息
        CyanExamInfo exam = examInfoMapper.selectCyanExamInfoByExamId(examId);
        if (exam == null || exam.getPublishedAt() == null || !"0".equals(exam.getStatus())
                || exam.getStartTime() == null || exam.getEndTime() == null) {
            return error("考试不存在或不可作答");
        }

        // 检查考试时间
        Date now = new Date();
        if (now.before(exam.getStartTime())) {
            return error("考试尚未开始");
        }
        if (!now.before(exam.getEndTime())) {
            return error("考试已结束");
        }
        ExamSession session = examStudentService.startSession(examId, userId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            return error("考试已交卷");
        }

        // 获取题目列表（包含学生已有答案）
        List<Map<String, Object>> questionList = examStudentService.getQuestionsWithAnswers(examId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("examName", exam.getExamName());
        result.put("endTime", com.cyan.exam.system.service.ExamPolicy.deadline(exam, session));
        result.put("serverTime", new Date());
        result.put("policy", com.cyan.exam.system.service.ExamPolicy.read(exam));
        result.put("questions", questionList);
        result.put("onsiteTasks", onsiteMapper.tasks(examId).stream().filter(t -> !"DRAFT".equals(t.get("state"))).map(t -> {
            Map<String,Object> safe=new HashMap<>();
            for(String key:List.of("id","title","description","durationMinutes","maxScore","state")) safe.put(key,t.get(key));
            Long taskId=((Number)t.get("id")).longValue();
            String draft=onsiteMapper.codeDraft(taskId,userId);
            var history=onsiteMapper.codeHistory(taskId,userId);
            safe.put("userAnswer",draft!=null?draft:history.isEmpty()?"":history.get(0).get("source"));
            safe.put("submissionId",history.isEmpty()?null:history.get(0).get("id"));
            safe.put("submittedSource",history.isEmpty()?null:history.get(0).get("source"));
            safe.put("codeLocked",onsiteMapper.scores(taskId).stream().anyMatch(s->userId.equals(((Number)s.get("userId")).longValue())));
            return safe;
        }).toList());
        return success(result);
    }

    /**
     * 提交一道题的答案
     */
    @PostMapping("/submitAnswer")
    public AjaxResult submitAnswer(@RequestBody Map<String, Object> params) {
        if (params == null || params.get("examId") == null || params.get("questionId") == null
                || !(params.get("answer") instanceof String)) {
            return error("答题参数不完整");
        }
        Long examId = Long.valueOf(params.get("examId").toString());
        Long questionId = Long.valueOf(params.get("questionId").toString());
        String answer = params.get("answer").toString();
        Long userId = SecurityUtils.getUserId();

        examStudentService.submitAnswer(examId, userId, questionId, answer);
        return success();
    }
}
