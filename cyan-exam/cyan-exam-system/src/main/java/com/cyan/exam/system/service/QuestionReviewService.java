package com.cyan.exam.system.service;

import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.mapper.CyanQuestionMapper;
import com.cyan.exam.system.mapper.QuestionReviewMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionReviewService {
    private final CyanQuestionMapper questions;
    private final QuestionReviewMapper reviews;
    public QuestionReviewService(CyanQuestionMapper questions, QuestionReviewMapper reviews) {
        this.questions=questions; this.reviews=reviews;
    }
    public record Decision(String action, Integer revision, String reason) {}
    public static String nextState(String state, String action) {
        return switch (action == null ? "" : action) {
            case "SUBMIT" -> require(state, Set.of("DRAFT"), "REVIEW");
            case "APPROVE" -> require(state, Set.of("REVIEW"), "PUBLISHED");
            case "REJECT", "WITHDRAW" -> require(state, Set.of("REVIEW"), "DRAFT");
            case "DISABLE" -> require(state, Set.of("PUBLISHED"), "DISABLED");
            case "REOPEN" -> require(state, Set.of("DISABLED"), "DRAFT");
            default -> throw new ServiceException("未知审核操作");
        };
    }
    private static String require(String state, Set<String> allowed, String next) {
        if (!allowed.contains(state)) throw new ServiceException("题目状态已变化，请刷新后操作");
        return next;
    }
    public CyanQuestion lockForEdit(Long id, Integer revision) {
        CyanQuestion stored=questions.lockQuestion(id);
        if (stored==null) throw new ServiceException("题目不存在");
        if (revision==null || !revision.equals(stored.getRevision())) throw new ServiceException("题目已被修改，请刷新后重试");
        if ("REVIEW".equals(stored.getReviewState())) throw new ServiceException("待审核题目请先撤回，再修改");
        return stored;
    }
    public void record(CyanQuestion q, String action, String from, String reason) {
        reviews.event(q.getQuestionId(), q.getRevision(), action, from, q.getReviewState(),
                SecurityUtils.getUsername(), reason, JSON.toJSONString(q));
    }
    @Transactional
    public void decide(Long id, Decision input) {
        if (input==null || input.revision()==null) throw new ServiceException("缺少题目版本");
        CyanQuestion q=questions.lockQuestion(id);
        if(q==null || !input.revision().equals(q.getRevision())) throw new ServiceException("题目已变化，请刷新");
        String next=nextState(q.getReviewState(),input.action());
        String reason=Objects.toString(input.reason(),"").trim();
        if(reason.length()>1000 || ("REJECT".equals(input.action()) && reason.isBlank()))
            throw new ServiceException("退回需填写原因，意见不超过1000字");
        if (Set.of("SUBMIT","APPROVE").contains(input.action()) &&
                (q.getTitle()==null || q.getTitle().isBlank() || q.getAnswer()==null || q.getAnswer().isBlank()))
            throw new ServiceException("请先补齐题干和答案或评分规则");
        String from=q.getReviewState();
        q.setReviewState(next); q.setRevision(q.getRevision()+1); q.setStatus("DISABLED".equals(next)?"1":"0");
        reviews.transition(id,next,q.getStatus(),SecurityUtils.getUsername());
        record(q,input.action(),from,reason);
    }
    public List<Map<String,Object>> history(Long id) { return reviews.history(id); }
}
