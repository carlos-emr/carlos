/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.lab.ca.on;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("lab")
class LabResultDataAcknowledgementCountUnitTest extends CarlosUnitTestBase {
    private LabResultData lab(String type) {
        createAndRegisterMock(OscarLogDao.class);
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        LabResultData result = new LabResultData(type);
        result.setSegmentID("12");
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"HL7", "CML", "MDS", "BCP"})
    void shouldRetainAndCacheVerifiedAggregateCounts_forEveryExistingSource(String type) {
        LabResultData lab = lab(type);
        try (MockedConstruction<CommonLabResultData> scope = mockConstruction(CommonLabResultData.class,
                (data, context) -> {
                    when(data.getMatchingLabs("12", type)).thenReturn("12,13");
                    when(data.getAckCount("12", type)).thenReturn(2);
                    when(data.getAckCount("13", type)).thenReturn(3);
                })) {
            assertThat(lab.getMultipleAckCount()).isEqualTo(5);
            assertThat(lab.isMultipleAckCountUnavailable()).isFalse();
            assertThat(lab.getMultipleAckCount()).isEqualTo(5);
            assertThat(scope.constructed()).hasSize(1);
            verify(scope.constructed().getFirst()).getMatchingLabs("12", type);
            verify(scope.constructed().getFirst()).getAckCount("12", type);
            verify(scope.constructed().getFirst()).getAckCount("13", type);
        }
    }

    @Test
    void shouldRetainSingleVersionDisplay_withoutInventingAnAggregate() {
        LabResultData lab = lab("HL7");
        try (var scope = mockConstruction(CommonLabResultData.class,
                (data, context) -> when(data.getMatchingLabs("12", "HL7")).thenReturn("12"))) {
            assertThat(lab.getMultipleAckCount()).isEqualTo(-1);
            assertThat(lab.isMultipleAckCountUnavailable()).isFalse();
            verify(scope.constructed().getFirst(), never()).getAckCount(anyString(), anyString());
        }
    }

    @Test
    void shouldRetainReadableRowsWithExplicitUnavailableState_whenCmlHistoryIsIncomplete() {
        LabResultData lab = lab("HL7");
        var missing = mock(Hl7textResultsData.IncompleteCmlVersionChainException.class);
        try (var scope = mockConstruction(CommonLabResultData.class,
                (data, context) -> when(data.getMatchingLabs("12", "HL7")).thenThrow(missing))) {
            assertThat(lab.isMultipleAckCountUnavailable()).isTrue();
            assertThat(lab.getMultipleAckCount()).isEqualTo(-1);
            assertThat(lab.isMultipleAckCountUnavailable()).isTrue();
            assertThat(scope.constructed()).hasSize(1);
            verify(scope.constructed().getFirst(), never()).getAckCount(anyString(), anyString());
        }
    }

    @Test
    void shouldPropagateUnexpectedChainQueryFailures_withoutCachingAFalseResult() {
        LabResultData lab = lab("HL7");
        var failure = new IllegalStateException("query failed");
        try (var scope = mockConstruction(CommonLabResultData.class,
                (data, context) -> when(data.getMatchingLabs("12", "HL7")).thenThrow(failure))) {
            assertThatThrownBy(lab::getMultipleAckCount).isSameAs(failure);
            assertThatThrownBy(lab::isMultipleAckCountUnavailable).isSameAs(failure);
            assertThat(scope.constructed()).hasSize(2);
        }
    }

    @Test
    void shouldPropagateAcknowledgementQueryFailures_afterAChainWasVerified() {
        LabResultData lab = lab("HL7");
        var failure = new IllegalStateException("acknowledgement query failed");
        try (var scope = mockConstruction(CommonLabResultData.class, (data, context) -> {
            when(data.getMatchingLabs("12", "HL7")).thenReturn("12,13");
            when(data.getAckCount("12", "HL7")).thenThrow(failure);
        })) {
            assertThatThrownBy(lab::getMultipleAckCount).isSameAs(failure);
            verify(scope.constructed().getFirst(), never()).getAckCount("13", "HL7");
        }
    }

    @Test
    void shouldResetUnavailableState_whenAnExplicitCountReplacesTheCache() {
        LabResultData lab = lab("HL7");
        try (var scope = mockConstruction(CommonLabResultData.class, (data, context) ->
                when(data.getMatchingLabs("12", "HL7")).thenThrow(
                        mock(Hl7textResultsData.IncompleteCmlVersionChainException.class)))) {
            assertThat(lab.isMultipleAckCountUnavailable()).isTrue();
            lab.setMultipleAckCount(7);
            assertThat(lab.getMultipleAckCount()).isEqualTo(7);
            assertThat(lab.isMultipleAckCountUnavailable()).isFalse();
            assertThat(scope.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldExposeTheWarningToJspBeans_withoutAddingAJsonWireProperty() throws Exception {
        assertThat(java.util.Arrays.stream(java.beans.Introspector.getBeanInfo(LabResultData.class)
                .getPropertyDescriptors()).anyMatch(p -> p.getName().equals("multipleAckCountUnavailable")))
                .isTrue();
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.getSerializationConfig().introspect(mapper.constructType(LabResultData.class))
                .findProperties()).noneMatch(p -> p.getName().equals("multipleAckCountUnavailable"));
        assertThat(java.lang.reflect.Modifier.isTransient(LabResultData.class
                .getDeclaredField("multipleAckCountUnavailable").getModifiers())).isTrue();
    }
}
