/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.DateSearchType;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Filter;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Operation;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.PatientCount;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the inbox count SQL shape to the date mode carried by the filter, and the DAO's layering:
 * no other DAO injected and no dependency on the inbox web model (issue #5772).
 * @since 2026-11-06
 */
@Tag("dao")
class InboxCategoryDaoUnitTest extends CarlosUnitTestBase {
    private final EntityManager em = mock(EntityManager.class);
    private final Query query = mock(Query.class);

    private InboxCategoryDao dao() {
        when(em.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        var dao = new InboxCategoryDao();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        return dao;
    }

    private static Filter filter(String startDate, String endDate, DateSearchType mode) {
        return new Filter("", "", "", false, false, "", "", "", startDate, endDate, mode);
    }

    private String onlyStatement() {
        var sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture(), eq(Tuple.class));
        return sql.getValue();
    }

    @ParameterizedTest
    @CsvSource({"SERVICE_OBSERVATION,2026-1-5", "RECEIVED_CREATED,2026-1-5",
            "SERVICE_OBSERVATION,2026-01-05", "RECEIVED_CREATED,2026-01-05"})
    void shouldNormalizeBothCalendarBounds_whenInboxDatesAreNotPadded(DateSearchType mode, String date) throws Exception {
        assertThat(dao().count(filter(date, date, mode), Operation.LAB_UNMATCHED, null).total()).isZero();
        verify(query).setParameter(1, "2026-01-05");
        verify(query).setParameter(2, "2026-01-06");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-2-30", "2026-13-1", "2026-0-1", "2026-1-0", "invalid"})
    void shouldRefuseImpossibleDates_beforeQuerying(String date) {
        var dao = new InboxCategoryDao();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        assertThatThrownBy(() -> dao.count(filter(date, date, DateSearchType.SERVICE_OBSERVATION),
                Operation.LAB_UNMATCHED, null))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
        verifyNoInteractions(em);
    }

    @Test
    void shouldJoinReceivedDates_whenFilterSelectsReceivedCreated() throws Exception {
        assertThat(dao().count(filter("2026-01-01", "", DateSearchType.RECEIVED_CREATED),
                Operation.LAB_UNMATCHED, null).total()).isZero();

        assertThat(onlyStatement()).contains("JOIN hl7TextMessage message", "message.created >= ?")
                .doesNotContain("info.obr_date >= ?");
        verify(query).setParameter(1, "2026-01-01");
    }

    @Test
    void shouldCompareObservationDates_whenFilterSelectsServiceObservation() throws Exception {
        assertThat(dao().count(filter("2026-01-01", "", DateSearchType.SERVICE_OBSERVATION),
                Operation.LAB_UNMATCHED, null).total()).isZero();

        assertThat(onlyStatement()).contains("info.obr_date >= ?").doesNotContain("hl7TextMessage");
        verify(query).setParameter(1, "2026-01-01");
    }

    @ParameterizedTest
    @CsvSource({"RECEIVED_CREATED,doc.contentdatetime,doc.observationdate",
            "SERVICE_OBSERVATION,doc.observationdate,doc.contentdatetime"})
    void shouldCompareModeDocumentDates_whenCountingUnmatchedDocuments(DateSearchType mode, String used, String unused)
            throws Exception {
        dao().count(filter("2026-01-01", "2026-01-31", mode), Operation.DOCUMENT_UNMATCHED, null);

        assertThat(onlyStatement()).contains(used + " >= ?", used + " <= ?").doesNotContain(unused);
        verify(query).setParameter(1, "2026-01-01 00:00:00");
        verify(query).setParameter(2, "2026-01-31 23:59:59");
    }

    @Test
    void shouldRejectFilter_withoutDateSearchType() {
        assertThatThrownBy(() -> filter("", "", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dateSearchType");
    }

    @ParameterizedTest
    @CsvSource(value = {"receivedCreated,RECEIVED_CREATED", "serviceObservation,SERVICE_OBSERVATION",
            "NULL,SERVICE_OBSERVATION", "'',SERVICE_OBSERVATION", "ReceivedCreated,SERVICE_OBSERVATION",
            "' receivedCreated',SERVICE_OBSERVATION", "obrDate,SERVICE_OBSERVATION"}, nullValues = "NULL")
    void shouldMapPreferenceValue_asTheInboxResultListsDo(String value, DateSearchType expected) {
        assertThat(DateSearchType.fromPreference(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(DateSearchType.class)
    void shouldRoundTripPreferenceValue_forEveryMode(DateSearchType mode) {
        assertThat(DateSearchType.fromPreference(mode.preferenceValue())).isSameAs(mode);
    }

    @Test
    void shouldReturnOneSummaryPerRow_withTheCountInTheOperationsColumn() throws Exception {
        var dao = dao();
        Tuple lab = row(7, "Lab", 3);
        Tuple document = row(8, "Document", 2);
        Tuple hrm = row(9, "Hrm", 4);
        when(hrm.get("demographic_no", Integer.class)).thenReturn(9);
        var patientSearch = new Filter("Synthetic", "", "", true, false, "", "N", "all", null, null,
                DateSearchType.SERVICE_OBSERVATION);

        when(query.getResultList()).thenReturn(List.of(lab));
        var labs = dao.count(patientSearch, Operation.LAB_PATIENT, null);
        when(query.getResultList()).thenReturn(List.of(document));
        var documents = dao.count(patientSearch, Operation.DOCUMENT_PATIENT, null);
        when(query.getResultList()).thenReturn(List.of(hrm));
        var reports = dao.count(patientSearch, Operation.HRM_PATIENT, null);

        assertThat(labs.total()).isEqualTo(3);
        assertThat(labs.patients()).containsExactly(new PatientCount(7, "Synthetic", "Lab", 3, 0, 0));
        assertThat(documents.total()).isEqualTo(2);
        assertThat(documents.patients()).containsExactly(new PatientCount(8, "Synthetic", "Document", 0, 2, 0));
        // An HRM report is also a document in the sidebar; CategoryData adds the document figure.
        assertThat(reports.total()).isEqualTo(4);
        assertThat(reports.patients()).containsExactly(new PatientCount(9, "Synthetic", "Hrm", 0, 4, 4));
    }

    private static Tuple row(int demographicNo, String lastName, long count) {
        Tuple row = mock(Tuple.class);
        when(row.get("demographic_no")).thenReturn(demographicNo);
        when(row.get("count")).thenReturn(count);
        when(row.get("first_name", String.class)).thenReturn("Synthetic");
        when(row.get("last_name", String.class)).thenReturn(lastName);
        return row;
    }

    @Test
    void shouldInjectNoOtherDao_forLayeringContract() {
        // CLAUDE.md: DAOs may not inject other DAOs; the date preference is resolved by a service.
        assertThat(InboxCategoryDao.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(constructor.getParameterCount()).isZero());
        assertThat(Arrays.stream(InboxCategoryDao.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType().getName()))
                .containsExactly(EntityManager.class.getName());
    }

    @Test
    void shouldKeepCommnDaoFreeOfInboxWebModel_forPackageCycleContract() throws Exception {
        // mds.data.CategoryData depends on commn.dao.InboxCategoryDao; a DAO importing mds.data
        // (PatientInfo) closed a package cycle. The DAO returns PatientCount records instead.
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java/io/github/carlos_emr/carlos/commn/dao"))) {
            assertThat(sources.filter(path -> path.toString().endsWith(".java")))
                    .isNotEmpty()
                    .allSatisfy(path -> assertThat(Files.readString(path)).describedAs(path.toString())
                            .doesNotContain("io.github.carlos_emr.carlos.mds.data."));
        }
    }
}
