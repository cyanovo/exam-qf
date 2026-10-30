package com.cyan.exam.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.cyan.exam.common.annotation.Excel;
import com.cyan.exam.common.core.domain.BaseEntity;

/**
 * 题库对象 cyan_question
 * 
 * @author cyan
 * @date 2026-05-26
 */
public class CyanQuestion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 题目ID */
    private Long questionId;

    /** 题目分类（JAVA / PYTHON / C / HTML） */
    @Excel(name = "题目分类")
    private String category;

    /** 难易程度（1-5，1最简单，5最难） */
    @Excel(name = "难易程度")
    private Integer difficulty;

    /** 题目类型（SINGLE_CHOICE / MULTI_CHOICE / JUDGE / FILL_BLANK / SHORT_ANSWER） */
    @Excel(name = "题目类型", readConverterExp = "SINGLE_CHOICE=单选题,MULTI_CHOICE=多选题,JUDGE=判断题,FILL_BLANK=填空题,SHORT_ANSWER=简答题")

    private String questionType;

    /** 题干（支持富文本、图片） */
    @Excel(name = "题干")
    private String title;

    /** 副题干（选择题存放选项内容，JSON格式；非选择题可空） */
    @Excel(name = "副题干")
    private String subTitle;

    /** 答案（选择题：选项标签，如 A 或 A,B；判断题：对/错；填空题：关键答案，用分号分隔；简答题：参考答案文本） */
    @Excel(name = "答案")
    private String answer;
    private String gradingRules;
    private String initialCode;
    private String reviewState;
    private Integer revision;
    public String getReviewState() { return reviewState; }
    public void setReviewState(String reviewState) { this.reviewState = reviewState; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public String getInitialCode() { return initialCode; }
    public void setInitialCode(String initialCode) { this.initialCode = initialCode; }
    public String getGradingRules() { return gradingRules; }
    public void setGradingRules(String gradingRules) { this.gradingRules = gradingRules; }

    /** 状态（0启用 1停用） */
    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;
    private Integer maxScore;
    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }

    public void setQuestionId(Long questionId) 
    {
        this.questionId = questionId;
    }

    public Long getQuestionId() 
    {
        return questionId;
    }

    public void setCategory(String category) 
    {
        this.category = category;
    }

    public String getCategory() 
    {
        return category;
    }

    public void setDifficulty(Integer difficulty)
    {
        this.difficulty = difficulty;
    }

    public Integer getDifficulty()
    {
        return difficulty;
    }

    public void setQuestionType(String questionType) 
    {
        this.questionType = questionType;
    }

    public String getQuestionType() 
    {
        return questionType;
    }

    public void setTitle(String title) 
    {
        this.title = title;
    }

    public String getTitle() 
    {
        return title;
    }

    public void setSubTitle(String subTitle) 
    {
        this.subTitle = subTitle;
    }

    public String getSubTitle() 
    {
        return subTitle;
    }

    public void setAnswer(String answer) 
    {
        this.answer = answer;
    }

    public String getAnswer() 
    {
        return answer;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("questionId", getQuestionId())
            .append("category", getCategory())
            .append("difficulty", getDifficulty())
            .append("questionType", getQuestionType())
            .append("title", getTitle())
            .append("subTitle", getSubTitle())
            .append("answer", getAnswer())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .append("status", getStatus())
            .toString();
    }
}
