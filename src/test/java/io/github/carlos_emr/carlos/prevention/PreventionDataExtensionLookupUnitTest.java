/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prevention;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.managers.DHIRSubmissionManager;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.Date;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Isolated("PreventionData retains static DAO references")
@DisplayName("RH injection extension lookups preserve clinical data without logging it")
class PreventionDataExtensionLookupUnitTest extends CarlosUnitTestBase {
    private static final String CLINICAL_MARKER = "SYNTHETIC-CLINICAL-LOT-REASON";
    private final PreventionDao preventionDao = mock(PreventionDao.class);
    private final PreventionExtDao extensionDao = mock(PreventionExtDao.class);
    private final PartialDateDao partialDateDao = mock(PartialDateDao.class);
    private Object originalPreventionDao;
    private Object originalExtensionDao;
    private Object originalPartialDateDao;

    @BeforeEach
    void setUpLookups() {
        registerMock(PreventionDao.class, preventionDao);
        registerMock(PreventionExtDao.class, extensionDao);
        registerMock(PartialDateDao.class, partialDateDao);
        DHIRSubmissionManager dhir = createAndRegisterMock(DHIRSubmissionManager.class);
        when(dhir.findByPreventionId(anyInt())).thenReturn(List.of());
        originalPreventionDao = ReflectionTestUtils.getField(PreventionData.class, "preventionDao");
        originalExtensionDao =
                ReflectionTestUtils.getField(PreventionData.class, "preventionExtDao");
        originalPartialDateDao =
                ReflectionTestUtils.getField(PreventionData.class, "partialDateDao");
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", preventionDao);
        ReflectionTestUtils.setField(PreventionData.class, "preventionExtDao", extensionDao);
        ReflectionTestUtils.setField(PreventionData.class, "partialDateDao", partialDateDao);
    }

    @AfterEach
    void restoreLookups() {
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", originalPreventionDao);
        ReflectionTestUtils.setField(
                PreventionData.class, "preventionExtDao", originalExtensionDao);
        ReflectionTestUtils.setField(
                PreventionData.class, "partialDateDao", originalPartialDateDao);
    }

    @Test
    void shouldReturnLegacyFields_whenThePreventionHasNoDemographicAssociation() {
        var prevention = injection(41);
        prevention.setDemographicId(null);
        when(preventionDao.find(Integer.valueOf(41))).thenReturn(prevention);
        try (MockedStatic<ProviderData> providers = mockStatic(ProviderData.class)) {
            assertThat(PreventionData.getPreventionById("41"))
                    .containsEntry("id", "41")
                    .doesNotContainKey("demographicNo");
        }
    }

