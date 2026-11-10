/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
class ConsultationPreferenceValidationUnitTest extends CarlosUnitTestBase {
    @BeforeEach
    void registerDependencies() {
        registerMock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class,
                mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class));
        registerMock(io.github.carlos_emr.carlos.managers.ProviderManager2.class,
                mock(io.github.carlos_emr.carlos.managers.ProviderManager2.class));
        registerMock(io.github.carlos_emr.carlos.managers.SecurityManager.class,
                mock(io.github.carlos_emr.carlos.managers.SecurityManager.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.SecurityDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.SecurityDao.class));
    }
    @Test
    void shouldRetainSubmittedPreferencesAndShowError_whenGeneralPeriodIsInvalid() throws Exception {
        var request = request();
        var response = new MockHttpServletResponse();
        var properties = mock(UserPropertyDAO.class);
        registerMock(UserPropertyDAO.class, properties);
        var saved = new UserProperty();
        saved.setName("pref.colour");
        saved.setValue("red");
        when(properties.getDemographicProperties("999998")).thenReturn(List.of(saved));
        request.setParameter("pref.colour", "blue");
        request.setParameter("pref." + UserProperty.CONSULTATION_TIME_PERIOD_WARNING, "6 months");
        request.setParameter("new_password", "FAKE-new-password");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = spy(new UserPreference2Action());
            doNothing().when(action).init();
            doReturn("Enter a positive whole number of months").when(action).getText("provider.consultationWarning.invalid");
            assertThat(action.saveGeneral()).isEqualTo("form");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(request.getAttribute("prefs")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("pref.colour", "blue");
            assertThat(request.getAttribute("preferenceValidationError")).isNotNull();
            verify(properties, never()).saveProp(any(UserProperty.class));
            assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/provider/UserPreferences.jsp")))
                    .contains("role=\"alert\"", "<c:out value=\"${preferenceValidationError}\"/>");
        }
    }

    @Test
    void shouldRetainSubmittedPopupValueAndShowError_whenPeriodIsInvalid() throws Exception {
        var request = request();
        var response = new MockHttpServletResponse();
        var properties = mock(UserPropertyDAO.class);
        registerMock(UserPropertyDAO.class, properties);
        var submitted = new UserProperty();
        submitted.setValue("6 months");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = spy(new ProviderProperty2Action());
            doReturn("Enter a positive whole number of months").when(action).getText("provider.consultationWarning.invalid");
            action.setDateProperty(submitted);
            assertThat(action.saveConsultationRequestCuffOffDate()).isEqualTo("gen");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(request.getAttribute("dateProperty")).isSameAs(submitted);
            assertThat(submitted.getValue()).isEqualTo("6 months");
            assertThat(request.getAttribute("preferenceValidationError")).isNotNull();
            verify(properties, never()).saveProp(any(UserProperty.class));
            assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/provider/setGenProperty.jsp")))
                    .contains("role=\"alert\"", "<c:out value=\"${preferenceValidationError}\"/>");
        }
    }

    @Test
    void shouldStoreCanonicalMonths_whenPopupValueHasWhitespace() {
        var request = request();
        var properties = mock(UserPropertyDAO.class);
        registerMock(UserPropertyDAO.class, properties);
        var submitted = new UserProperty();
        submitted.setValue(" 006 ");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action = spy(new ProviderProperty2Action());
            doReturn("Enter a positive whole number of months").when(action).getText("provider.consultationWarning.invalid");
            action.setDateProperty(submitted);
            assertThat(action.saveConsultationRequestCuffOffDate()).isEqualTo("gen");
            var saved = org.mockito.ArgumentCaptor.forClass(UserProperty.class);
            verify(properties).saveProp(saved.capture());
            assertThat(saved.getValue().getValue()).isEqualTo("6");
        }
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/preferences");
        var user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        return request;
    }
}
