package com.cyan.exam.system.service.impl;

import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.domain.CyanQuestion;
import com.cyan.exam.system.mapper.CyanExamInfoMapper;
import com.cyan.exam.system.mapper.ExamAnswerRecordMapper;
import com.cyan.exam.system.mapper.ExamQuestionMapper;
import com.cyan.exam.system.mapper.SysUserMapper;
import com.cyan.exam.system.mapper.ExamStudentMapper;
import com.cyan.exam.system.mapper.ExamSessionMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamGradingSnapshotTest extends AuthenticatedExamTest {
    @org.mockito.Spy private com.cyan.exam.system.service.ExamAccessScope accessScope;
    @InjectMocks private ExamGradingServiceImpl service;
    @Mock private com.cyan.exam.system.service.ExamResultService resultService;
    @Mock private CyanExamInfoMapper infoMapper;
    @Mock private ExamQuestionMapper questionMapper;
    @Mock private ExamAnswerRecordMapper answerMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ExamStudentMapper studentMapper;
    @Mock private ExamSessionMapper sessionMapper;

    @Test
    void gradingUsesPublishedSnapshotAnswers() {
        CyanExamInfo exam = new CyanExamInfo();
        exam.setOwnerUserId(9L);
        exam.setExamName("Exam");
        exam.setPublishedAt(new java.util.Date());
        when(infoMapper.selectCyanExamInfoByExamId(1L)).thenReturn(exam);
        when(studentMapper.selectUserIdsByExamId(1L)).thenReturn(List.of(2L));
        CyanQuestion snapshot = new CyanQuestion();
        snapshot.setQuestionId(3L);
        snapshot.setTitle("Published version");
        snapshot.setQuestionType("FILL_BLANK");
        snapshot.setAnswer("Frozen answer");
        snapshot.setMaxScore(10);
        when(questionMapper.selectSnapshots(1L)).thenReturn(List.of(snapshot));
        when(answerMapper.selectByExamAndUser(1L, 2L)).thenReturn(List.of());
        Map<String, Object> detail = service.getStudentAnswerDetail(1L, 2L);
        @SuppressWarnings("unchecked")
        Map<String, Object> question = ((List<Map<String, Object>>) detail.get("questions")).get(0);
        assertEquals("Published version", question.get("title"));
        assertEquals("Frozen answer", question.get("referenceAnswer"));
        assertEquals(10, question.get("maxScore"));
        verify(questionMapper).selectSnapshots(1L);
    }
}
