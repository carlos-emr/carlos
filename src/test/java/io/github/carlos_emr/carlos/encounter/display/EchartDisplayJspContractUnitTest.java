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
package io.github.carlos_emr.carlos.encounter.display;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wiring between the eChart display settings and the pages that honour them, so a later
 * edit cannot silently drop a setting: the Preferences page offers every setting as a Yes/No pair
 * with a translated label, the save path writes them, and each chart surface reads its own setting.
 * Behaviour is covered by the unit and Playwright checks; this test guards the seams between them.
 */
@Tag("unit")
@Tag("fast")
@Tag("encounter")
@DisplayName("eChart display settings JSP wiring")
class EchartDisplayJspContractUnitTest extends CarlosUnitTestBase {

    private static final Path WEBAPP = Path.of("src", "main", "webapp");
    private static final Path PREFERENCES_JSP = WEBAPP.resolve("WEB-INF/jsp/provider/providerpreference.jsp");
    private static final Path HEADER_JSP = WEBAPP.resolve("WEB-INF/jsp/casemgmt/newEncounterHeader.jsp");
    private static final Path LAYOUT_JSP = WEBAPP.resolve("WEB-INF/jsp/casemgmt/newEncounterLayout.jsp");
    private static final Path CHART_NOTES_JSP = WEBAPP.resolve("WEB-INF/jsp/casemgmt/ChartNotes.jsp");
    private static final Path CASE_VIEW_JSP = WEBAPP.resolve("WEB-INF/jsp/casemgmt/newCaseManagementView.jsp");
    private static final Path CASE_VIEW_JS_JSP = WEBAPP.resolve("js/newCaseManagementView.js.jsp");
    private static final Path LEFT_NAV_JSP = WEBAPP.resolve("WEB-INF/jsp/encounter/LeftNavBarDisplay.jsp");
    private static final Path PROPERTY_ACTION = Path.of("src", "main", "java", "io", "github", "carlos_emr",
            "carlos", "provider", "web", "ProviderPropertyAction.java");
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pt_BR", "pl");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

    @ParameterizedTest
    @EnumSource(EchartDisplayPreference.class)
    @DisplayName("should offer each setting on the Preferences page as a labelled Yes/No pair")
    void shouldOfferEachSetting_asLabelledYesNoPair(EchartDisplayPreference preference) throws IOException {
        String page = source(PREFERENCES_JSP);

        assertThat(page)
                .contains("echartYesNo(EchartDisplayPreference." + preference.name() + ", echartPrefs")
                .contains("<fmt:message key=\"" + preference.labelKey() + "\"/>")
                .contains("id=\"" + preference.propertyName() + "-label\"")
                .contains("aria-labelledby=\"" + preference.propertyName() + "-label\"");
    }

