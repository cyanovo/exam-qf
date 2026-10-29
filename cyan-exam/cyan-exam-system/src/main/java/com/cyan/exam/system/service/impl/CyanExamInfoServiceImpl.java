package com.cyan.exam.system.service.impl;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.alibaba.fastjson.JSON;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.domain.CyanQuestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.mapper.CyanExamInfoMapper;
import com.cyan.exam.system.mapper.ExamStudentMapper;
import com.cyan.exam.system.mapper.ExamQuestionMapper;
import com.cyan.exam.system.mapper.CyanQuestionMapper;
import com.cyan.exam.system.service.ICyanExamInfoService;
import com.cyan.exam.system.service.ExamAccessScope;
import com.cyan.exam.common.utils.SecurityUtils;

@Service
public class CyanExamInfoServiceImpl implements ICyanExamInfoService {

    @Autowired
    private CyanExamInfoMapper cyanExamInfoMapper;

    @Autowired
    private ExamStudentMapper examStudentMapper;

    @Autowired
    private ExamQuestionMapper examQuestionMapper;
    @Autowired
    private CyanQuestionMapper questionMapper;
    @Autowired
    private ExamAccessScope accessScope;

    private CyanExamInfo lockEditable(Long examId) {
        CyanExamInfo exam = cyanExamInfoMapper.lockByExamId(examId);
        accessScope.requireAccess(exam);
        if (exam.getPublishedAt() != null || exam.getStartTime() != null
                && !new Date().before(exam.getStartTime())) {
            throw new ServiceException("考试已发布或已开始，不能修改");
        }
        return exam;
    }

