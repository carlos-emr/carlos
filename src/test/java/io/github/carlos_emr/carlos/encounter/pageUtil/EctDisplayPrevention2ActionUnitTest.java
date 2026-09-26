/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.encounter.pageUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.commn.dao.CVCMappingDao;
import io.github.carlos_emr.carlos.commn.model.CVCMapping;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.Prevention;
import io.github.carlos_emr.carlos.prevention.PreventionDS;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the eChart Preventions box row links: each row opens its prevention directly for providers
 * with {@code _prevention w}, and falls back to the full list for read-only providers.
 */
@Tag("unit")
@Tag("prevention")
@DisplayName("eChart Preventions box row links")
class EctDisplayPrevention2ActionUnitTest {

    private static final String LIST_HANDLER =
            "popupPage(700, 960,'prevention42','/carlos/prevention/ViewPreventionIndex?demographic_no=42');return false;";

    @Nested
    @DisplayName("form URL")
    class FormUrl {

        @Test
        @DisplayName("should edit the newest record by id when the patient has one")
        void shouldBuildEditUrl_whenRecordExists() {
            String url = EctDisplayPrevention2Action.buildPreventionItemUrl(
                    "/carlos", "42", "Flu", "Flu shot", "46233009", "917", true);

            assertThat(url).isEqualTo("/carlos/prevention/ViewAddPreventionData?id=917&demographic_no=42");
        }

        @Test
        @DisplayName("should open a blank immunization form preset to the prevention when there is no record")
        void shouldBuildAddUrl_whenNoRecordExists() {
            String url = EctDisplayPrevention2Action.buildPreventionItemUrl(
                    "/carlos", "42", "Td", "Tetanus", "871729003", null, false);

            assertThat(url).isEqualTo("/carlos/prevention/ViewAddPreventionData?snomedId=871729003"
                    + "&prevention=Td&demographic_no=42&prevResultDesc=Tetanus");
        }

        @Test
        @DisplayName("should omit the SNOMED id and send an empty result description for a screening")
        void shouldOmitSnomedId_forScreeningWithoutCode() {
            String url = EctDisplayPrevention2Action.buildPreventionItemUrl(
                    "/carlos", "42", "PAP", null, null, null, false);

            assertThat(url).isEqualTo("/carlos/prevention/ViewAddPreventionData?prevention=PAP&demographic_no=42&prevResultDesc=");
        }

        @Test
        @DisplayName("should route through disambiguation when the prevention maps to several CVC vaccines")
        void shouldUseDisambiguationRoute_whenSeveralCvcMappings() {
            String url = EctDisplayPrevention2Action.buildPreventionItemUrl(
                    "/carlos", "42", "HepAB", "", "SCT1", null, true);

            assertThat(url).startsWith("/carlos/prevention/ViewAddPreventionDataDisambiguate?snomedId=SCT1&prevention=HepAB&");
        }

        @Test
        @DisplayName("should URI-encode query values containing quotes and ampersands")
        void shouldEncodePreventionName_forUriComponent() {
            String url = EctDisplayPrevention2Action.buildPreventionItemUrl(
                    "/carlos", "42", "O'Brien & \"Co\"", "a&b=c", null, null, false);

            assertThat(url).isEqualTo("/carlos/prevention/ViewAddPreventionData?prevention=O%27Brien%20%26%20%22Co%22"
                    + "&demographic_no=42&prevResultDesc=a%26b%3Dc");
        }
    }

    @Nested
    @DisplayName("popup handler")
    class PopupHandler {

        @Test
        @DisplayName("should keep quotes out of the onclick attribute and the window name first")
        void shouldEncodePreventionName_forJavaScriptAndUri() {
            String url = EctDisplayPrevention2Action.buildPreventionItemUrl(
                    "/carlos", "42", "O'Brien & \"Co\"", "x'y", null, null, false);

            String handler = EctDisplayPrevention2Action.buildPopupHandler("addPreventionData42", url);

            // The navbar writes the handler raw into onclick="..."; no quote may terminate
            // the attribute or the single-quoted JavaScript strings.
            assertThat(handler).doesNotContain("\"");
            assertThat(handler.chars().filter(c -> c == '\'').count()).isEqualTo(4);
            assertThat(handler).startsWith("popupPage(600,900,'addPreventionData42','").endsWith("');return false;");
            // LeftNavBarDisplay.jsp registers the first quoted token for the box refresh.
            Matcher window = Pattern.compile("'([^']*)'").matcher(handler);
            assertThat(window.find()).isTrue();
            assertThat(window.group(1)).isEqualTo("addPreventionData42");
        }
    }

    @Nested
    @DisplayName("navbar rows")
    class NavbarRows {