    @ParameterizedTest
    @EnumSource(EchartDisplayPreference.class)
    @DisplayName("should translate each setting's label in every shipped bundle")
    void shouldTranslateEachLabel_inEveryBundle(EchartDisplayPreference preference) throws IOException {
        for (String locale : LOCALES) {
            assertThat(bundle(locale).getProperty(preference.labelKey()))
                    .as("%s in oscarResources_%s", preference.labelKey(), locale)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("should render the radio pair with OSCAR 19's true/false values and ids")
    void shouldRenderRadioPair_withOscarValuesAndIds() throws IOException {
        String page = source(PREFERENCES_JSP);

        assertThat(page)
                .contains("id=\"secEchart\"")
                .contains("<fmt:message key=\"provider.providerpreference.section.echart\"/>")
                .contains("EchartDisplayPreferences echartPrefs = SpringUtils.getBean(EchartDisplayPreferenceResolver.class).resolve(providerNo);")
                .contains("\"-true\\\" name=\\\"\" + name + \"\\\" value=\\\"true\\\"\"")
                .contains("\"-false\\\" name=\\\"\" + name + \"\\\" value=\\\"false\\\"\"")
                .contains("SafeEncode.forHtmlContent(yes)")
                .contains("SafeEncode.forHtmlContent(no)");
    }

    @Test
    @DisplayName("should save the section from the shared Save All path")
    void shouldSaveSection_fromSaveAllPath() throws IOException {
        assertThat(Files.readString(PROPERTY_ACTION, StandardCharsets.UTF_8))
                .contains("SpringUtils.getBean(EchartDisplayPreferencePersister.class).persist(providerNo, request::getParameter);");
    }

    @Test
    @DisplayName("should build the header bar from the viewer's settings")
    void shouldBuildHeader_fromViewerSettings() throws IOException {
        String header = source(HEADER_JSP);

        assertThat(header)
                .contains("SpringUtils.getBean(EchartDisplayPreferenceResolver.class)")
                .contains(".assemble(viewerProviderNo, demographic, echartPrefs, browserLocale)")
                .contains("<c:when test=\"${echartHeader.showDateOfBirth}\">")
                .contains("<c:if test=\"${not empty echartHeader.hin}\">")
                .contains("<c:if test=\"${not empty echartHeader.email}\">")
                .contains("<c:forEach var=\"phone\" items=\"${echartHeader.phones}\">");
    }

    @Test
    @DisplayName("should make the header phones tel: links only behind the click-to-dial setting")
    void shouldGateTelLinks_onProviderSetting() throws IOException {
        String header = source(HEADER_JSP);

        assertThat(header)
                .contains("<c:set var=\"phoneTelHref\" value=\"${echartHeader.phoneTelLinks ? phone.telHref() : ''}\"/>")
                .contains("href=\"${carlos:forHtmlAttribute(phoneTelHref)}\"")
                .contains("<fmt:message key=\"encounter.header.clickToCall\" var=\"clickToCallMsg\"/>")
                .doesNotContain("href=\"tel:");
        for (String locale : LOCALES) {
            assertThat(bundle(locale).getProperty("encounter.header.clickToCall"))
                    .as("encounter.header.clickToCall in oscarResources_%s", locale)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("should show the timer only when the provider's setting allows it")
    void shouldGateTimer_onProviderSetting() throws IOException {
        String notes = source(CHART_NOTES_JSP);
        int gate = notes.indexOf(".resolve(loggedInInfo.getLoggedInProviderNo()).showTimer()) { %>");
        int timer = notes.indexOf("id=\"aTimer\"");
        int toggle = notes.indexOf("id=\"toggleTimer\"");
        int close = notes.indexOf("<% } %>", toggle);

        assertThat(gate).as("timer gate").isPositive();
        assertThat(timer).isGreaterThan(gate);
        assertThat(toggle).isGreaterThan(timer);
        assertThat(close).as("gate closes after both timer buttons").isGreaterThan(toggle);
    }

    @Test
    @DisplayName("should render the Ocean placeholder only through the combined clinic and provider check")
    void shouldGateOcean_onClinicAndProvider() throws IOException {
        String view = source(CASE_VIEW_JSP);
        int gate = view.indexOf("boolean echartShowOcean = caseManagementViewEchartResolver.isOceanShown(");
        int placeholder = view.indexOf("<div id=\"ocean_placeholder\"");

        assertThat(gate).isPositive();
        assertThat(placeholder).isGreaterThan(gate);
        assertThat(view).doesNotContain("echartShowOceanPref");
    }

    @Test
    @DisplayName("should request the doctor panels first in the left column, each behind its own setting")
    void shouldRequestDoctorPanels_behindTheirSettings() throws IOException {
        String script = source(CASE_VIEW_JS_JSP);
        int panels = script.indexOf("var echartDoctorPanels = [<% if (navEchartPrefs.showFamilyDoctor()) { %>");
        int family = script.indexOf("{path: \"/encounter/displayFamilyDoctor\", id: \"fDoc\"},<% } %>"
                + "<% if (navEchartPrefs.showReferringDoctor()) { %>");
        int referring = script.indexOf("{path: \"/encounter/displayReferringDoctor\", id: \"rDoc\"},<% } %>");
        int loader = script.indexOf("leftNavBar.unshift(ctx + echartDoctorPanels[panelIdx].path + \"?hC=\" + Colour.contacts);");

        assertThat(panels).as("panels declared from the provider's settings").isPositive();
        assertThat(family).as("Family Doctor first, behind its setting").isGreaterThan(panels);
        assertThat(referring).as("Referring Doctor second, behind its setting").isGreaterThan(family);
        assertThat(loader).as("the loader puts the declared panels at the top").isGreaterThan(referring);
    }

    @Test
    @DisplayName("should render folder rows only when the panel is filed in folders")
    void shouldRenderFolders_onlyWhenPanelIsFiled() throws IOException {
        String nav = source(LEFT_NAV_JSP);

        assertThat(nav)
                .contains("if (dao.isFiledInFolders()) {")
                .contains("numDisplayed = displayFolders(dao, jscode, displayThreshold, reloadURL.toString(), request, out);")
                .contains("aria-expanded=")
                .contains("SafeEncode.forHtmlContent(name)");
    }

    @Test
    @DisplayName("should title the chart window and label the bar as OSCAR 19 did")
    void shouldMatchOscar19_inWindowTitleAndBarLabels() throws IOException {
        String layout = source(LAYOUT_JSP);
        String header = source(HEADER_JSP);

        assertThat(layout)
                .contains("<title><fmt:message key=\"encounter.Index.windowTitle\"/> - <oscar:nameage demographicNo=\"${demographicNo}\"/></title>");
        assertThat(Pattern.compile("<title>").matcher(layout).results().count()).as("title elements").isEqualTo(1);
        assertThat(header)
                .contains("<fmt:message key=\"encounter.Index.msgMRP\"/>")
                .contains("key=\"encounter.Header.nextAppt\"")
                .contains("id=\"chartCalculatorsLink\"")
                .doesNotContain("/encounter/ViewAbout")
                .doesNotContain("/encounter/ViewLicense")
                .doesNotContain("global.help");
        for (String locale : LOCALES) {
            assertThat(bundle(locale).getProperty("encounter.Index.windowTitle"))
                    .as("encounter.Index.windowTitle in oscarResources_%s", locale)
                    .isEqualTo("E");
        }
    }

    private static String source(Path path) throws IOException {
        assertThat(Files.isRegularFile(path)).as("%s must exist", path).isTrue();
        return JSP_COMMENT.matcher(Files.readString(path, StandardCharsets.UTF_8)).replaceAll("");
    }

    private static Properties bundle(String locale) throws IOException {
        try (InputStream is = EchartDisplayJspContractUnitTest.class.getResourceAsStream("/oscarResources_" + locale + ".properties")) {
            assertThat(is).as("bundle %s", locale).isNotNull();
            Properties properties = new Properties();
            properties.load(is);
            return properties;
        }
    }
}
