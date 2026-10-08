// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.prevention.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.prevention.PreventionSubmissionGuard;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.provider.model.PreventionManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("prevention")
class AddPrevention2ActionUnitTest extends CarlosWebTestBase {
    private MockedStatic<PreventionData> data;
    private MockedStatic<PreventionDisplayConfig> display;
    private PreventionManager manager;

    @BeforeEach void setUpPrevention() {
        allowPrivilege("_prevention", "w");
        mockRequest.setMethod("POST");
        mockRequest.getSession().setAttribute("user", "999998");
        mockRequest.setParameter("demographic_no", "42");
        mockRequest.setParameter("prevention", "Flu");
        mockRequest.setParameter("prevDate", "2026-09");
        mockRequest.setParameter("provider", "999998");
        mockRequest.setParameter("given", "given");
        DemographicDao demographic = mock(DemographicDao.class);
        when(demographic.clientExists(42)).thenReturn(true);
        replaceSpringUtilsBean(DemographicDao.class, demographic);
        manager = mock(PreventionManager.class);
        replaceSpringUtilsBean(PreventionManager.class, manager);
        try (MockedStatic<io.github.carlos_emr.carlos.utility.SpringUtils> ignored =
                mockStatic(io.github.carlos_emr.carlos.utility.SpringUtils.class)) {
            data = mockStatic(PreventionData.class);
            display = mockStatic(PreventionDisplayConfig.class);
        }
        PreventionDisplayConfig config = mock(PreventionDisplayConfig.class);
        when(config.getPrevention("Flu")).thenReturn(new HashMap<>());
        display.when(PreventionDisplayConfig::getInstance).thenReturn(config);
    }

    @AfterEach void closeStaticMocks() {
        if (display != null) display.close();
        if (data != null) data.close();
    }

    @Test void shouldReportFailedSave_withoutClosingPopupOrClearingCache() throws Exception {
        data.when(() -> PreventionData.insertPreventionData(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("synthetic failure"));
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("form");
        assertThat(mockResponse.getStatus()).isEqualTo(500);
        assertThat((List<?>) mockRequest.getAttribute("errors")).hasSize(1);
        verifyNoInteractions(manager);
    }

    @Test void shouldPreservePartialDateInput_whenSavingNewRecord() throws Exception {
        data.when(() -> PreventionData.insertPreventionData(any(), any(), eq("2026-09"), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(100);
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("success");
        verify(manager).removePrevention("42");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "bad", "-1", "2147483648", "43"})
    void shouldRejectInvalidPatient_withoutWriting(String patient) throws Exception {
        mockRequest.setParameter("demographic_no", patient);
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        data.verifyNoInteractions();
        verifyNoInteractions(manager);
    }

    @Test void shouldRejectUnrelatedRecord_beforeDelete() throws Exception {
        mockRequest.setParameter("id", "100");
        mockRequest.setParameter("delete", "true");
        data.when(() -> PreventionData.requirePreventionInChart(100, 42))
                .thenThrow(new IllegalArgumentException("unrelated patient"));
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        data.verify(() -> PreventionData.deletePreventionData(any(), any()), never());
        verifyNoInteractions(manager);
    }

    @Test void shouldDeleteOnlyValidatedChartRecord_whenRequestIsValid() throws Exception {
        mockRequest.setParameter("id", "100");
        mockRequest.setParameter("delete", "true");
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("success");
        data.verify(() -> PreventionData.deletePreventionData("100", "42"));
        verify(manager).removePrevention("42");
    }
    @Test void shouldRejectMissingInteractiveDate_withoutWriting() throws Exception {
        mockRequest.removeParameter("prevDate");
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        data.verifyNoInteractions();
    }

    @Test void shouldReportInvalidClinicalDate_withoutClearingCache() throws Exception {
        data.when(() -> PreventionData.insertPreventionData(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenThrow(new IllegalArgumentException("invalid date"));
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("form");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(manager);
    }

    @Test void shouldPreservePreviousRecordLink_whenUpdating() throws Exception {
        mockRequest.setParameter("id", "100");
        data.when(() -> PreventionData.updatetPreventionData(eq("100"), eq("999998"), eq("42"), eq("2026-09"),
                any(), any(), any(), any(), any(), any(), argThat(extra -> extra.stream()
                    .anyMatch(entry -> "100".equals(entry.get("previousId")))), any())).thenReturn(101);
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("success");
        verify(manager).removePrevention("42");
    }

    @Test void shouldRejectAnUnpersistedResult_withoutReportingSuccess() throws Exception {
        data.when(() -> PreventionData.insertPreventionData(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(-1);
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("form");
        assertThat(mockResponse.getStatus()).isEqualTo(500);
        verifyNoInteractions(manager);
    }

    // The submission tests run outside the base class's test transaction, as the action does in
    // production: its save then commits or rolls back on its own and the claim sees the outcome
    // (PreventionData itself is mocked and writes nothing). Inside the test transaction the outcome
    // is unknown and the claim, correctly, never frees the token.
    private String issueToken(String record) {
        String token = PreventionSubmissionGuard.issue(mockRequest.getSession(), "42", record);
        mockRequest.setParameter(PreventionSubmissionGuard.PARAMETER, token);
        return token;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldSaveOnce_whenTheSameRenderedFormIsSubmittedTwice() throws Exception {
        issueToken(null);
        data.when(() -> PreventionData.insertPreventionData(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(100);

        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("success");
        // The repeat closes the popup as the first save did, and writes nothing.
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("success");

        data.verify(() -> PreventionData.insertPreventionData(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()), times(1));
        verify(manager, times(1)).removePrevention("42");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldLetTheRepeatSave_whenTheFirstSaveRolledBack() throws Exception {
        issueToken(null);
        data.when(() -> PreventionData.insertPreventionData(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("synthetic failure"))
                .thenReturn(100);

        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("form");
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("success");

        data.verify(() -> PreventionData.insertPreventionData(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()), times(2));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldDeleteOnce_whenTheDeleteIsSubmittedTwice() throws Exception {
        mockRequest.setParameter("id", "100");
        mockRequest.setParameter("delete", "true");
        issueToken("100");

        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("success");
        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("success");

        data.verify(() -> PreventionData.deletePreventionData("100", "42"), times(1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRefuseWithoutWriting_whenTheTokenWasIssuedForAnotherRecord() throws Exception {
        issueToken("100");

        assertThat(executeAction(new AddPrevention2Action())).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(409);
        assertThat(mockResponse.getContentType()).startsWith("text/plain");
        assertThat(mockResponse.getContentAsString()).contains("Nothing was saved");
        data.verify(() -> PreventionData.insertPreventionData(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()), never());
        verifyNoInteractions(manager);
    }

}
