/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Retained route behavior, authorization and invalid opener refusal. @since 2026-11-09 */
@Tag("unit")
class BcCompatibilityRoutesUnitTest extends CarlosUnitTestBase {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "A provider has an invalid billing group,400",
            "Another Teleplan export is running; retry when it finishes,409",
            "Teleplan artifact changed; operator review is required,503",
            "FAKE-PATIENT unexpected database failure,500"})
    void shouldExplainLegacyExportRefusals_withoutExposingUnexpectedMessages(String message,int status) throws Exception {
        var request=new MockHttpServletRequest("POST","/billing/CA/BC/genTeleplanGroupReport");
        var response=new MockHttpServletResponse();
        var login=mock(io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("BC60");
        io.github.carlos_emr.carlos.utility.LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),login);
        var security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_report"),eq("r"),isNull())).thenReturn(true);
        var service=createAndRegisterMock(io.github.carlos_emr.carlos.billings.ca.bc.service.LegacyBcTeleplanExportService.class);
        when(service.generate(any(),any())).thenThrow(new IllegalStateException("wrapper",new IllegalArgumentException(message)));
        try(var servlet=mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new ViewGenTeleplanGroupReport2Action().execute()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(response.getErrorMessage()).doesNotContain("FAKE-PATIENT");
            if(status!=500) assertThat(response.getErrorMessage()).isEqualTo(message);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void shouldKeepPrivateBookmarksUseful_withoutAnUnassembledStatement(boolean preview) throws Exception {
        var request=new MockHttpServletRequest(); request.setContextPath("/carlos"); request.setParameter("providerId","BC60");
        var response=new MockHttpServletResponse();
        var security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_billing"),eq("r"),isNull())).thenReturn(true);
        var properties=mock(CarlosProperties.class); when(properties.getProperty("billregion","")).thenReturn("BC");
        try(var servlet=mockStatic(ServletActionContext.class);var config=mockStatic(CarlosProperties.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            config.when(CarlosProperties::getInstance).thenReturn(properties);
            String result=preview?new ViewPrivateBillingPrintPreview2Action().execute():new ViewPrivateBillingStatement2Action().execute();
            assertThat(result).isEqualTo("none");
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/billing/CA/BC/reprocessBill?showPRIV=show&providerview=BC60");
        }
    }

    @Test
    void shouldRetainPreparedPrivatePreview_forInternalDispatchers() throws Exception {
        var request=new MockHttpServletRequest(); request.setAttribute("patientBills",java.util.List.of());
        var security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_billing"),eq("r"),isNull())).thenReturn(true);
        try(var servlet=mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            assertThat(new ViewPrivateBillingPrintPreview2Action().execute()).isEqualTo("success");
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"", "form.name", "x'];alert(1);//"})
    void shouldRefuseInvalidOpener_beforeCodePickerDispatch(String form) throws Exception {
        var request=new MockHttpServletRequest(); request.setParameter("form",form); request.setParameter("field","xml_other1");
        var response=new MockHttpServletResponse();
        var security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_billing"),eq("r"),isNull())).thenReturn(true);
        try(var servlet=mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new ViewSupportBillingCodes2Action().execute()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(400);
        }
    }

    @Test
    void shouldValidateTypedLegacyAssociations_throughTheLiveWriter() {
        var request=new MockHttpServletRequest("POST","/billing/CA/BC/associateCodesAction");
        var response=new MockHttpServletResponse();
        var security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_billing"),eq("w"),isNull())).thenReturn(true);
        try(var servlet=mockStatic(ServletActionContext.class);
            var persistence=mockConstruction(BillingAssociationPersistence.class,(mock,context)->{
                when(mock.serviceCodeExists("00100")).thenReturn(true); when(mock.dxcodeExists("250")).thenReturn(true);
            })) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action=new AssociateCodes2Action(); var association=new ServiceCodeAssociation();
            association.setServiceCode("00100"); association.addDXCode("250"); action.setSvcAssoc(association);
            assertThat(action.execute()).isEqualTo("success");
            verify(persistence.constructed().getFirst()).saveServiceCodeAssociation(association,"");
        }
    }

    @Test
    void shouldRefuseGetOnLegacyAssociationWriter_beforePersistence() {
        var request=new MockHttpServletRequest("GET","/billing/CA/BC/associateCodesAction");
        var response=new MockHttpServletResponse();
        var security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_billing"),eq("w"),isNull())).thenReturn(true);
        try(var servlet=mockStatic(ServletActionContext.class);var persistence=mockConstruction(BillingAssociationPersistence.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new AssociateCodes2Action().execute()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(405); assertThat(persistence.constructed()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void shouldSaveAssociationWithActualStrutsParameterBinding_forTypedAndEditorClients(boolean typed) throws Exception {
        var request=new MockHttpServletRequest("POST","/billing/CA/BC/associateCodesAction");
        request.setParameter(typed ? "svcAssoc.serviceCode" : "xml_other1", "00100");
        request.setParameter(typed ? "svcAssoc.dxCodes" : "xml_diagnostic_detail1", "250");
        var response=new MockHttpServletResponse();
        var security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_billing"),eq("w"),isNull())).thenReturn(true);
        var previous=org.apache.struts2.ActionContext.getContext();
        var previousDispatcher=org.apache.struts2.dispatcher.Dispatcher.getInstance();
        var dispatcher=new org.apache.struts2.dispatcher.Dispatcher(request.getServletContext(),
                java.util.Map.of("config","struts-default.xml","struts.devMode","false"));
        dispatcher.init();
        try(var servlet=mockStatic(ServletActionContext.class);
            var persistence=mockConstruction(BillingAssociationPersistence.class,(mock,context)->{
                when(mock.serviceCodeExists("00100")).thenReturn(true); when(mock.dxcodeExists("250")).thenReturn(true);
            })) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action=new AssociateCodes2Action();
            var container=dispatcher.getContainer();
            container.getInstance(org.apache.struts2.ognl.ThreadAllowlist.class).allowClassHierarchy(action.getClass());
            var stack=container.getInstance(org.apache.struts2.util.ValueStackFactory.class).createValueStack();
            stack.push(action);
            var context=stack.getActionContext().withContainer(container).withValueStack(stack)
                    .withServletRequest(request).withServletResponse(response)
                    .withParameters(org.apache.struts2.dispatcher.HttpParameters.create(request.getParameterMap()).build()).bind();
            var invocation=mock(org.apache.struts2.ActionInvocation.class);
            var proxy=mock(org.apache.struts2.ActionProxy.class); when(proxy.getMethod()).thenReturn("execute");
            when(invocation.getProxy()).thenReturn(proxy); when(invocation.getAction()).thenReturn(action);
            when(invocation.getInvocationContext()).thenReturn(context); when(invocation.getStack()).thenReturn(stack);
            when(invocation.invoke()).thenReturn("success");
            var interceptor=new org.apache.struts2.interceptor.parameter.ParametersInterceptor();
            container.inject(interceptor); interceptor.setRequireAnnotations("true"); interceptor.init();
            try { interceptor.intercept(invocation); } finally { interceptor.destroy(); }
            assertThat(action.getSvcAssoc().getServiceCode()).isEqualTo("00100");
            assertThat(action.getSvcAssoc().getDxCodes()).containsExactly("250");
            assertThat(action.execute()).isEqualTo("success");
            verify(persistence.constructed().getFirst()).saveServiceCodeAssociation(action.getSvcAssoc(),"");
        } finally {
            dispatcher.getContainer().getInstance(org.apache.struts2.ognl.ThreadAllowlist.class).clearAllowlist();
            dispatcher.cleanup();
            if(previousDispatcher==null) org.apache.struts2.dispatcher.Dispatcher.clearInstance();
            else org.apache.struts2.dispatcher.Dispatcher.setInstance(previousDispatcher);
            if(previous==null) org.apache.struts2.ActionContext.clear(); else previous.bind();
        }
    }
}
