package com.cyan.exam.system.service.impl;

import com.cyan.exam.system.domain.CyanExamInfo;
import java.io.InputStream;
import java.util.Date;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExamOwnershipMapperTest {
    private Configuration configuration;
    private static final String NAMESPACE = "com.cyan.exam.system.mapper.CyanExamInfoMapper.";

    @BeforeEach
    void parseRealMapper() throws Exception {
        configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("CyanExamInfo", CyanExamInfo.class);
        String resource = "mapper/system/CyanExamInfoMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void teacherScopeIsBoundInListSql() {
        CyanExamInfo filter = new CyanExamInfo();
        filter.setOwnerUserId(9L);
        BoundSql query = configuration.getMappedStatement(NAMESPACE + "selectCyanExamInfoList")
                .getBoundSql(filter);
        assertTrue(query.getSql().contains("owner_user_id = ?"));
        assertEquals("ownerUserId", query.getParameterMappings().get(0).getProperty());
        assertEquals(9L, configuration.newMetaObject(query.getParameterObject()).getValue("ownerUserId"));
        assertTrue(configuration.getResultMap(NAMESPACE + "CyanExamInfoResult").getResultMappings()
                .stream().anyMatch(m -> "ownerUserId".equals(m.getProperty())
                        && "owner_user_id".equals(m.getColumn())));
    }

    @Test
    void unscopedMapperQueriesRemainAvailableForAdminAndStudentRosterFlow() {
        BoundSql query = configuration.getMappedStatement(NAMESPACE + "selectCyanExamInfoList")
                .getBoundSql(new CyanExamInfo());
        assertFalse(query.getSql().contains("owner_user_id ="));
    }

    @Test
    void insertPersistsOwnerButNormalUpdateCannotChangeIt() {
        CyanExamInfo exam = new CyanExamInfo();
        exam.setOwnerUserId(10L);
        exam.setExamName("Exam");
        exam.setUpdateTime(new Date());
        BoundSql insert = configuration.getMappedStatement(NAMESPACE + "insertCyanExamInfo").getBoundSql(exam);
        assertTrue(insert.getSql().contains("owner_user_id"));
        assertTrue(insert.getParameterMappings().stream().anyMatch(m -> "ownerUserId".equals(m.getProperty())));
        BoundSql update = configuration.getMappedStatement(NAMESPACE + "updateCyanExamInfo").getBoundSql(exam);
        assertFalse(update.getSql().contains("owner_user_id"));
        assertFalse(update.getParameterMappings().stream().anyMatch(m -> "ownerUserId".equals(m.getProperty())));
    }
}
