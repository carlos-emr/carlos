/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EncounterFormDaoImpl} table validation and SQL selection paths.
 *
 * @since 2026-11-27
 */
@Tag("unit")
@Tag("dao")
class EncounterFormDaoImplUnitTest {

    private EntityManager entityManager;
    private EncounterFormDaoImpl dao;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        dao = new EncounterFormDaoImpl();
        dao.entityManager = entityManager;
    }

    @Test
    void shouldRejectNullInvalidOrUnregisteredTable_whenQueryingHistory() {
        Query lookupQuery = mock(Query.class);
        when(entityManager.createQuery("select x from EncounterForm x where x.formTable=?1"))
                .thenReturn(lookupQuery);
        when(lookupQuery.setParameter(1, "formUnregistered")).thenReturn(lookupQuery);
        when(lookupQuery.getResultList()).thenReturn(List.of());

        assertThatThrownBy(() -> dao.findPatientHistory(null, "101", false))
                .isInstanceOf(UnknownEncounterFormTableException.class);
        assertThatThrownBy(() -> dao.findPatientHistory("1bad", "101", false))
                .isInstanceOf(UnknownEncounterFormTableException.class);
        assertThatThrownBy(() -> dao.findPatientHistory("form;drop", "101", false))
                .isInstanceOf(UnknownEncounterFormTableException.class);
        assertThatThrownBy(() -> dao.findPatientHistory("formUnregistered", "101", true))
                .isInstanceOf(UnknownEncounterFormTableException.class);
    }

    @Test
    void shouldBuildExpectedHistorySql_forXmlGroupedAndUngroupedForms() {
        Query lookupQuery = mock(Query.class);
        when(entityManager.createQuery("select x from EncounterForm x where x.formTable=?1"))
                .thenReturn(lookupQuery);
        when(lookupQuery.setParameter(1, "formCustom")).thenReturn(lookupQuery);
        when(lookupQuery.getResultList()).thenReturn(List.of(new EncounterForm()));

        Query nativeQuery = mock(Query.class);
        Date created = Date.valueOf("2026-11-20");
        Timestamp edited = Timestamp.valueOf("2026-11-21 10:15:00");
        when(entityManager.createNativeQuery(anyString(), eq(Object[].class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq(1), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{7, 101, created, edited}));

        List<EncounterFormDao.HistoryRow> xmlRows = dao.findPatientHistory("form", "101", true);
        List<EncounterFormDao.HistoryRow> groupedRows = dao.findPatientHistory("formCustom", "101", true);
        List<EncounterFormDao.HistoryRow> ungroupedRows = dao.findPatientHistory("formGrowth0_36", "101", false);

        assertThat(xmlRows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(7);
            assertThat(row.demographicNo()).isEqualTo(101);
        });
        assertThat(groupedRows).hasSize(1);
        assertThat(ungroupedRows).hasSize(1);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(3))
                .createNativeQuery(sqlCaptor.capture(), eq(Object[].class));
        assertThat(sqlCaptor.getAllValues()).containsExactly(
                "SELECT form_no, demographic_no, CAST(form_date AS DATE), CAST(form_date AS DATE) FROM form WHERE demographic_no=?1 ORDER BY form_no DESC",
                "SELECT max(ID), demographic_no, CAST(formCreated AS DATE), max(formEdited) FROM formCustom WHERE demographic_no=?1 GROUP BY date(formEdited)",
                "SELECT ID, demographic_no, CAST(formCreated AS DATE), formEdited FROM formGrowth0_36 WHERE demographic_no=?1 ORDER BY ID DESC");
    }

    @Test
    void shouldBuildExpectedLatestFormIdSql_forXmlAndPhysicalForms() {
        Query nativeQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyInt(), any())).thenReturn(nativeQuery);
        when(nativeQuery.setMaxResults(1)).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(42), List.of(), List.of(99));

        assertThat(dao.findLatestPatientFormId("form", "101", "LegacyForm", false)).isEqualTo("42");
        assertThat(dao.findLatestPatientFormId("formGrowth0_36", "101", null, true)).isEqualTo("0");
        assertThat(dao.findLatestPatientFormId("formGrowth0_36", "101", null, false)).isEqualTo("99");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(3)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues()).containsExactly(
                "SELECT form_no FROM form WHERE demographic_no=?1 AND form_name=?2 ORDER BY form_no DESC",
                "SELECT ID FROM formGrowth0_36 WHERE demographic_no=?1 ORDER BY formEdited DESC, ID DESC",
                "SELECT ID FROM formGrowth0_36 WHERE demographic_no=?1 ORDER BY ID DESC");
    }
}
