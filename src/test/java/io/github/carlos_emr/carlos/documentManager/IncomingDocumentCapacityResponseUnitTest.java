/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fast")
class IncomingDocumentCapacityResponseUnitTest {
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        request.setMethod("POST");
        request.setParameter("method", "addIncomingDocument");
        request.setParameter("pdfAction", "DeletePage");
        return request;
    }

    @Test
    void pageCountWaitAfterPostTargetsOnlySafeGetAndEncodesHtml() throws Exception {
        var request = request();
        var response = new MockHttpServletResponse();
        var writer = new StringWriter();
        IncomingDocumentCapacityResponse.pageCountBusy(request, response, writer, "1", "File", "2", "3", "");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        var html = Jsoup.parse(writer.toString());
        var marker = html.getElementById("incomingDocumentPageCountWait");
        assertThat(marker.attr("data-auto-retry")).isEqualTo("true");
        assertThat(marker.attr("data-retry-url")).isEqualTo("/carlos/documentManager/ViewIncomingDocs?queueId=1&pdfDir=File&pdfNo=2&pdfPageNumber=3");
        assertThat(writer.toString()).doesNotContain("DeletePage", "addIncomingDocument", "location.reload");
        assertThat(html.select("script")).hasSize(1);
    }

    @Test
    void priorMutationErrorRemainsVisibleAndDisablesAutomaticNavigation() throws Exception {
        var writer = new StringWriter();
        IncomingDocumentCapacityResponse.pageCountBusy(request(), new MockHttpServletResponse(), writer,
                "1", "Fax", "1", "1", "<script>private error</script>");
        var html = Jsoup.parse(writer.toString());
        assertThat(html.getElementById("incomingDocumentPageCountWait").attr("data-auto-retry")).isEqualTo("false");
        assertThat(html.select("p[role=alert]").text()).isEqualTo("<script>private error</script>");
        assertThat(html.select("script")).hasSize(1);
    }

    @Test
    void nativeCapacityRefusalPreservesExactRepeatedFieldsWithoutAutomaticPostReplay() throws Exception {
        var request = request();
        request.setParameter("documentDescription", "<img src=x onerror='bad()'> & \"quoted\"");
        request.addParameter("flagproviders", "10", "20");
        request.setParameter("CSRF-TOKEN", "private-token");
        var response = new MockHttpServletResponse();
        IncomingDocumentCapacityResponse.filingBusy(request, response);
        var html = Jsoup.parse(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(html.select("form").attr("action")).isEqualTo("/carlos/documentManager/ManageDocument");
        assertThat(html.select("input[name=documentDescription]").val()).isEqualTo(request.getParameter("documentDescription"));
        assertThat(html.select("input[name=flagproviders]").eachAttr("value")).containsExactly("10", "20");
        assertThat(html.select("input[name=CSRF-TOKEN]").val()).isEqualTo("private-token");
        assertThat(html.select("script,img")).isEmpty();
    }

    @Test
    void contractRefusalIsExplicitAndContainsNoSubmittedClinicalData() throws Exception {
        var request = request();
        request.addHeader(IncomingDocumentCapacityResponse.CONTRACT_HEADER, IncomingDocumentCapacityResponse.CONTRACT_VERSION);
        request.setParameter("documentDescription", "private-description");
        var response = new MockHttpServletResponse();
        IncomingDocumentCapacityResponse.filingBusy(request, response);
        var result = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(result.path("success").asBoolean(true)).isFalse();
        assertThat(result.path("accepted").asBoolean(true)).isFalse();
        assertThat(result.path("retryable").asBoolean()).isTrue();
        assertThat(response.getContentAsString()).doesNotContain("private-description");
    }

    @Test
    void readOnlyNavigationRejectsQueueInjectionAndNormalizesBadPageNumbers() {
        assertThatThrownBy(() -> IncomingDocumentCapacityResponse.readOnlyUrl(request(), "../1", "File", "1", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IncomingDocumentCapacityResponse.readOnlyUrl(request(), "1", "unknown", "1", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(IncomingDocumentCapacityResponse.readOnlyUrl(request(), "1", "File", "0", "private"))
                .endsWith("pdfNo=1&pdfPageNumber=1");
    }
    @Test
    void readOnlyNavigationPreservesOnlyValidatedOptionalWorkflowState() {
        var request = request();
        request.setParameter("lastdemographic_no", "100");
        request.setParameter("entryMode", "Fast");
        assertThat(IncomingDocumentCapacityResponse.readOnlyUrl(request, "1", "File", "1", "1"))
                .endsWith("&lastdemographic_no=100&entryMode=Fast");
        request.setParameter("lastdemographic_no", "2147483648");
        request.setParameter("entryMode", "private-mode");
        assertThat(IncomingDocumentCapacityResponse.readOnlyUrl(request, "1", "File", "1", "1"))
                .doesNotContain("lastdemographic_no", "entryMode", "private-mode");
        assertThatThrownBy(() -> IncomingDocumentCapacityResponse.readOnlyUrl(request, "queue1", "File", "1", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namedQueuesRequireReadAccessWhileDefaultQueueRemainsShared() {
        io.github.carlos_emr.carlos.managers.SecurityInfoManager security = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        io.github.carlos_emr.carlos.utility.LoggedInInfo info = new io.github.carlos_emr.carlos.utility.LoggedInInfo();
        IncomingDocumentCapacityResponse.requireQueueAccess(security, info, "1");
        org.mockito.Mockito.verifyNoInteractions(security);
        assertThatThrownBy(() -> IncomingDocumentCapacityResponse.requireQueueAccess(security, info, "2"))
                .isInstanceOf(SecurityException.class);
        org.mockito.Mockito.when(security.hasPrivilege(info, "_queue.2", "r", (String) null)).thenReturn(true);
        IncomingDocumentCapacityResponse.requireQueueAccess(security, info, "2");
        for (String invalid : new String[] {"0", "01", "-1", "2147483648", "../2"}) {
            assertThatThrownBy(() -> IncomingDocumentCapacityResponse.requireQueueAccess(security, info, invalid))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void unreadablePageCountPreservesPriorErrorWithoutAutomaticReplay() throws Exception {
        var request = request();
        request.setMethod("POST");
        request.setParameter("pdfAction", "DeletePage");
        var response = new MockHttpServletResponse();
        var writer = new java.io.StringWriter();
        IncomingDocumentCapacityResponse.pageCountFailed(request, response, writer, "1", "File", "2", "1", "Edit <failed>");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(writer.toString()).contains("could not be read", "Edit &lt;failed&gt;")
                .doesNotContain("<script", "<form", "DeletePage", "data-auto-retry");
        assertThat(org.jsoup.Jsoup.parse(writer.toString()).selectFirst("a").attr("href"))
                .contains("ViewIncomingDocs?queueId=1", "pdfNo=2");
    }

}