    @Override
    @Transactional
    public void publish(Long examId) {
        CyanExamInfo exam = lockEditable(examId);
        Date now = new Date();
        if (!"0".equals(exam.getStatus()) || exam.getStartTime() == null
                || exam.getEndTime() == null || !exam.getEndTime().after(exam.getStartTime())
                || !exam.getStartTime().after(now)) {
            throw new ServiceException("请设置有效的未来考试时间并启用考试");
        }
        List<CyanQuestion> assigned = examQuestionMapper.selectAssignedQuestions(examId);
        List<Long> ids = assigned.stream().map(CyanQuestion::getQuestionId).collect(Collectors.toList());
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null)
                || ids.stream().distinct().count() != ids.size()) {
            throw new ServiceException("发布前请配置有效题目");
        }
        List<CyanQuestion> source = questionMapper.selectCyanQuestionByQuestionIds(ids.toArray(new Long[0]));
        if (source == null || source.size() != ids.size()) {
            throw new ServiceException("部分题目已从题库删除");
        }
        Map<Long, CyanQuestion> byId = source.stream().collect(Collectors.toMap(CyanQuestion::getQuestionId, q -> q));
        List<CyanQuestion> ordered = new ArrayList<>();
        for (CyanQuestion assignment : assigned) {
            Long id = assignment.getQuestionId();
            if (assignment.getMaxScore() == null || assignment.getMaxScore() < 1
                    || assignment.getMaxScore() > 100) {
                throw new ServiceException("题目满分必须在 1 到 100 分之间：" + id);
            }
            CyanQuestion q = byId.get(id);
            if (q == null || !"0".equals(q.getStatus()) || q.getTitle() == null
                    || q.getTitle().isBlank() || q.getQuestionType() == null
                    || q.getQuestionType().isBlank() || q.getAnswer() == null || q.getAnswer().isBlank()) {
                throw new ServiceException("题目缺失、停用或题干/答案不完整：" + id);
            }
            if ("SINGLE_CHOICE".equals(q.getQuestionType()) || "MULTI_CHOICE".equals(q.getQuestionType())
                    || "JUDGE".equals(q.getQuestionType())) {
                try {
                    List<Map> options = JSON.parseArray(q.getSubTitle(), Map.class);
                    if (options == null || options.isEmpty() || options.stream().anyMatch(option ->
                            option == null || option.get("label") == null || option.get("content") == null)) {
                        throw new ServiceException("选择题选项不完整：" + id);
                    }
                } catch (RuntimeException ex) {
                    throw new ServiceException("题目选项格式无效：" + id);
                }
            } else if ("PYTHON".equals(q.getQuestionType())) {
                com.cyan.exam.system.service.coding.CodingRules.parse(q.getAnswer(), assignment.getMaxScore());
            } else if (!"FILL_BLANK".equals(q.getQuestionType())
                    && !"SHORT_ANSWER".equals(q.getQuestionType())) {
                throw new ServiceException("题目类型不支持：" + id);
            }
            q.setMaxScore(assignment.getMaxScore());
            ordered.add(q);
        }
        examQuestionMapper.insertSnapshots(examId, ordered);
        if (cyanExamInfoMapper.publish(examId) != 1) {
            throw new ServiceException("考试发布失败");
        }
    }

    @Override
    public List<CyanExamInfo> selectCyanExamInfoList(CyanExamInfo examInfo) {
        accessScope.restrictList(examInfo);
        return cyanExamInfoMapper.selectCyanExamInfoList(examInfo);
    }

    @Override
    public CyanExamInfo selectCyanExamInfoByExamId(Long examId) {
        CyanExamInfo exam = cyanExamInfoMapper.selectCyanExamInfoByExamId(examId);
        accessScope.requireAccess(exam);
        return exam;
    }

    @Override
    @Transactional
    public int insertCyanExamInfo(CyanExamInfo examInfo) {
        examInfo.setOwnerUserId(accessScope.actorId());
        examInfo.setCreateBy(SecurityUtils.getUsername());
        examInfo.setCreateTime(new Date());
        int rows = cyanExamInfoMapper.insertCyanExamInfo(examInfo);
        // 处理学生关联
        replaceStudents(examInfo.getExamId(),
                examInfo.getStudentIds() != null ? Arrays.asList(examInfo.getStudentIds()) : null);
        // 处理试题关联
        replaceQuestions(examInfo.getExamId(),
                examInfo.getQuestionIds() != null ? Arrays.asList(examInfo.getQuestionIds()) : null);
        return rows;
    }

    @Override
    @Transactional
    public int updateCyanExamInfo(CyanExamInfo examInfo) {
        CyanExamInfo stored = lockEditable(examInfo.getExamId());
        examInfo.setOwnerUserId(stored.getOwnerUserId());
        examInfo.setUpdateBy(SecurityUtils.getUsername());
        examInfo.setUpdateTime(new Date());
        int rows = cyanExamInfoMapper.updateCyanExamInfo(examInfo);
        // 更新关联：先删后增
        if (examInfo.getStudentIds() != null) {
            replaceStudents(examInfo.getExamId(), Arrays.asList(examInfo.getStudentIds()));
        }
        if (examInfo.getQuestionIds() != null) {
            replaceQuestions(examInfo.getExamId(), Arrays.asList(examInfo.getQuestionIds()));
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteCyanExamInfoByExamIds(Long[] examIds) {
        Arrays.stream(examIds).sorted(Comparator.naturalOrder()).forEach(this::lockEditable);
        // 删除考试时也需要删除关联
        for (Long examId : examIds) {
            examStudentMapper.deleteExamStudentByExamId(examId);
            examQuestionMapper.deleteExamQuestionByExamId(examId);
        }
        return cyanExamInfoMapper.deleteCyanExamInfoByExamIds(examIds);
    }

    @Override
    public List<Long> selectStudentIdsByExamId(Long examId) {
        selectCyanExamInfoByExamId(examId);
        return examStudentMapper.selectUserIdsByExamId(examId);
    }

    @Override
    @Transactional
    public void saveExamStudents(Long examId, List<Long> userIds) {
        lockEditable(examId);
        replaceStudents(examId, userIds);
    }

    private void replaceStudents(Long examId, List<Long> userIds) {
        examStudentMapper.deleteExamStudentByExamId(examId);
        if (userIds != null && !userIds.isEmpty()) {
            examStudentMapper.batchInsertExamStudent(examId, userIds);
        }
    }

    @Override
    public List<Long> selectQuestionIdsByExamId(Long examId) {
        selectCyanExamInfoByExamId(examId);
        return examQuestionMapper.selectQuestionIdsByExamId(examId);
    }

    @Override
    public List<CyanQuestion> selectAssignedQuestions(Long examId) {
        selectCyanExamInfoByExamId(examId);
        return examQuestionMapper.selectAssignedQuestions(examId);
    }

    @Override
    @Transactional
    public void saveQuestionPoints(Long examId, Map<Long, Integer> points) {
        lockEditable(examId);
        if (points == null || points.isEmpty()) {
            throw new ServiceException("请选择题目并设置满分");
        }
        List<Long> assigned = examQuestionMapper.selectQuestionIdsByExamId(examId);
        if (assigned.size() != points.size() || !points.keySet().containsAll(assigned)) {
            throw new ServiceException("题目满分配置与已选题目不一致");
        }
        for (Map.Entry<Long, Integer> entry : points.entrySet()) {
            Integer value = entry.getValue();
            if (value == null || value < 1 || value > 100
                    || examQuestionMapper.updatePoints(examId, entry.getKey(), value) != 1) {
                throw new ServiceException("题目满分无效或题目不存在：" + entry.getKey());
            }
        }
    }

    @Override
    @Transactional
    public void saveExamQuestions(Long examId, List<Long> questionIds) {
        lockEditable(examId);
        replaceQuestions(examId, questionIds);
    }

    private void replaceQuestions(Long examId, List<Long> questionIds) {
        examQuestionMapper.deleteExamQuestionByExamId(examId);
        if (questionIds != null && !questionIds.isEmpty()) {
            examQuestionMapper.batchInsertExamQuestion(examId, questionIds);
        }
    }
}
