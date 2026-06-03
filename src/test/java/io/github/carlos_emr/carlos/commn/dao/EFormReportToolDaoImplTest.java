/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Collections;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormReportTool;
import io.github.carlos_emr.carlos.commn.model.EFormValue;

@Tag("unit")
@Tag("fast")
@DisplayName("EFormReportToolDaoImpl unit tests")
class EFormReportToolDaoImplTest {

    private EFormReportToolDaoImpl dao;
    private EntityManager mockEntityManager;

    @BeforeEach
    void setUp() throws Exception {
        dao = new EFormReportToolDaoImpl();
        mockEntityManager = mock(EntityManager.class);

        java.lang.reflect.Field entityManagerField = AbstractDaoImpl.class.getDeclaredField("entityManager");
        entityManagerField.setAccessible(true);
        entityManagerField.set(dao, mockEntityManager);
    }

    @Test
    @DisplayName("should accept identifier when it contains only letters digits and underscores")
    void shouldAcceptIdentifier_whenAllowedCharactersOnly() {
        assertThat(EFormReportToolDaoImpl.validateIdentifier("ERT_report_2026", "report tool table name"))
                .isEqualTo("ERT_report_2026");
    }

    @Test
    @DisplayName("should reject identifier when it contains whitespace comments or punctuation")
    void shouldRejectIdentifier_whenWhitespaceCommentsOrPunctuationPresent() {
        assertThatThrownBy(() -> EFormReportToolDaoImpl.validateIdentifier("foo /*", "report tool table name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid report tool table name");

        assertThatThrownBy(() -> EFormReportToolDaoImpl.validateIdentifier("foo;drop", "report tool table name"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EFormReportToolDaoImpl.validateIdentifier("foo bar", "report tool table name"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EFormReportToolDaoImpl.validateIdentifier("foo`bar", "report tool table name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject report tool name before creating table SQL")
    void shouldRejectReportToolName_beforeCreatingTableSql() {
        EFormReportTool reportTool = new EFormReportTool();
        reportTool.setName("foo /*");

        assertThatThrownBy(() -> dao.addNew(reportTool, new EForm(), Collections.emptyList(), "101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid report tool name");

        verifyNoInteractions(mockEntityManager);
    }

    @Test
    @DisplayName("should reject persisted table name before counting records")
    void shouldRejectPersistedTableName_beforeCountingRecords() {
        EFormReportTool reportTool = new EFormReportTool();
        reportTool.setTableName("ERT_safe /*");

        assertThatThrownBy(() -> dao.getNumRecords(reportTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid report tool table name");

        verifyNoInteractions(mockEntityManager);
    }

    @Test
    @DisplayName("should preserve legacy literal null when source provider number is null")
    void shouldPreserveLegacyLiteralNull_whenSourceProviderNumberIsNull() {
        Query mockQuery = mock(Query.class);
        when(mockEntityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        EFormReportTool reportTool = new EFormReportTool();
        reportTool.setTableName("ERT_safe");

        dao.populateReportTableItem(reportTool, Collections.singletonList(buildValue("field_one", "value-one")),
                7, 21, new Date(0L), null);

        // Provider number is bound as a parameter (?4), not concatenated into the SQL text (the
        // SQL-injection hardening from #1806). A null source provider number maps to the legacy
        // "null" sentinel so historical report rows keep a non-null value.
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockEntityManager).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).doesNotContain("'null'");
        verify(mockQuery).setParameter(4, "null");
    }

    @Test
    @DisplayName("should preserve non null provider number when populating report table")
    void shouldPreserveProviderNumber_whenSourceProviderNumberIsPresent() {
        Query mockQuery = mock(Query.class);
        when(mockEntityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        EFormReportTool reportTool = new EFormReportTool();
        reportTool.setTableName("ERT_safe");

        dao.populateReportTableItem(reportTool, Collections.singletonList(buildValue("field_one", "value-one")),
                7, 21, new Date(0L), "P12345");

        // Provider number is bound as a parameter (?4), not concatenated into the SQL text.
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockEntityManager).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).doesNotContain("'P12345'").doesNotContain("'null'");
        verify(mockQuery).setParameter(4, "P12345");
    }

    private static EFormValue buildValue(String varName, String varValue) {
        EFormValue value = new EFormValue();
        value.setVarName(varName);
        value.setVarValue(varValue);
        return value;
    }
}
