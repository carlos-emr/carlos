/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService.Operation;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
class EFormSavedContinuationActionUnitTest {
    @TempDir Path temporary;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DocumentAttachmentManager documents = mock(DocumentAttachmentManager.class);
    private final EFormDataDao forms = mock(EFormDataDao.class);
    private final DemographicManager patients = mock(DemographicManager.class);
    private final EFormRenderApprovalService approvals = new EFormRenderApprovalService();
    private final AtomicInteger renders = new AtomicInteger();
    private final AtomicInteger persisted = new AtomicInteger();
    private final EFormRenderCompletenessReport omissions = new EFormRenderCompletenessReport(2, 0, 0, 0, false, false, false, false);

    @BeforeEach
    void setUp() {
        request.setMethod("POST");
        request.getSession();
        request.setParameter("fdid", "42");
        request.setParameter("demographicNo", "123");
        request.setParameter("autoClose", "true");
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        EFormData saved = new EFormData();
        saved.setDemographicId(123);
        when(forms.find(42)).thenReturn(saved);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
    }

    @AfterEach
    void close() { login.close(); servlet.close(); }

    private String execute(Operation operation) {
        return operation == Operation.EDOC
                ? new SaveEFormAsEDoc2Action(security, documents, approvals, forms).execute()
                : new DownloadEFormPdf2Action(security, documents, approvals, patients, forms).execute();
    }

    private void ticket(Operation operation) {
        request.setParameter("renderApproval", approvals.issueCapacityContinuation(request, user, 42, "123", operation));
    }

    @SuppressWarnings("unchecked")
    private void continueWaiting() {
        Map<String, String> fields = (Map<String, String>) request.getAttribute("renderCapacityFields");
        assertThat(fields).containsOnlyKeys("fdid", "demographicNo", "renderApproval", "autoClose");
        fields.forEach(request::setParameter);
        response.reset();
    }

    private void renderBehavior(Operation operation, boolean incompleteAfterBusy) throws Exception {
        org.mockito.stubbing.Answer<Object> rendering = invocation -> {
            int attempt = renders.incrementAndGet();
            if (attempt <= 2) throw new PDFGenerationException("capacity", true);
            EFormRenderApproval approval = invocation.getArgument(2);
            if (incompleteAfterBusy && !approval.permits(42, "999998", omissions)) {
                throw new EformContentUnavailableException("incomplete", 42, omissions);
            }
            if (operation == Operation.EDOC) { persisted.incrementAndGet(); return 7; }
            return new EformDataManager.EformPdfRender(Files.writeString(temporary.resolve("rendered.pdf"), "PDF bytes"),
                    EFormRenderCompletenessReport.complete());
        };
        if (operation == Operation.EDOC) when(documents.saveEFormAsEDoc(any(), any(), any())).thenAnswer(rendering);
        else {
            when(documents.renderEFormPacketWithCompleteness(any(), any(), any())).thenAnswer(rendering);
            when(documents.convertPDFToBase64(any())).thenReturn("UERG");
        }
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"EDOC", "DOWNLOAD"})
    void repeatedBusyWaitsThenCompletesOnceAndRejectsConsumedToken(Operation operation) throws Exception {
        ticket(operation);
        renderBehavior(operation, false);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThat(execute(operation)).isEqualTo("renderBusy");
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(persisted).hasValue(0);
            assertThat(request.getAttribute("renderCapacityAction")).isEqualTo(operation == Operation.EDOC
                    ? "/eform/saveEFormAsEDoc" : "/eform/downloadEFormPdf");
            continueWaiting();
        }
        assertThat(execute(operation)).isEqualTo(operation == Operation.EDOC ? "close" : "download");
        assertThat(request.getAttribute("isSuccess_Autoclose")).isEqualTo("true");
        assertThat(execute(operation)).isEqualTo("error");
        assertThat(renders).hasValue(3);
        assertThat(persisted).hasValue(operation == Operation.EDOC ? 1 : 0);
        assertThat(temporary.resolve("rendered.pdf")).doesNotExist();
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"EDOC", "DOWNLOAD"})
    void emptyContinuationCannotApproveMissingContentButInformedTicketCan(Operation operation) throws Exception {
        ticket(operation);
        renderBehavior(operation, true);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThat(execute(operation)).isEqualTo("renderBusy");
            continueWaiting();
        }
        assertThat(execute(operation)).isEqualTo("missingContent");
        assertThat(persisted).hasValue(0);
        assertThat(request.getAttribute("failedContentResources")).isEqualTo(2);
        assertThat(request.getAttribute("approvalAutoClose")).isEqualTo("true");
        request.setParameter("renderApproval", (String) request.getAttribute("renderApproval"));
        assertThat(execute(operation)).isEqualTo(operation == Operation.EDOC ? "close" : "download");
        assertThat(persisted).hasValue(operation == Operation.EDOC ? 1 : 0);
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"EDOC", "DOWNLOAD"})
    void genuineFailureNeverCreatesAnAutomaticContinuation(Operation operation) throws Exception {
        ticket(operation);
        if (operation == Operation.EDOC) when(documents.saveEFormAsEDoc(any(), any(), any()))
                .thenThrow(new PDFGenerationException("persistence or render failure"));
        else when(documents.renderEFormPacketWithCompleteness(any(), any(), any()))
                .thenThrow(new PDFGenerationException("corrupt render"));
        assertThat(execute(operation)).isEqualTo("error");
        assertThat(request.getAttribute("renderCapacityAction")).isNull();
        assertThat(request.getAttribute("renderCapacityFields")).isNull();
    }
}
