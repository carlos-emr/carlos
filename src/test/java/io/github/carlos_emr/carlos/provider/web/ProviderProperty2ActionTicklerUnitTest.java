/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression coverage for the complete Tickler Preferences save/reopen contract. */
@Tag("unit")
class ProviderProperty2ActionTicklerUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private UserPropertyDAO properties;
    private ProviderDao providers;
    private LoggedInInfo login;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private ProviderProperty2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        response = new MockHttpServletResponse();
        properties = mock(UserPropertyDAO.class);
        providers = mock(ProviderDao.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("owner");
        registerMock(UserPropertyDAO.class, properties);
        registerMock(ProviderDao.class, providers);
        registerMock(ProviderManager2.class, mock(ProviderManager2.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        when(providers.getProviders(true)).thenAnswer(call -> new ArrayList<Provider>());
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        action = new ProviderProperty2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
    }

    private UserProperty existing(String value) {
        UserProperty property = new UserProperty();
        property.setId(42);
        property.setProviderNo("owner");
        property.setName(UserProperty.TICKLER_TASK_ASSIGNEE);
        property.setValue(value);
        when(properties.getProp("owner", UserProperty.TICKLER_TASK_ASSIGNEE)).thenReturn(property);
        return property;
    }

    private void choose(String choice, String provider) {
        if (choice != null) request.setParameter("taskAssigneeMRP.value", choice);
        if (provider != null) request.setParameter("taskAssigneeSelection.value", provider);
    }

    private void activeProvider() {
        Provider provider = new Provider();
        provider.setProviderNo("other");
        provider.setStatus("1");
        when(providers.getProvider("other")).thenReturn(provider);
    }

    @Test
    void createsPreferenceForSessionOwnerWithoutAnOgnlModel() {
        activeProvider();
        choose("provider", "other");
        assertThat(action.saveTicklerTaskAssignee()).isEqualTo("complete");
        verify(properties).replaceTicklerTaskAssignee("owner", "other");
        assertThat(request.getAttribute("status")).isEqualTo("success");
    }

    @Test
    void updatesExistingPreferenceWithoutCreatingADuplicate() {
        UserProperty property = existing("mrp");
        activeProvider();
        choose("provider", "other");
        action.saveTicklerTaskAssignee();
        verify(properties).replaceTicklerTaskAssignee("owner", "other");
        verify(properties, never()).getProp(anyString(), anyString());
    }

    @Test
    void defaultReplacesAllCopiesInTheDaoTransaction() {
        UserProperty property = existing("other");
        choose("default", "ignored");
        action.saveTicklerTaskAssignee();
        verify(properties).replaceTicklerTaskAssignee("owner", null);
        verify(properties, never()).delete(any());
        verify(properties, never()).saveProp(any(UserProperty.class));
        assertThat(property.getValue()).isEqualTo("other");
    }

    @Test
    void defaultWithoutAnExistingPreferenceDoesNotCreateOne() {
        choose("default", null);
        action.saveTicklerTaskAssignee();
        verify(properties, never()).remove(anyInt());
        verify(properties, never()).saveProp(any(UserProperty.class));
        verify(properties).replaceTicklerTaskAssignee("owner", null);
    }

    @Test
    void mrpStoresTheSentinelAndIgnoresTheProviderInput() {
        choose("mrp", "other");
        action.saveTicklerTaskAssignee();
        verify(properties).replaceTicklerTaskAssignee("owner", "mrp");
        verify(providers, never()).getProvider(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void rejectsNonPostBeforeAccessingTheDao(String method) {
        request.setMethod(method);
        choose("default", null);
        assertThat(action.saveTicklerTaskAssignee()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(properties, providers);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "providers"})
    void invalidChoiceLeavesExistingPreferenceUnchanged(String choice) {
        UserProperty property = existing("other");
        choose(choice, "other");
        assertThat(action.saveTicklerTaskAssignee()).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("ticklerPreferenceError")).isEqualTo(true);
        assertThat(request.getAttribute("status")).isNull();
        assertThat(property.getValue()).isEqualTo("other");
        verify(properties, never()).saveProp(any(UserProperty.class));
        verify(properties, never()).remove(anyInt());
        verify(properties, never()).replaceTicklerTaskAssignee(anyString(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"missing", "-1", " "})
    void invalidProviderCannotReplaceTheSavedPreference(String value) {
        UserProperty property = existing("mrp");
        choose("provider", value);
        action.saveTicklerTaskAssignee();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(property.getValue()).isEqualTo("mrp");
        verify(properties, never()).saveProp(any(UserProperty.class));
        verify(properties, never()).replaceTicklerTaskAssignee(anyString(), any());
    }

    @Test
    void inactiveProviderIsRejected() {
        Provider inactive = new Provider();
        inactive.setStatus("0");
        when(providers.getProvider("inactive")).thenReturn(inactive);
        choose("provider", "inactive");
        action.saveTicklerTaskAssignee();
        assertThat(response.getStatus()).isEqualTo(400);
        verify(properties, never()).saveProp(any(UserProperty.class));
        verify(properties, never()).replaceTicklerTaskAssignee(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"other", "mrp", "default", ""})
    void viewSelectsTheSavedChoiceWithoutMutatingIt(String value) {
        UserProperty property = existing(value);
        action.viewTicklerTaskAssignee();
        String expected = "other".equals(value) ? "provider" : "mrp".equals(value) ? "mrp" : "default";
        assertThat(request.getAttribute("taskAssigneeMRPValue")).isEqualTo(expected);
        if ("other".equals(value)) assertThat(request.getAttribute("selectedProvider")).isEqualTo("other");
        assertThat(property.getValue()).isEqualTo(value);
        verify(properties, never()).saveProp(any(UserProperty.class));
        verify(properties, never()).replaceTicklerTaskAssignee(anyString(), any());
    }

    @Test
    void saveFailureCannotReportSuccess() {
        choose("mrp", null);
        doThrow(new IllegalStateException("database unavailable")).when(properties).replaceTicklerTaskAssignee("owner", "mrp");
        assertThatThrownBy(action::saveTicklerTaskAssignee).isInstanceOf(IllegalStateException.class);
        assertThat(request.getAttribute("status")).isNull();
    }

    @Test
    void deleteFailureCannotReportSuccess() {
        existing("other");
        choose("default", null);
        doThrow(new IllegalStateException("database unavailable")).when(properties).replaceTicklerTaskAssignee("owner", null);
        assertThatThrownBy(action::saveTicklerTaskAssignee).isInstanceOf(IllegalStateException.class);
        assertThat(request.getAttribute("status")).isNull();
    }

    @Test
    void rejectsMissingSessionWithoutReadingPreferences() {
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        choose("default", null);
        assertThatThrownBy(action::saveTicklerTaskAssignee).isInstanceOf(SecurityException.class);
        verifyNoInteractions(properties);
    }
}
