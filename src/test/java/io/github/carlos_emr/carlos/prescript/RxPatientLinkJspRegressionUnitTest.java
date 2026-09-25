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
package io.github.carlos_emr.carlos.prescript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-patient Rx state (#3875): links into Rx pages must name their patient, because a request that
 * names none falls back to the most recently opened Rx patient. The static-script page lists a
 * patient's saved drugs and offers to re-prescribe them, so it must never use that fallback.
 *
 * @since 2026-09-24
 */
@DisplayName("Rx patient-link JSP regressions")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxPatientLinkJspRegressionUnitTest {

    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");
    private static final Pattern STATIC_SCRIPT_LINK = Pattern.compile("/rx/ViewStaticScript2\\?[^\"']*");

    private static String read(String relative) throws IOException {
        return Files.readString(JSP_ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should name the patient on every static-script link")
    void shouldNamePatient_onEveryStaticScriptLink() throws IOException {
        for (String jsp : new String[] {"casemgmt/prescriptions.jsp", "rx/PrintDrugProfile2.jsp", "rx/ListDrugs.jsp",
                "casemgmt/ChartNotesAjax.jsp"}) {
            Matcher links = STATIC_SCRIPT_LINK.matcher(read(jsp));
            int found = 0;
            while (links.find()) {
                found++;
                assertThat(links.group()).as(jsp).contains("demographicNo=");
            }
            assertThat(found).as(jsp).isPositive();
        }
    }

    @Test
    @DisplayName("should keep the drug-profile Show All / Show Current toggles on the page's patient")
    void shouldNamePatient_onDrugProfileToggleLinks() throws IOException {
        int found = 0;
        for (String line : read("rx/PrintDrugProfile2.jsp").split("\\R")) {
            if (!line.contains(">Show All</a>") && !line.contains(">Show Current</a>")) {
                continue;
            }
            found++;
            assertThat(line).contains("/rx/ViewPrintDrugProfile2?").contains(
                    "demographicNo=<carlos:encode value='<%= profileDemographicNo %>' context=\"uriComponent\"/>");
        }
        // Show All and Show Current, above and below the drug list.
        assertThat(found).isEqualTo(4);
    }

    @Test
    @DisplayName("should post the add-allergy form for its patient and redirect back to that patient")
    void shouldNamePatient_onAddAllergyFormAndRedirect() throws IOException {
        String jsp = read("rx/AddReaction2.jsp");
        assertThat(jsp).contains("name=\"formDemographicNo\"")
                .contains("<input type=\"hidden\" name=\"demographicNo\"");

        String struts = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-prescription.xml"),
                StandardCharsets.UTF_8);
        // The shared "Patient" session attribute is gone (#3875); the action exposes the patient.
        assertThat(struts).doesNotContain("#session.Patient")
                .contains("/rx/showAllergy?demographicNo=${demographicNo}");
    }

    @Test
    @DisplayName("should stop every Rx page that resolves its bean before using a missing one")
    void shouldStopRendering_whenRxBeanDoesNotResolve() throws IOException {
        // A patient whose Rx is not open, or a malformed/conflicting demographicNo, resolves no
        // bean. The pages redirected but kept executing and dereferenced the null bean (a 500).
        String unguarded = "if (rxResolvedBean != null) { pageContext.setAttribute(\"RxSessionBean\", rxResolvedBean); } }";
        String guarded = "if (rxResolvedBean != null) { pageContext.setAttribute(\"RxSessionBean\", rxResolvedBean); }"
                + " else { response.sendRedirect(\"error.html\"); return; } }";
        int pages = 0;
        try (java.util.stream.Stream<Path> files = Files.list(JSP_ROOT.resolve("rx"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".jsp")).toList()) {
                String jsp = Files.readString(file, StandardCharsets.UTF_8);
                assertThat(jsp).as(file.toString()).doesNotContain(unguarded);
                if (jsp.contains(guarded)) {
                    pages++;
                }
                if (jsp.contains("RxSessionBean bean2 = ")) {
                    assertThat(jsp).as(file.toString()).contains("if (bean2 == null) {");
                }
                // Every Rx page authorises the patient it renders, whatever route forwarded to it:
                // the plain resolver is never used to pick the page's bean (#3908).
                assertThat(jsp).as(file.toString()).doesNotContain("RxSessionBeanResolver.resolve(request)");
                // A page that opens a patient's bean itself authorises that patient first; the
                // addFavoriteStaticScript result forwards here having checked only the drug's patient.
                assertThat(jsp).as(file.toString()).doesNotContain("RxSessionBeanResolver.activate(request");
                // Choosing a drug stages a card: no GET link may reach rx/chooseDrug.
                assertThat(jsp).as(file.toString()).doesNotContain("/rx/chooseDrug?");
                // Reprint state is per patient; the old session-wide reprint attributes rendered one
                // patient's reprinted script in another patient's window (#3908).
                assertThat(jsp).as(file.toString())
                        .doesNotContain("\"tmpBeanRX\"")
                        .doesNotContain("getAttribute(\"rePrint\")")
                        .doesNotContain("getAttribute(\"comment\")");
                // Rx pages set <base href=".../carlos/">, so a relative styles.css resolves to the
                // missing /carlos/styles.css (an HTML 404 the browser refuses as a stylesheet).
                assertThat(jsp).as(file.toString()).doesNotContain("href=\"styles.css\"");
            }
        }
        assertThat(pages).isGreaterThanOrEqualTo(16);
    }

    @Test
    @DisplayName("should answer a refused Rx request with the security-error page instead of a 500")
    void shouldMapRxSecurityExceptions_toSecurityErrorPage() throws IOException {
        String struts = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-prescription.xml"),
                StandardCharsets.UTF_8);
        assertThat(struts)
                .contains("<result name=\"securityError\">/WEB-INF/jsp/error/securityError.jsp</result>")
                .contains("<exception-mapping exception=\"java.lang.SecurityException\" result=\"securityError\"/>");
    }

    @Test
    @DisplayName("should name this page's patient on every pharmacy modal URL")
    void shouldNamePatient_onPharmacyModalUrls() throws IOException {
        String jsp = read("rx/SelectPharmacy2.jsp");
        int start = jsp.indexOf("function openPharmacyModal(url) {");
        int end = jsp.indexOf("iframe.src = url;", start);
        assertThat(start).isPositive();
        assertThat(jsp.substring(start, end)).contains("\"demographicNo=\" + encodeURIComponent(demo)");
    }

    @Test
    @DisplayName("should open the static-script page only for an explicitly named patient")
    void shouldRefuseFallbackPatient_onStaticScriptPage() throws IOException {
        String jsp = read("rx/StaticScript2.jsp");

        // Its re-prescribe calls stage for the page's patient only.
        // Staging records the ReRx source itself; no separate, un-awaited list update (#3908).
        assertThat(jsp).contains("RxSessionBeanResolver.requestedDemographicNo(request)")
                .doesNotContain("RxSessionBeanResolver.resolve(request)")
                .doesNotContain("parameterValue=updateReRxDrug")
                .contains("/rx/rePrescribe2?method=saveReRxDrugIdToStash")
                .contains("\"&demographicNo=\" + staticScriptDemographicNo")
                .contains("/rx/searchDrug?demographicNo=\" + staticScriptDemographicNo")
                // A refused stage reports the refusal instead of opening the search.
                .contains("if (!response || !response.ok || response.redirected) {")
                // The token is read when the POST is sent; reading it in <head> always got '' (#3908).
                .contains("var csrfToken = await staticScriptCsrfToken();")
                .contains("<%@ include file=\"/WEB-INF/jspf/csrf-token.jspf\" %>")
                .doesNotContain("var csrfEl = document.querySelector")
                .contains("<fmt:message key=\"StaticScript.js.reRxRefused\" var=\"msg_reRxRefused\"/>");
    }

    @Test
    @DisplayName("should encode stored allergy and favourite text for each sidebar output context")
    void shouldEncodeStoredSidebarText() throws IOException {
        for (String name : new String[] {"rx/SideLinksEditFavorites2.jsp", "rx/SideLinksNoEditFavorites.jsp",
                "rx/SideLinksNoEditFavorites2.jsp"}) {
            String jsp = read(name);
            assertThat(jsp).as(name).contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>");
            for (String value : new String[] {"allergies[j].getDescription()", "allergies[j].getReaction()",
                    "favorites[j].getFavoriteName()"}) {
                assertThat(jsp).as(name + " attribute " + value)
                        .contains("<carlos:encode value='<%= " + value + " %>' context=\"htmlAttribute\"/>");
            }
            for (String value : new String[] {"allergies[j].getShortDesc(13, 8, \"...\")",
                    "favorites[j].getFavoriteName()", "favorites[j].getFavoriteName().substring(0, 10) + \"...\""}) {
                assertThat(jsp).as(name + " text " + value)
                        .contains("<carlos:encode value='<%= " + value + " %>' context=\"html\"/>");
            }
            // A raw title lets persisted quotes create attributes; a raw text label permits markup.
            assertThat(jsp).doesNotContain("title=\"<%= allergies", "title=\"<%= favorites",
                    "<%=allergies[j].getShortDesc", "<%= favorites[j].getFavoriteName() %> <%}");
        }
    }

    @Test
    @DisplayName("should allow shared Rx sidebars with either permission and authorize clinical sections independently")
    void shouldSeparatePrescriptionAndAllergySidebarPermissions() throws IOException {
        for (String name : new String[] {"rx/SideLinksEditFavorites2.jsp", "rx/SideLinksNoEditFavorites.jsp",
                "rx/SideLinksNoEditFavorites2.jsp"}) {
            String jsp = read(name);
            assertThat(jsp).as(name)
                    .contains("RxSessionBean bean2 = RxRequestedPatientAccess.resolveAuthorised(request, \"_rx\", \"r\")")
                    .contains("RxSessionBean rxSidebarAllergyBean = RxRequestedPatientAccess.resolveAuthorised(request, \"_allergy\", \"r\")")
                    .contains("if (bean2 == null) bean2 = rxSidebarAllergyBean;")
                    .contains("Allergy[] allergies = rxSidebarAllergyBean == null ? new Allergy[0]")
                    .contains("<% if (rxSidebarAllergyBean != null) { %>")
                    .contains("rxSidebarAllergyBean.getDemographicNo()).getActiveAllergies()");
            // Missing or unauthorized patient still stops the fragment before any data access.
            assertThat(jsp.indexOf("if (bean2 == null) {"))
                    .isGreaterThan(jsp.indexOf("if (bean2 == null) bean2 = rxSidebarAllergyBean;"))
                    .isLessThan(jsp.indexOf("Allergy[] allergies"));
        }
        assertThat(read("rx/SideLinksEditFavorites2.jsp"))
                .contains("<% if (rxSidebarMayReadRx) { %>")
                .contains("<% if (RxRequestedPatientAccess.resolveAuthorised(request, \"_rxresearch\", \"r\") != null) { %>");
    }

    @Test
    @DisplayName("should keep favourite and add-favourite navigations on the window's patient")
    void shouldNamePatient_onStagingPageNavigations() throws IOException {
        for (String jsp : new String[] {"rx/SideLinksEditFavorites2.jsp", "rx/SideLinksNoEditFavorites.jsp",
                "rx/SideLinksNoEditFavorites2.jsp"}) {
            assertThat(read(jsp)).as(jsp)
                    .contains("/rx/searchDrug?demographicNo=<%= bean2.getDemographicNo() %>&usefav=true");
        }
        assertThat(read("rx/SearchDrug3.jsp"))
                .contains("window.location.href = RxPatientContext.withPatient(ctx + \"/rx/searchDrug\");")
                .contains("onFailure: reportRefusedSave");
    }

    @Test
    @DisplayName("should name the patient on every allergy-page link back to the allergy list")
    void shouldNamePatient_onAllergyBackLinks() throws IOException {
        String breadcrumb = "/rx/showAllergy?demographicNo=<carlos:encode value='<%= String.valueOf(bean.getDemographicNo()) %>'"
                + " context=\"uriComponent\"/>\">";
        for (String jsp : new String[] {"rx/AddReaction2.jsp", "rx/ChooseAllergy2.jsp"}) {
            String page = read(jsp);
            // showAllergy refuses a request that names no patient (#3908).
            assertThat(page).as(jsp).doesNotContain("/rx/showAllergy\">", "getContextPath() + \"/rx/showAllergy\";")
                    .contains(breadcrumb)
                    .contains("\"/rx/showAllergy?demographicNo=\" + bean.getDemographicNo();");
        }
        // submitAddReaction() calls form.submit(), which the patient-context submit listener never sees.
        assertThat(read("rx/ChooseAllergy2.jsp")).contains(
                "<input type=\"hidden\" name=\"demographicNo\" value=\"<%= bean == null ? \"\" : String.valueOf(bean.getDemographicNo()) %>\"/>");
    }

    @Test
    @DisplayName("should keep programmatic Rx navigations on the page's patient")
    void shouldNamePatient_onProgrammaticNavigations() throws IOException {
        assertThat(read("rx/WriteScript.jsp")).doesNotContain("<c:redirect url=\"/rx/searchDrug\"/>")
                .contains("<c:param name=\"demographicNo\" value=\"${bean.demographicNo}\"/>");
        String favourites = read("rx/SideLinksEditFavorites2.jsp");
        // "Edit" opens the read-only ViewEditFavorites2 gate, not the POST-only favourite write (#3908).
        for (String route : new String[] {"ViewEditFavorites2", "copyFavorite"}) {
            assertThat(favourites).contains("/rx/" + route + "?demographicNo=<carlos:encode value='<%= String.valueOf(bean2.getDemographicNo()) %>'");
        }
        // An iframe src assignment is not tagged by rx-patient-context.js (medication history modal).
        assertThat(read("rx/SearchDrug3.jsp")).contains(
                "this.waitifrm.setAttribute(\"src\",RxPatientContext.withPatient(displaySRC+\"?randomId=\"+encodeURIComponent(randomId)));");
    }

    @Test
    @DisplayName("should render the Rx Print patient chooser without any per-patient Rx state")
    void shouldRenderPrintChooser_withoutRxPatient() throws IOException {
        // The chooser runs before a patient is chosen; resolving a bean sent a session with no open
        // Rx patient to error.html before the search form (#3908).
        String jsp = read("rx/Print.jsp");
        assertThat(jsp).doesNotContain("RxSessionBeanResolver").doesNotContain("RxSessionBean")
                .doesNotContain("error.html")
                .contains("/rx/searchPatient\" method=\"post\"").contains("name=\"surname\"");
    }
}