    @Test
    void shouldRefuseASourceFailure_insteadOfReturningTheAlreadyReadClinicalRows() {
        when(extensionDao.findByKeyAndValue("workflowId", "17"))
                .thenReturn(List.of(link(41), link(42)));
        when(preventionDao.find(Integer.valueOf(41))).thenReturn(injection(41));
        when(preventionDao.find(Integer.valueOf(42)))
                .thenThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                CLINICAL_MARKER));
        try (MockedStatic<ProviderData> providers = mockStatic(ProviderData.class);
                LogCapture logs = LogCapture.forLogger(PreventionData.class)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> PreventionData.getPreventionDataFromExt("workflowId", "17"))
                    .isExactlyInstanceOf(
                            io.github.carlos_emr.carlos.prevention.dto
                                    .PreventionSourceUnavailableException.class)
                    .hasCauseInstanceOf(
                            org.springframework.dao.DataAccessResourceFailureException.class)
                    // The exception's own text is fixed: it never repeats the failure's.
                    .hasMessageNotContaining(CLINICAL_MARKER);
            assertThat(logs.messages()).noneMatch(message -> message.contains(CLINICAL_MARKER));
            // The record read failed inside the extension lookup: reported once, not once per reader.
            assertThat(logs.messages())
                    .filteredOn(message -> message.contains("PREVENTION_SOURCE_UNAVAILABLE"))
                    .hasSize(1);
        }
    }

    @Test
    void shouldLogOneSanitizedLine_whenACallerShowsTheReadAsUnreadable() {
        try (LogCapture logs = LogCapture.forLogger(PreventionData.class)) {
            PreventionData.logUnreadable("flowsheet\r\nforged entry", "7\nforged entry");

            assertThat(logs.messages()).hasSize(1);
            assertThat(logs.messages().get(0))
                    .startsWith("PREVENTION_SOURCE_UNAVAILABLE shown as unreadable by flowsheet")
                    .contains("demographicNo=7")
                    .doesNotContain("\r", "\n");
        }
    }

    @Test
    void shouldPassTheDateReviewRefusalThrough_insteadOfReportingAnUnavailableSource() {
        when(extensionDao.findByKeyAndValue("workflowId", "17")).thenReturn(List.of(link(41)));
        when(preventionDao.find(Integer.valueOf(41))).thenReturn(injection(41));
        // A partial_date tag no reader understands makes the injection's date need review.
        when(partialDateDao.getFormat(anyInt(), eq(41), anyInt())).thenReturn("unrecognised");
        try (MockedStatic<ProviderData> providers = mockStatic(ProviderData.class)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> PreventionData.getPreventionDataFromExt("workflowId", "17"))
                    .isExactlyInstanceOf(
                            io.github.carlos_emr.carlos.prevention.dto
                                    .PreventionDatePrecisionException.class);
        }
    }

    @Test
    void shouldReturnLinkedInjectionFields_withoutParsingOrLoggingClinicalEntityText() {
        Prevention injection = injection(41);
        when(extensionDao.findByKeyAndValue("workflowId", "17")).thenReturn(List.of(link(41)));
        when(preventionDao.find(Integer.valueOf(41))).thenReturn(injection);
        when(extensionDao.findByPreventionId(41))
                .thenReturn(
                        List.of(
                                field(41, "lot", CLINICAL_MARKER), field(41, "dose", "300"),
                                field(41, "reason", "antepartum"),
                                        field(41, "name", "synthetic product")));

        // Diagnostic text is deliberately nonnumeric and must never be parsed as an identifier.
        assertThat(injection.toString())
                .isEqualTo("Prevention[id=41]")
                .doesNotContain(CLINICAL_MARKER);
        injection.setPreventionExts(new org.hibernate.collection.spi.PersistentBag<>());
        try (MockedStatic<ProviderData> providers = mockStatic(ProviderData.class);
                LogCapture logs = LogCapture.forLogger(PreventionData.class)) {
            assertThat(PreventionData.getPreventionDataFromExt("workflowId", "17"))
                    .singleElement()
                    .satisfies(
                            row -> {
                                assertThat(row)
                                        .containsEntry("id", "41")
                                        .containsEntry("demographicNo", "7")
                                        .containsEntry("lot", CLINICAL_MARKER)
                                        .containsEntry("dose", "300")
                                        .containsEntry("name", "synthetic product");
                                assertThat(row.get("summary")).asString().contains(CLINICAL_MARKER);
                            });
            assertThat(logs.events())
                    .noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.WARN));
            assertThat(logs.messages()).noneMatch(message -> message.contains(CLINICAL_MARKER));
        }
        verify(preventionDao, times(1)).find(Integer.valueOf(41));
    }

    @Test
    void shouldSkipMissingAndDeletedPreventions_withoutHidingLaterLiveInjectionOrLoggingErrors() {
        when(extensionDao.findByKeyAndValue("workflowId", "17"))
                .thenReturn(List.of(link(null), link(40), link(41), link(42)));
        Prevention deleted = injection(41);
        deleted.setDeleted(true);
        when(preventionDao.find(Integer.valueOf(41))).thenReturn(deleted);
        when(preventionDao.find(Integer.valueOf(42))).thenReturn(injection(42));

        try (MockedStatic<ProviderData> providers = mockStatic(ProviderData.class);
                LogCapture logs = LogCapture.forLogger(PreventionData.class)) {
            assertThat(PreventionData.getPreventionDataFromExt("workflowId", "17"))
                    .singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("id", "42"));
            assertThat(logs.events())
                    .noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.WARN));
            assertThat(logs.messages()).noneMatch(message -> message.contains(CLINICAL_MARKER));
        }
        verify(preventionDao, never()).find(null);
    }

    private static Prevention injection(int id) {
        Prevention prevention = new Prevention();
        ReflectionTestUtils.setField(prevention, "id", id);
        prevention.setDemographicId(7);
        prevention.setProviderNo("-1");
        prevention.setCreatorProviderNo("999998");
        prevention.setPreventionType("Rh");
        prevention.setPreventionDate(new Date(0));
        prevention.setLastUpdateDate(new Date(0));
        prevention.setSnomedId(CLINICAL_MARKER);
        return prevention;
    }

    private static PreventionExt link(Integer id) {
        return field(id, "workflowId", "17");
    }

    private static PreventionExt field(Integer id, String key, String value) {
        PreventionExt extension = new PreventionExt();
        extension.setPreventionId(id);
        extension.setKeyval(key);
        extension.setVal(value);
        return extension;
    }
}
