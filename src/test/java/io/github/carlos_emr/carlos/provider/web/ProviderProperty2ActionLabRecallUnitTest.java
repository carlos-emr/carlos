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
package io.github.carlos_emr.carlos.provider.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issues #4720 and #4721: Preferences ▸ Lab Recall Settings. The page stored the message subject under a
 * name the messenger does not read, so a lab's Recall message never got it; and its banner printed the
 * message key (and a stray {@code "/>}) instead of the message, with the refused-delegate key missing from
 * every bundle.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@DisplayName("Lab Recall Settings subject and banner (#4720, #4721)")
class ProviderProperty2ActionLabRecallUnitTest extends CarlosUnitTestBase {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/provider/setLabRecallPrefs.jsp");
    private static final String OWNER = "999998";
    private static final String SUBJECT = "FAKE-PW recall: please book a follow-up";

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/setProviderStaleDate");
    private final UserPropertyDAO properties = mock(UserPropertyDAO.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private ProviderProperty2Action action;

    @BeforeEach
    void setUp() {
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn(OWNER);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        registerMock(UserPropertyDAO.class, properties);
        registerMock(ProviderDao.class, providers);
        registerMock(ProviderManager2.class, mock(ProviderManager2.class));
        registerMock(SecurityInfoManager.class, security);
        Provider active = new Provider();
        active.setProviderNo(OWNER);
        active.setLastName("FAKE-PW");
        active.setFirstName("Provider");
        when(providers.getProviders(true)).thenAnswer(call -> new ArrayList<>(List.of(active)));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        action = new ProviderProperty2Action();
    }

    @AfterEach
    void tearDown() {
        loggedIn.close();
        servlet.close();
    }

    private static UserProperty property(int id, String name, String value) {
        UserProperty property = new UserProperty();
        property.setId(id);
        property.setProviderNo(OWNER);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    private void submit(String delegate) {
        request.setParameter("labRecallDelegate.value", delegate);
        request.setParameter("labRecallTicklerPriority.value", "High");
        action.setLabRecallMsgSubject(property(0, null, SUBJECT));
    }

    @Test
    @DisplayName("should save the subject under the name the Recall message reads")
    void shouldSaveSubject_underMessengerName() {
        submit(OWNER);

        assertThat(action.saveLabRecallPrefs()).isEqualTo("genLabRecallPrefs");

        ArgumentCaptor<UserProperty> saved = ArgumentCaptor.forClass(UserProperty.class);
        verify(properties, atLeastOnce()).saveProp(saved.capture());
        assertThat(saved.getAllValues())
                .filteredOn(p -> UserProperty.LAB_RECALL_MSG_SUBJECT.equals(p.getName()))
                .extracting(UserProperty::getValue).containsExactly(SUBJECT);
        assertThat(saved.getAllValues()).extracting(UserProperty::getName)
                .doesNotContain(UserProperty.LAB_RECALL_MSG_SUBJECT_LEGACY);
        assertThat(request.getAttribute("providermsgSuccess")).isEqualTo("provider.setLabRecall.msgSuccess");
    }

    @Test
    @DisplayName("should move a subject saved under the old name to the new one")
    void shouldDeleteLegacySubject_whenSaving() {
        UserProperty legacy = property(7, UserProperty.LAB_RECALL_MSG_SUBJECT_LEGACY, "FAKE-PW old subject");
        when(properties.getProp(OWNER, UserProperty.LAB_RECALL_MSG_SUBJECT_LEGACY)).thenReturn(legacy);
        submit(OWNER);

        action.saveLabRecallPrefs();

        verify(properties).delete(legacy);
    }

    @Test
    @DisplayName("should show a subject saved under the old name until it is saved again")
    void shouldReadLegacySubject_whenOnlyOldRowExists() {
        when(properties.getProp(OWNER, UserProperty.LAB_RECALL_MSG_SUBJECT_LEGACY))
                .thenReturn(property(7, UserProperty.LAB_RECALL_MSG_SUBJECT_LEGACY, "FAKE-PW old subject"));

        action.viewLabRecall();

        assertThat(((UserProperty) request.getAttribute("subject")).getValue()).isEqualTo("FAKE-PW old subject");
    }

    @Test
    @DisplayName("should refuse a delegate that is not an active provider, as an error, and save nothing")
    void shouldRefuseInactiveDelegate_asError() {
        submit("FAKE-PW-none");

        action.saveLabRecallPrefs();

        assertThat(request.getAttribute("status")).isEqualTo("error");
        assertThat(request.getAttribute("providermsgSuccess")).isEqualTo("provider.setLabRecall.msgInvalidDelegate");
        verify(properties, never()).saveProp(any(UserProperty.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "es", "fr", "pl", "pt_BR"})
    @DisplayName("should have every banner message the page can show in each bundle")
    void shouldHaveBannerMessages_inEveryBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            bundle.load(in);
        }
        for (String key : List.of("provider.setLabRecall.msgSuccess", "provider.setLabRecall.msgDeleted",
                "provider.setLabRecall.msgInvalidDelegate")) {
            assertThat(bundle.getProperty(key)).as(locale + " " + key).isNotBlank();
        }
    }

    @Test
    @DisplayName("should print the banner message from the bundle, as an error for a refused delegate")
    void shouldPrintBannerMessage_fromBundle() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8);
        assertThat(page).contains("<span id=\"AlertText\"><%=bundle.getString(providermsgSuccess)%></span>");
        assertThat(page).doesNotContain("<%=providermsgSuccess%>\"/>");
        assertThat(page).contains("\"error\".equals(request.getAttribute(\"status\")) ? \"alert-danger\" : \"alert-success\"");
    }
}
