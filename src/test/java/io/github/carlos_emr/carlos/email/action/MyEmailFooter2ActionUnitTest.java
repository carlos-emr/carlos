/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
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
    private MockHttpServletRequest request; private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> struts; private MockedStatic<LoggedInInfo> users;
    private MockedStatic<LogAction> audit;
    @BeforeEach void setup() {
        request=new MockHttpServletRequest("POST","/email/saveMyEmailFooter");request.setContextPath("/carlos");
        response=new MockHttpServletResponse();when(user.getLoggedInProviderNo()).thenReturn("101");
        when(security.hasPrivilege(user,"_email",SecurityInfoManager.WRITE,null)).thenReturn(true);
        when(clinic.snapshot()).thenReturn(new ClinicEmailFooterSnapshot("Mandatory Clinic",null));
        struts=mockStatic(ServletActionContext.class);struts.when(ServletActionContext::getRequest).thenReturn(request);
        struts.when(ServletActionContext::getResponse).thenReturn(response);
        users=mockStatic(LoggedInInfo.class);users.when(()->LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        audit=mockStatic(LogAction.class);
    }
    @AfterEach void close(){audit.close();users.close();struts.close();}
    private SaveMyEmailFooter2Action save(){return new SaveMyEmailFooter2Action(security,personal,clinic);}

    @Test void shouldShowSeparateEmptyPersonalAndMandatoryClinic() {
        when(personal.ownFooter("101")).thenReturn("");
        assertThat(new ViewMyEmailFooter2Action(security,personal,clinic).execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("myFooter")).isEqualTo("");
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("Mandatory Clinic");
        assertThat(request.getAttribute("clinicChangeNotice")).isNull();verify(personal).ownFooter("101");verifyNoMoreInteractions(personal);
    }
    @Test void shouldSaveOnlySessionOwner_despiteForgedOwnerAndClinicFields() throws Exception {
        request.setParameter("providerNo","202");request.setParameter("clinicFooter","Forged clinic");
        request.setParameter("footerAction","save");request.setParameter("myFooter","Own personal");
        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        verify(personal).saveOwnFooter("101","Own personal");verifyNoInteractions(clinic);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/email/myEmailFooter?saved=true");
    }
    @ParameterizedTest @ValueSource(strings={"GET","HEAD","PUT","DELETE"})
    void shouldRefuseNonPost_withoutDataWork(String method)throws Exception {
        request.setMethod(method);assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);verifyNoInteractions(personal,clinic);
    }
    @Test void shouldRefuseDeniedSender_beforeSettingsLookup() {
        when(security.hasPrivilege(user,"_email",SecurityInfoManager.WRITE,null)).thenReturn(false);
        assertThatThrownBy(()->new ViewMyEmailFooter2Action(security,personal,clinic).execute()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->save().execute()).isInstanceOf(SecurityException.class);verifyNoInteractions(personal,clinic);
    }
    @ParameterizedTest @ValueSource(strings={"restorePrevious","useClinicDefault","keepCurrent","invalid"})
    void shouldRejectRemovedClinicReplacementOperations(String op)throws Exception {
        request.setParameter("footerAction",op);assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);verifyNoInteractions(personal,clinic);
    }
    @Test void shouldRejectMissingPersonalField_ratherThanClear()throws Exception {
        request.setParameter("footerAction","save");assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);verifyNoInteractions(personal,clinic);
    }
    @Test void shouldRefuseHugeRawPersonalInput_beforeParsingOrSettingsWork()throws Exception {
        request.setParameter("footerAction","save");request.setParameter("myFooter","x".repeat(40001));
        assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);verifyNoInteractions(personal,clinic);
    }
    @Test void shouldClearOnlyPersonal_withoutClinicVersionParameter()throws Exception {
        request.setParameter("footerAction","clear");assertThat(save().execute()).isEqualTo(ActionSupport.NONE);
        verify(personal).saveOwnFooter("101","");verifyNoInteractions(clinic);
    }
    @Test void shouldKeepTypedPersonalAndShowCurrentClinic_afterConcurrentSave()throws Exception {
        request.setParameter("footerAction","save");request.setParameter("myFooter","Typed personal");
        doThrow(new org.springframework.dao.ConcurrencyFailureException("FAKE conflict"))
                .when(personal).saveOwnFooter("101","Typed personal");
        assertThat(save().execute()).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("myFooter")).isEqualTo("Typed personal");
        assertThat(request.getAttribute("myFooterSaveConflict")).isEqualTo(true);
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("Mandatory Clinic");
    }
}
