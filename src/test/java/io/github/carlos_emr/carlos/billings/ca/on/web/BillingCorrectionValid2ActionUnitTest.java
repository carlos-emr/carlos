/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCorrectionReviewViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionValidationCommand;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionReviewPreparationService;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewDraft;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingCorrectionValid2Action} request parsing and correction-review handoff. */
@DisplayName("BillingCorrectionValid2Action")
@Tag("unit")
@Tag("billing")
class BillingCorrectionValid2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private BillingCorrectionReviewPreparationService preparationService;
    @Mock private BillingCorrectionReviewViewModelAssembler reviewViewModelAssembler;
    @Mock private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldForwardReviewModel_withoutLegacySessionCarrierBeans() throws Exception {
        request.setParameter("xml_billing_no", "42");
        request.setParameter("dob", "1980-01-01");
        request.setParameter("xml_vdate", "2026-04-28");
        request.setParameter("xml_appointment_date", "2026-04-28");
        request.setParameter("update_date", "2026-04-29");
        request.setParameter("servicecode0", "A001A");
        request.setParameter("billingunit0", "1");

        BillingCorrectionReviewViewModel model = BillingCorrectionReviewViewModel.builder()
                .dataLoaded(true)
                .billingNo("42")
                .build();
        BillingCorrectionReviewDraft draft = draft();
        when(preparationService.prepareReviewDraft(any())).thenReturn(draft);
        when(reviewViewModelAssembler.assemble(draft)).thenReturn(model);

        String result = new BillingCorrectionValid2Action(
                securityInfoManager, preparationService, reviewViewModelAssembler).execute();

        assertThat(result).isEqualTo("review");
        assertThat(request.getAttribute("reviewModel")).isSameAs(model);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getSession().getAttribute("billing")).isNull();
        assertThat(request.getSession().getAttribute("billingDataBean")).isNull();
        assertThat(request.getSession().getAttribute("billingPatientDataBean")).isNull();
        verify(preparationService).prepareReviewDraft(any());
        verify(reviewViewModelAssembler).assemble(draft);
    }

    @Test
    void shouldReturnError_whenReviewPreparationRejectsInput() throws Exception {
        when(preparationService.prepareReviewDraft(any()))
                .thenThrow(new BillingValidationException("Invalid coded field."));

        String result = new BillingCorrectionValid2Action(
                securityInfoManager, preparationService, reviewViewModelAssembler).execute();

        assertThat(result).isEqualTo("error");
        assertThat(request.getAttribute("correctionError")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("correctionErrorMessage")).isEqualTo("Invalid coded field.");
        verify(reviewViewModelAssembler, never()).assemble(any());
    }

    @Test
    void shouldDefensivelyCopyBillingItems_whenReviewViewModelIsBuilt() {
        java.util.List<BillingCorrectionReviewViewModel.Item> items =
                new java.util.ArrayList<>();
        items.add(new BillingCorrectionReviewViewModel.Item(
                "A001A", "Minor assessment", "1", "10.00", "250"));

        BillingCorrectionReviewViewModel model = BillingCorrectionReviewViewModel.builder()
                .billingItems(items)
                .build();
        items.clear();

        assertThat(model.getBillingItems()).hasSize(1);
        assertThatThrownBy(() -> model.getBillingItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldBindDob_fromXmlDobPostedByCorrectionForm() throws Exception {
        request.setParameter("xml_billing_no", "42");
        request.setParameter("xml_dob", "19800101");
        request.setParameter("xml_vdate", "20260428");
        request.setParameter("xml_appointment_date", "20260429");
        request.setParameter("update_date", "20260430");
        request.setParameter("servicecode0", "A001A");
        request.setParameter("billingunit0", "1");

        when(preparationService.prepareReviewDraft(any())).thenReturn(draft());

        new BillingCorrectionValid2Action(
                securityInfoManager, preparationService, reviewViewModelAssembler).execute();

        ArgumentCaptor<BillingCorrectionValidationCommand> captor =
                forClass(BillingCorrectionValidationCommand.class);
        verify(preparationService).prepareReviewDraft(captor.capture());
        assertThat(captor.getValue().dobText()).isEqualTo("1980-01-01");
        assertThat(captor.getValue().visitDateText()).isEqualTo("2026-04-28");
        assertThat(captor.getValue().billingDateText()).isEqualTo("2026-04-29");
        assertThat(captor.getValue().updateDateText()).isEqualTo("2026-04-30");
    }

    @Test
    void shouldCollectOnlyXmlParameters_whenRequestContainsXmlSubstring() throws Exception {
        request.setParameter("xml_billing_no", "42");
        request.setParameter("xml_safe", "safe");
        request.setParameter("prefix_xml_bad", "bad");
        request.setParameter("not_xml_bad", "bad");
        request.setParameter("servicecode0", "A001A");
        request.setParameter("billingunit0", "1");

        when(preparationService.prepareReviewDraft(any())).thenReturn(draft());

        new BillingCorrectionValid2Action(
                securityInfoManager, preparationService, reviewViewModelAssembler).execute();

        ArgumentCaptor<BillingCorrectionValidationCommand> captor =
                forClass(BillingCorrectionValidationCommand.class);
        verify(preparationService).prepareReviewDraft(captor.capture());
        assertThat(captor.getValue().xmlParameters())
                .containsEntry("xml_billing_no", "42")
                .containsEntry("xml_safe", "safe")
                .doesNotContainKeys("prefix_xml_bad", "not_xml_bad");
    }

    @Test
    void shouldOrderServiceLinesByNumericSuffix_whenRequestEnumeratesParametersOutOfOrder() throws Exception {
        request.setParameter("xml_billing_no", "42");
        request.setParameter("xml_dob", "19800101");
        request.setParameter("servicecode2", "C003A");
        request.setParameter("billingunit2", "3");
        request.setParameter("billingamount2", "30.00");
        request.setParameter("servicecode0", "A001A");
        request.setParameter("billingunit0", "1");
        request.setParameter("billingamount0", "10.00");
        request.setParameter("servicecode1", "");
        request.setParameter("billingunit1", "");
        request.setParameter("billingamount1", "");

        when(preparationService.prepareReviewDraft(any())).thenReturn(draft());

        new BillingCorrectionValid2Action(
                securityInfoManager, preparationService, reviewViewModelAssembler).execute();

        ArgumentCaptor<BillingCorrectionValidationCommand> captor =
                forClass(BillingCorrectionValidationCommand.class);
        verify(preparationService).prepareReviewDraft(captor.capture());
        assertThat(captor.getValue().serviceLines())
                .extracting("serviceCode")
                .containsExactly("A001A", "C003A");
    }

    private static BillingCorrectionReviewDraft draft() {
        return new BillingCorrectionReviewDraft(
                true,
                "",
                "42",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "0",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                List.of());
    }
}