        @Test
        @DisplayName("should link each row to its own prevention when the provider can write preventions")
        void shouldLinkRowsToTheirPrevention_whenProviderCanWrite() throws Exception {
            CVCMappingDao cvcMappingDao = mock(CVCMappingDao.class);
            when(cvcMappingDao.findMultipleByOscarName("HepAB")).thenReturn(List.of(new CVCMapping(), new CVCMapping()));
            when(cvcMappingDao.findMultipleByOscarName("PAP")).thenReturn(List.of());

            NavBarDisplayDAO panel = render(true, cvcMappingDao);

            assertThat(panel.numItems()).isEqualTo(3);
            Map<String, String> handlers = handlersByTitle(panel);
            assertThat(handlers.get("Flu")).isEqualTo("popupPage(600,900,'addPreventionData42','"
                    + "\\/carlos\\/prevention\\/ViewAddPreventionData?id=917\\x26demographic_no=42');return false;");
            assertThat(handlers.get("PAP")).contains("ViewAddPreventionData?prevention=PAP");
            assertThat(handlers.get("HepAB")).contains("ViewAddPreventionDataDisambiguate?snomedId=SCT1");
            // An existing record is edited by id, so it never needs the CVC lookup.
            verify(cvcMappingDao, never()).findMultipleByOscarName("Flu");
        }

        @Test
        @DisplayName("should keep the full-list link on every row when the provider is read-only")
        void shouldKeepListLink_whenProviderIsReadOnly() throws Exception {
            CVCMappingDao cvcMappingDao = mock(CVCMappingDao.class);

            NavBarDisplayDAO panel = render(false, cvcMappingDao);

            assertThat(panel.numItems()).isEqualTo(3);
            handlersByTitle(panel).values().forEach(handler -> assertThat(handler).isEqualTo(LIST_HANDLER));
            verify(cvcMappingDao, never()).findMultipleByOscarName(anyString());
        }

        @Test
        @DisplayName("should keep the heading and right-hand link on the full prevention list")
        void shouldKeepHeadingOnList_whenProviderCanWrite() throws Exception {
            NavBarDisplayDAO panel = render(true, mock(CVCMappingDao.class));

            assertThat(panel.getLeftPopup().url()).isEqualTo("/carlos/prevention/ViewPreventionIndex?demographic_no=42");
            assertThat(panel.getRightPopup().url()).isEqualTo("/carlos/prevention/ViewPreventionIndex?demographic_no=42");
        }

        private NavBarDisplayDAO render(boolean canWrite, CVCMappingDao cvcMappingDao) throws Exception {
            SecurityInfoManager security = mock(SecurityInfoManager.class);
            LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
            when(security.hasPrivilege(loggedInInfo, "_prevention", "r", null)).thenReturn(true);
            when(security.hasPrivilege(loggedInInfo, "_prevention", "w", null)).thenReturn(canWrite);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", loggedInInfo);
            request.setContextPath("/carlos");
            EctSessionBean session = new EctSessionBean();
            session.demographicNo = "42";

            // SpringUtils is mocked first: PreventionData and PreventionDisplayConfig resolve beans in
            // their static initializers, and a failed class initialization poisons the whole fork.
            try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
                 MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
                 MockedStatic<PreventionData> data = mockStatic(PreventionData.class);
                 MockedStatic<PreventionDisplayConfig> display = mockStatic(PreventionDisplayConfig.class)) {
                Prevention prevention = mock(Prevention.class);
                when(prevention.getWarningMsgs()).thenReturn(new HashMap<>());
                PreventionDisplayConfig config = mock(PreventionDisplayConfig.class);
                when(config.getPreventions()).thenReturn(new ArrayList<>(List.of(
                        configured("Flu", "46233009"), configured("PAP", null), configured("HepAB", "SCT1"))));
                when(config.display(any(), any(), eq("42"), anyInt())).thenReturn(true);
                Map<String, Object> fluRecord = new HashMap<>();
                fluRecord.put("id", "917");
                fluRecord.put("refused", "0");

                spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
                spring.when(() -> SpringUtils.getBean(PreventionDS.class)).thenReturn(mock(PreventionDS.class));
                servlet.when(ServletActionContext::getRequest).thenReturn(request);
                display.when(PreventionDisplayConfig::getInstance).thenReturn(config);
                data.when(() -> PreventionData.getPrevention(loggedInInfo, 42)).thenReturn(prevention);
                data.when(() -> PreventionData.getPreventionData(eq(loggedInInfo), anyString(), eq(42)))
                        .thenReturn(new ArrayList<>());
                data.when(() -> PreventionData.getPreventionData(loggedInInfo, "Flu", 42))
                        .thenReturn(new ArrayList<>(List.of(fluRecord)));
                data.when(() -> PreventionData.getPreventionKeyValues("917")).thenReturn(new HashMap<>());

                EctDisplayPrevention2Action action = spy(new EctDisplayPrevention2Action(cvcMappingDao));
                doReturn("Preventions").when(action).getText(anyString());
                NavBarDisplayDAO panel = new NavBarDisplayDAO();

                assertThat(action.getInfo(session, request, panel)).isTrue();
                return panel;
            }
        }

        private HashMap<String, String> configured(String name, String snomedConceptCode) {
            HashMap<String, String> prevention = new HashMap<>();
            prevention.put("name", name);
            prevention.put("desc", name + " description");
            if (snomedConceptCode != null) {
                prevention.put("snomedConceptCode", snomedConceptCode);
            }
            return prevention;
        }

        private Map<String, String> handlersByTitle(NavBarDisplayDAO panel) {
            Map<String, String> handlers = new HashMap<>();
            for (int i = 0; i < panel.numItems(); i++) {
                NavBarDisplayDAO.Item item = panel.getItem(i);
                // Titles carry a one-character status prefix and a space.
                handlers.put(item.getTitle().substring(2), item.getURL());
            }
            return handlers;
        }
    }
}
