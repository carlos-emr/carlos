/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Every preference write or removal in {@code setProviderStaleDate} / {@code setTicklerPreferences} is reached
 * only from its page's POST, so each one refuses any other method before reading or storing anything. The list
 * comes from the action's own dispatch map: an entry that does not start with "view" must refuse GET, so a new
 * unguarded write fails here.
 */
@Tag("unit")
@DisplayName("Provider preference writes accept only POST")
class ProviderProperty2ActionMethodGuardUnitTest extends CarlosUnitTestBase {
    private UserPropertyDAO properties;
    private ProviderDao providers;
    private ProviderManager2 providerManager;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;

    @BeforeEach
    void setUp() {
        properties = mock(UserPropertyDAO.class);
        providers = mock(ProviderDao.class);
        providerManager = mock(ProviderManager2.class);
        security = mock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("owner");
        registerMock(UserPropertyDAO.class, properties);
        registerMock(ProviderDao.class, providers);
        registerMock(ProviderManager2.class, providerManager);
        registerMock(SecurityInfoManager.class, security);
        servlet = mockStatic(ServletActionContext.class);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
    }

    @AfterEach
    void tearDown() {
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
    }

    private ProviderProperty2Action actionFor(MockHttpServletRequest request, MockHttpServletResponse response) {
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        ProviderProperty2Action action = new ProviderProperty2Action();
        action.init();
        return action;
    }

    @SuppressWarnings("unchecked")
    private List<String> writeDispatches() throws Exception {
        ProviderProperty2Action action = actionFor(new MockHttpServletRequest(), new MockHttpServletResponse());
        Field field = ProviderProperty2Action.class.getDeclaredField("methodMap");
        field.setAccessible(true);
        return ((Map<String, ?>) field.get(action)).keySet().stream().filter(name -> !name.startsWith("view")).sorted().toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT"})
    @DisplayName("should answer 405 and touch nothing for every write dispatch on a method other than POST")
    void shouldRefuseEveryWriteDispatch_whenNotPost(String httpMethod) throws Exception {
        List<String> dispatches = writeDispatches();
        assertThat(dispatches).hasSizeGreaterThanOrEqualTo(32)
                .contains("save", "remove", "saveOntarioMDId", "saveLabRecallPrefs", "saveLabMacroPrefs",
                        "saveTicklerTaskAssignee", "setOfferFileForOthersPref", "setAllowOthersFileForYouPref");

        SoftAssertions soft = new SoftAssertions();
        for (String dispatch : dispatches) {
            MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/setProviderStaleDate");
            request.setParameter("method", dispatch);
            MockHttpServletResponse response = new MockHttpServletResponse();

            String result = actionFor(request, response).execute();

            soft.assertThat(result).as("%s %s result", httpMethod, dispatch).isEqualTo("none");
            soft.assertThat(response.getStatus()).as("%s %s status", httpMethod, dispatch).isEqualTo(405);
            soft.assertThat(response.getHeader("Allow")).as("%s %s Allow", httpMethod, dispatch).isEqualTo("POST");
        }
        soft.assertAll();
        verifyNoInteractions(properties, providers, providerManager, security);
    }

    @Test
    @DisplayName("should still run a write dispatch on POST")
    void shouldRunWriteDispatch_onPost() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/setProviderStaleDate");
        request.setParameter("method", "remove");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(actionFor(request, response).execute()).isEqualTo("success");

        verify(properties).getProp("owner", io.github.carlos_emr.carlos.commn.model.UserProperty.STALE_NOTEDATE);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should keep the view dispatches open to GET")
    void shouldOpenViewDispatch_onGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/setProviderStaleDate");
        request.setParameter("method", "viewDefaultSex");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(actionFor(request, response).execute()).isNotEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
