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
package io.github.carlos_emr.carlos.email.action;

import io.github.carlos_emr.carlos.email.core.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.log.LogAction;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("email") @Tag("security")
class MyEmailFooter2ActionUnitTest {
    private final SecurityInfoManager security=mock(SecurityInfoManager.class);
    private final EmailFooterService personal=mock(EmailFooterService.class);
    private final ClinicEmailFooterService clinic=mock(ClinicEmailFooterService.class);
    private final LoggedInInfo user=mock(LoggedInInfo.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> struts;
    private MockedStatic<LoggedInInfo> users;
    private MockedStatic<LogAction> audit;
    @BeforeEach void setup() {
        request=new MockHttpServletRequest("POST","/email/saveMyEmailFooter");
        request.setContextPath("/carlos");
        request.setParameter("personalFooterRevision", EmailFooterService.revisionOf("101", ""));
        response=new MockHttpServletResponse();
        when(user.getLoggedInProviderNo()).thenReturn("101");
        when(security.hasPrivilege(user,"_email",SecurityInfoManager.WRITE,null)).thenReturn(true);
        when(clinic.snapshot()).thenReturn(new ClinicEmailFooterSnapshot("Mandatory Clinic",null));
        struts=mockStatic(ServletActionContext.class);
        struts.when(ServletActionContext::getRequest).thenReturn(request);
        struts.when(ServletActionContext::getResponse).thenReturn(response);
        users=mockStatic(LoggedInInfo.class);
        users.when(()->LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        audit=mockStatic(LogAction.class);
    }
    @AfterEach void close(){audit.close();
    users.close();
    struts.close();
    }
    private SaveMyEmailFooter2Action save(){return new SaveMyEmailFooter2Action(security,personal,clinic);
    }

    @Test void shouldShowSeparateEmptyPersonalAndMandatoryClinic() throws Exception {
        when(personal.ownFooter("101")).thenReturn("");
        assertThat(new ViewMyEmailFooter2Action(security,personal,clinic).execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("myFooter")).isEqualTo("");
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("Mandatory Clinic");
        assertThat(request.getAttribute("clinicChangeNotice")).isNull();
        verify(personal).ownFooter("101");
        verifyNoMoreInteractions(personal);
    }
    @Test void shouldSaveOnlySessionOwner_despiteForgedOwnerAndClinicFields() throws Exception {
        request.setParameter("providerNo","202");
        request.setParameter("clinicFooter","Forged clinic");
        request.setParameter("footerAction","save");
        request.setParameter("myFooter","Own personal");
        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        verify(personal).saveOwnFooter("101","Own personal", request.getParameter("personalFooterRevision"));
        verifyNoInteractions(clinic);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/email/myEmailFooter?saved=true");
    }
    @ParameterizedTest @ValueSource(strings={"GET","HEAD","PUT","DELETE"})
    void shouldRefuseNonPost_withoutDataWork(String method)throws Exception {
        request.setMethod(method);
        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(personal,clinic);
    }
    @Test void shouldRefuseDeniedSender_beforeSettingsLookup() {
        when(security.hasPrivilege(user,"_email",SecurityInfoManager.WRITE,null)).thenReturn(false);
        assertThatThrownBy(()->new ViewMyEmailFooter2Action(security,personal,clinic).execute()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->save().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(personal,clinic);
    }
    @ParameterizedTest @ValueSource(strings={"restorePrevious","useClinicDefault","keepCurrent","invalid"})
    void shouldRejectRemovedClinicReplacementOperations(String op)throws Exception {
        request.setParameter("footerAction",op);
        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(personal,clinic);
    }
    @Test void shouldRejectMissingPersonalField_ratherThanClear()throws Exception {
        request.setParameter("footerAction","save");
        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(personal,clinic);
    }
    @Test void shouldRefuseHugeRawPersonalInput_beforeParsingOrSettingsWork()throws Exception {
        request.setParameter("footerAction","save");
        request.setParameter("myFooter","x".repeat(40001));
        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(personal,clinic);
    }
    @Test void shouldClearOnlyPersonal_withoutClinicVersionParameter()throws Exception {
        request.setParameter("footerAction","clear");
        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        verify(personal).saveOwnFooter("101","", request.getParameter("personalFooterRevision"));
        verifyNoInteractions(clinic);
    }
    @Test void shouldKeepTypedPersonalAndShowCurrentClinic_afterConcurrentSave()throws Exception {
        request.setParameter("footerAction","save");
        request.setParameter("myFooter","Typed personal");
        doThrow(new org.springframework.dao.ConcurrencyFailureException("FAKE conflict"))
                .when(personal).saveOwnFooter("101","Typed personal", request.getParameter("personalFooterRevision"));
        assertThat(save().execute()).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("myFooter")).isEqualTo("Typed personal");
        assertThat(request.getAttribute("myFooterSaveConflict")).isEqualTo(true);
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("Mandatory Clinic");
    }
    @ParameterizedTest
    @ValueSource(strings = {"save", "clear"})
    void shouldKeepNewerSavedTextAndOfferUnsavedText_whenEditorIsStale(String operation) throws Exception {
        request.setParameter("footerAction", operation);
        request.setParameter("myFooter", "Older typed text");
        String attempted = "clear".equals(operation) ? "" : "Older typed text";
        when(personal.ownFooter("101")).thenReturn("<b>Newer saved text</b>");
        doThrow(new EmailFooterService.StaleFooterException()).when(personal)
                .saveOwnFooter("101", attempted, request.getParameter("personalFooterRevision"));
        assertThat(save().execute()).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("myFooter")).isEqualTo("<b>Newer saved text</b>");
        assertThat(request.getAttribute("myFooterAttempted")).isEqualTo(attempted);
        assertThat(request.getAttribute("personalFooterRevision"))
                .isEqualTo(EmailFooterService.revisionOf("101", "<b>Newer saved text</b>"));
        assertThat(request.getAttribute("myFooterStale")).isEqualTo(true);
        assertThat(response.getRedirectedUrl()).isNull();
        audit.verifyNoInteractions();
    }


    // Ben's demo (15 Oct): the editor window in Preferences and on the email screen reads and saves the
    // saved personal footer itself, as JSON. Form posts behave as they always have.
    private static com.fasterxml.jackson.databind.JsonNode json(MockHttpServletResponse reply) throws Exception {
        assertThat(reply.getContentType()).startsWith("application/json");
        assertThat(reply.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(reply.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(reply.getContentAsByteArray());
    }
    private void askForJson() {
        request.setMethod("GET");
        request.addHeader("Accept", "application/json");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
    }
    private void sendFromWindow(String text) {
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.setParameter("footerAction", "save");
        request.setParameter("myFooter", text);
    }

    @Test void shouldGiveTheWindowTheSavedFooterAndItsRevision_readOnly() throws Exception {
        askForJson();
        when(personal.ownFooter("101")).thenReturn("<b>FAKE own</b>");

        assertThat(new ViewMyEmailFooter2Action(security,personal,clinic).execute()).isEqualTo(ActionSupport.NONE);

        var body = json(response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.get("footer").asText()).isEqualTo("<b>FAKE own</b>");
        assertThat(body.get("revision").asText()).isEqualTo(EmailFooterService.revisionOf("101", "<b>FAKE own</b>"));
        verify(personal).ownFooter("101");
        verifyNoMoreInteractions(personal);
        verifyNoInteractions(clinic);
        audit.verifyNoInteractions();
    }

    @Test void shouldRefuseTheFooterAsJson_toAUserWithoutTheRight() {
        askForJson();
        when(security.hasPrivilege(user,"_email",SecurityInfoManager.WRITE,null)).thenReturn(false);

        assertThatThrownBy(()->new ViewMyEmailFooter2Action(security,personal,clinic).execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(personal, clinic);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"POST","PUT","DELETE"})
    void shouldAnswerTheJsonReadForGetOnly(String method) throws Exception {
        askForJson();
        request.setMethod(method);

        new ViewMyEmailFooter2Action(security,personal,clinic).execute();

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET");
        verifyNoInteractions(personal, clinic);
    }

    @Test void shouldSaveFromTheWindow_andAnswerTheNewRevision() throws Exception {
        sendFromWindow("<b>FAKE own</b>");

        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);

        var body = json(response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(body.get("saved").asBoolean()).isTrue();
        assertThat(body.get("revision").asText()).isEqualTo(EmailFooterService.revisionOf("101", "<b>FAKE own</b>"));
        verify(personal).saveOwnFooter("101", "<b>FAKE own</b>", request.getParameter("personalFooterRevision"));
        audit.verify(() -> LogAction.addLog("101", io.github.carlos_emr.carlos.log.LogConst.UPDATE, "emailFooterOwn", "", "127.0.0.1"));
    }

    @Test void shouldAnswerTheWindowsRefusals_withAFixedCodeAndNoText() throws Exception {
        sendFromWindow("FAKE secret-looking text");
        doThrow(new EmailFooterService.StaleFooterException()).when(personal).saveOwnFooter(anyString(), anyString(), any());
        save().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(json(response).toString()).isEqualTo("{\"error\":\"stale\"}");

        response = new MockHttpServletResponse();
        struts.when(ServletActionContext::getResponse).thenReturn(response);
        doThrow(new org.springframework.dao.CannotAcquireLockException("FAKE")).when(personal).saveOwnFooter(anyString(), anyString(), any());
        save().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(json(response).toString()).isEqualTo("{\"error\":\"conflict\"}");

        response = new MockHttpServletResponse();
        struts.when(ServletActionContext::getResponse).thenReturn(response);
        request.setParameter("myFooter", "x".repeat(4 * EmailFooterHtml.MAX_HTML_LENGTH + 1));
        save().execute();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(json(response).toString()).isEqualTo("{\"error\":\"tooLong\"}");
        audit.verifyNoInteractions();
    }

    @Test void shouldStillRedirectAPlainFormPost_withoutTheAjaxHeader() throws Exception {
        request.setParameter("footerAction", "save");
        request.setParameter("myFooter", "FAKE own");

        save().execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/email/myEmailFooter?saved=true");
        assertThat(response.getContentType()).isNull();
    }

    @Test void shouldRefuseTheWindowsSave_toAUserWithoutTheRight() {
        sendFromWindow("FAKE own");
        when(security.hasPrivilege(user,"_email",SecurityInfoManager.WRITE,null)).thenReturn(false);

        assertThatThrownBy(()->save().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(personal, clinic);
    }

    @Test void shouldAnswerTheWindowsInvalidSends_asInvalid() throws Exception {
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        request.setParameter("footerAction", "save");
        save().execute();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(json(response).toString()).isEqualTo("{\"error\":\"invalid\"}");

        response = new MockHttpServletResponse();
        struts.when(ServletActionContext::getResponse).thenReturn(response);
        request.setParameter("footerAction", "restoreClinicDefault");
        request.setParameter("myFooter", "FAKE own");
        save().execute();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(json(response).toString()).isEqualTo("{\"error\":\"invalid\"}");
        verifyNoInteractions(personal, clinic);
        audit.verifyNoInteractions();
    }

    @Test void shouldAnswerTooLongAfterCleaning_asTooLong() throws Exception {
        sendFromWindow("FAKE own");
        doThrow(EmailFooterService.FooterTooLongException.class).when(personal).saveOwnFooter(anyString(), anyString(), any());

        save().execute();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(json(response).toString()).isEqualTo("{\"error\":\"tooLong\"}");
        audit.verifyNoInteractions();
    }
}
