/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMappingDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMapping;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders the vaccine-choice popup, the {@code success} result of
 * {@code prevention/ViewAddPreventionDataDisambiguate}, for a patient the page looks up itself.
 * 8872cdd32c67 (#6271) moved the heading of patient name, sex and age from a raw
 * {@code <%=nameage%>} to {@code <carlos:encode>}: a name holding markup must read back as the
 * literal text, with no element made from it, while an ordinary name with an apostrophe or accent
 * reads back unchanged. Before #6336 {@code AddPreventionDataDisambiguateEncodingUnitTest} only
 * read the JSP source and the packaged checks that open the popup never read its heading.
 *
 * <p>The page's scriptlet calls {@code PreventionData.getDemographicDateOfBirth}, so this render
 * can be the first use of {@link PreventionData} in its Surefire fork, whose static initializer
 * resolves {@link PreventionDao}, {@link PreventionExtDao} and {@link PartialDateDao} once. All
 * three are registered here so that initialization succeeds rather than leaving the class
 * unusable for later tests; the date-of-birth lookup itself reads {@link DemographicManager} on
 * each call. The tests that use those static DAOs with a real context
 * ({@code PreventionDataWriteIntegrationTest}, {@code PreventionLegacySqlIntegrationTest}) set
 * them from their own context and restore them, as {@code PreventionDataExtLookupUnitTest} does
 * with its mocks.</p>
 */
@Tag("integration")
@Tag("prevention")
class AddPreventionDataDisambiguateJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/prevention/AddPreventionDataDisambiguate.jsp";
    private static final int MARKUP_PATIENT = 1001;
    private static final int ACCENTED_PATIENT = 1002;
    private static final String MARKUP_LAST_NAME = "FAKE<b>&amp;";
    private static final String ACCENTED_LAST_NAME = "FAKE O'Brien-L\u00E9vesque";
    private static final String FIRST_NAME = "FAKE-First";
    private static final String SNOMED_ID = "FAKE-SNOMED-1";

    @TempDir
    static Path directory;
    private static JspRenderServer server;
    private static Map<Integer, Demographic> patients;

    @BeforeAll
    static void startServer() throws Exception {
        patients = Map.of(MARKUP_PATIENT, patient(MARKUP_PATIENT, MARKUP_LAST_NAME),
                ACCENTED_PATIENT, patient(ACCENTED_PATIENT, ACCENTED_LAST_NAME));
        // Both lookups the page makes (date of birth, then name/sex) use the Integer overload.
        DemographicManager demographics = mock(DemographicManager.class);
        when(demographics.getDemographic(any(), any(Integer.class)))
                .thenAnswer(invocation -> patients.get(invocation.getArgument(1)));

        CVCMapping mapping = new CVCMapping();
        mapping.setOscarName("Tdap");
        mapping.setCvcSnomedId(SNOMED_ID);
        CVCMappingDao mappings = mock(CVCMappingDao.class);
        when(mappings.findMultipleByOscarName("Tdap")).thenReturn(List.of(mapping));
        CVCImmunization vaccine = new CVCImmunization();
        vaccine.setSnomedConceptId(SNOMED_ID);
        vaccine.setPicklistName("FAKE Tdap vaccine");
        CVCImmunizationDao vaccines = mock(CVCImmunizationDao.class);
        when(vaccines.findBySnomedConceptId(SNOMED_ID)).thenReturn(vaccine);

        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(DemographicManager.class, demographics)
                .bean(DemographicCustDao.class, mock(DemographicCustDao.class))
                .bean(CVCMappingDao.class, mappings)
                .bean(CVCImmunizationDao.class, vaccines)
                .bean(PreventionDao.class, mock(PreventionDao.class))
                .bean(PreventionExtDao.class, mock(PreventionExtDao.class))
                .bean(PartialDateDao.class, mock(PartialDateDao.class))
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldShowThePatientHeadingAsText_whenTheNameHoldsMarkup() throws Exception {
        String ageBefore = age(MARKUP_PATIENT);
        JspRenderServer.RenderedPage page = openPicker(MARKUP_PATIENT);
        String ageAfter = age(MARKUP_PATIENT);

        Element heading = heading(page.document());
        assertThat(heading.text()).isIn(MARKUP_LAST_NAME + ", " + FIRST_NAME + " F " + ageBefore,
                MARKUP_LAST_NAME + ", " + FIRST_NAME + " F " + ageAfter);
        assertThat(heading.children()).isEmpty();
        assertThat(page.document().select("table.TopStatusBar b")).isEmpty();
        assertThat(page.body())
                .contains("FAKE&lt;b&gt;&amp;amp;, " + FIRST_NAME + " F ")
                .doesNotContain("FAKE<b>");
    }

    @Test
    void shouldShowAnOrdinaryNameUnchanged_inThePatientHeading() throws Exception {
        String ageBefore = age(ACCENTED_PATIENT);
        Document html = openPicker(ACCENTED_PATIENT).document();
        String ageAfter = age(ACCENTED_PATIENT);

        assertThat(heading(html).text()).isIn(ACCENTED_LAST_NAME + ", " + FIRST_NAME + " F " + ageBefore,
                ACCENTED_LAST_NAME + ", " + FIRST_NAME + " F " + ageAfter);
        // The rest of the popup still lists the vaccine to choose, linking on with the patient.
        Element choice = html.selectFirst("td.MainTableRightColumn li a");
        assertThat(choice.text()).isEqualTo("FAKE Tdap vaccine");
        assertThat(choice.attr("href")).isEqualTo(JspRenderServer.CONTEXT_PATH
                + "/prevention/ViewAddPreventionData?snomedId=" + SNOMED_ID + "&prevention=Tdap&demographic_no="
                + ACCENTED_PATIENT + "&prevResultDesc=");
    }

    /** What ViewAddPreventionDataDisambiguate forwards: the patient and the prevention type. */
    private static JspRenderServer.RenderedPage openPicker(int demographicNo) throws Exception {
        return server.request(PAGE)
                .parameter("demographic_no", String.valueOf(demographicNo))
                .parameter("prevention", "Tdap")
                .render(AddPreventionDataDisambiguateJspIntegrationTest::signIn)
                .assertOk();
    }

    /** The first cell of the status bar, where the page writes name, sex and age. */
    private static Element heading(Document html) {
        return html.selectFirst("table.TopStatusBar td");
    }

    /** The age the page computes, from the same birth date and the same utility. */
    private static String age(int demographicNo) {
        return UtilDateUtilities.calcAgeAtDate(patients.get(demographicNo).getBirthDay().getTime(), new Date());
    }

    private static Demographic patient(int demographicNo, String lastName) {
        Demographic patient = new Demographic();
        patient.setDemographicNo(demographicNo);
        patient.setLastName(lastName);
        patient.setFirstName(FIRST_NAME);
        patient.setSex("F");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("15");
        return patient;
    }

    /** The session {@code <security:oscarSec>} reads; the bean grants the role read on _prevention. */
    private static void signIn(HttpServletRequest request) {
        HttpSession session = request.getSession();
        session.setAttribute("userrole", "doctor");
        session.setAttribute("user", "999998");
        LoggedInInfo clinician = new LoggedInInfo();
        clinician.setLoggedInProvider(new Provider("999998"));
        LoggedInInfo.setLoggedInInfoIntoSession(session, clinician);
    }
}
