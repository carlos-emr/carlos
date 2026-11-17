/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form;

import java.util.Properties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class Frm2ActionPostGuardUnitTest extends CarlosWebTestBase {
    private void prepare(String method, String submit) {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        mockRequest.setMethod(method); addRequestParameter("submit", submit);
        addRequestParameter("form_class", "Rourke2017");
        addRequestParameter("demographic_no", "123"); addRequestParameter("formId", "7");
    }

    @ParameterizedTest
    @CsvSource({"GET,save", "HEAD,save", "PUT,save", "DELETE,save", "GET,autosaveAjax", "HEAD,autosaveAjax", "GET,saveFormLetter", "GET,printall", "GET,unknown"})
    void shouldRefuseWritesBeforeRecordConstructionAndIdentifierParsing(String method, String submit) {
        prepare(method, submit); addRequestParameter("demographic_no", "invalid"); addRequestParameter("formId", "invalid");
        try (var factory = mockConstruction(FrmRecordFactory.class)) {
            assertThat(new Frm2Action().execute()).isEqualTo("none");
            assertThat(mockResponse.getStatus()).isEqualTo(405); assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
            assertThat(factory.constructed()).isEmpty();
            verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), eq(null));
        }
    }

    @Test
    void shouldRefuseDefaultAndUpdateWritesWithoutAnExplicitSaveSubmit() {
        prepare("GET", ""); addRequestParameter("update", "true"); addRequestParameter("formId", "invalid");
        try (var factory = mockConstruction(FrmRecordFactory.class)) {
            assertThat(new Frm2Action().execute()).isEqualTo("none"); assertThat(factory.constructed()).isEmpty();
            assertThat(mockResponse.getStatus()).isEqualTo(405);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void shouldRetainAuthorizationFailureBeforeMethodOrRecordHandling(String method) {
        prepare(method, "save"); addRequestParameter("formId", "invalid");
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), eq(null))).thenReturn(false);
        try (var factory = mockConstruction(FrmRecordFactory.class)) {
            assertThatThrownBy(() -> new Frm2Action().execute()).isInstanceOf(SecurityException.class);
            assertThat(mockResponse.getHeader("Allow")).isNull(); assertThat(factory.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseWellFormedSaveWithoutConstructingItsRecord(String method) {
        prepare(method, "save"); addRequestParameter("weight", "5.5");
        try (var factory = mockConstruction(FrmRecordFactory.class)) {
            assertThat(new Frm2Action().execute()).isEqualTo("none"); assertThat(mockResponse.getStatus()).isEqualTo(405);
            assertThat(factory.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"GET,graph", "HEAD,graph", "GET,printAll", "HEAD,printAll", "GET,printAllJasperReport", "HEAD,printAllJasperReport"})
    void shouldPreserveReadOnlyGraphAndSavedRecordPrintBranches(String method, String submit) throws Exception {
        prepare(method, submit);
        FrmRecord record = mock(FrmRecord.class);
        when(record.getGraph(any(), eq(123), eq(7))).thenReturn(new Properties());
        when(record.getFormRecord(any(), eq(123), eq(7))).thenReturn(new Properties());
        when(record.findActionValue(submit)).thenReturn(submit);
        when(record.createActionURL(eq(submit), eq(submit), eq("123"), eq("7"))).thenReturn(submit + "?formId=7");
        try (var factory = mockConstruction(FrmRecordFactory.class, (created, context) -> when(created.factory("Rourke2017")).thenReturn(record))) {
            assertThat(new Frm2Action().execute()).isEqualTo(submit); assertThat(mockResponse.getStatus()).isEqualTo(200);
            verify(record, never()).saveFormRecord(any());
            if (submit.equals("graph")) verify(record).getGraph(any(), eq(123), eq(7));
            else verify(record).getFormRecord(any(), eq(123), eq(7));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "autosaveAjax", "update"})
    void shouldPreserveAuthorizedNativePostSavingAndItsResponse(String operation) throws Exception {
        String submit = operation.equals("update") ? "save" : operation;
        prepare("POST", submit); mockRequest.setContextPath("/carlos");
        if (operation.equals("update")) addRequestParameter("update", "true");
        addRequestParameter("weight", "5.5"); addRequestParameter("form_link", "formrourke2017p1.jsp");
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        FrmRecord record = mock(FrmRecord.class); when(record.saveFormRecord(any())).thenReturn(99);
        when(record.findActionValue(submit)).thenReturn("save");
        when(record.createActionURL(eq("save"), eq("save"), eq("123"), eq("99"))).thenReturn("save?demographic_no=123&formId=99");
        try (var log = mockStatic(LogAction.class);
             var factory = mockConstruction(FrmRecordFactory.class, (created, context) -> when(created.factory("Rourke2017")).thenReturn(record))) {
            assertThat(new Frm2Action().execute()).isNull();
            ArgumentCaptor<Properties> saved = ArgumentCaptor.forClass(Properties.class); verify(record).saveFormRecord(saved.capture());
            assertThat(saved.getValue().getProperty("weight")).isEqualTo("5.5");
            assertThat(saved.getValue().getProperty("provider_no")).isEqualTo("999998");
            if (operation.equals("autosaveAjax")) assertThat(mockResponse.getContentAsString()).contains("\"success\":true", "99");
            else assertThat(mockResponse.getRedirectedUrl()).contains("/form/forwardname?", "formId=99");
        }
    }
}
