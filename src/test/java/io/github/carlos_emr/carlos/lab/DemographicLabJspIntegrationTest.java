/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSourceFieldPresenceResolver;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Renders the patient lab list, lab/DemographicLab.jsp, the {@code success} result of
 * {@code lab/ViewDemographicLab}, for the list without a patient: the mode with a checkbox per row
 * for forwarding and filing, as the lab-forward picker opens it.
 *
 * <p>The page has no {@code pageEncoding}, so Jasper reads it as ISO-8859-1. Each checkbox's
 * visually hidden label ("Select", an em dash, then "Date of Test: " and the date) used to hold a
 * literal U+2014, which that reading served as mojibake to screen readers. 72fedd972c (#6305)
 * wrote it as {@code &#8212;}. This pins the label's text content (the label is visually hidden, so
 * text content, not the rendered text) in English and French. Before #6336 only
 * {@code JspPageEncodingRegressionTest} read the JSP source for it.</p>
 *
 * <p>The rows come from the page's own lookups, run against mocks: {@code CommonLabResultData}
 * asks {@code Hl7textResultsData} for the HL7 reports routed to no patient (demographic 0).</p>
 *
 * <p>Both classes keep DAOs in static fields that their initializers fill from
 * {@code SpringUtils}. Every bean those initializers read is registered, so whichever class this
 * render initializes cannot fail and leave later tests a {@code NoClassDefFoundError}. The two
 * {@code Hl7textResultsData} fields the page reads are pointed at this test's mocks after the
 * server starts and put back afterwards, so a class an earlier test initialized neither serves
 * this render its DAOs nor loses them. If this render is the first to initialize a class, its
 * other fields keep inert mocks, which is the state CommonLabResultDataAcknowledgeUnitTest and
 * other unit tests of these classes leave too; tests needing a particular DAO there set it
 * themselves. The {@code *_LABS} switches the page reads are pinned to the bundled HL7-only
 * configuration for the class and restored afterwards.</p>
 */
@Tag("integration")
@Tag("lab")
class DemographicLabJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/lab/DemographicLab.jsp";
    private static final String PROVIDER = "999998";
    private static final int UNMATCHED_PATIENT = 0;
    private static final int PATIENT = 1001;
    /** Each source switch the list consults; only HL7TEXT_LABS is on, as in the bundled carlos.properties. */
    private static final List<String> LAB_SOURCES =
            List.of("HL7TEXT_LABS", "CML_LABS", "MDS_LABS", "PATHNET_LABS", "Epsilon_LABS", "Spire_LABS");
    private static final String NEWER_OBR_DATE = "2026-03-04 09:15:00";
    private static final String OLDER_OBR_DATE = "2026-02-11 14:30:00";
    /** U+2014 read as ISO-8859-1 and served as UTF-8, and the same bytes shown as Windows-1252. */
    private static final List<String> EM_DASH_MOJIBAKE = List.of("\u00E2\u0080\u0094", "\u00E2\u20AC\u201D");

    @TempDir
    static Path directory;
    private static JspRenderServer server;
    private static final Deque<Runnable> RESTORES = new ArrayDeque<>();
    private static final Hl7TextInfoDao HL7_REPORTS = mock(Hl7TextInfoDao.class);
    private static final Hl7TextMessageDao HL7_MESSAGES = mock(Hl7TextMessageDao.class);

    @BeforeAll
    static void startServer() throws Exception {
        when(HL7_REPORTS.findByDemographicId(UNMATCHED_PATIENT)).thenReturn(List.<Object[]>of(
                new Object[] {report(801, NEWER_OBR_DATE, "FAKE CBC")},
                new Object[] {report(802, OLDER_OBR_DATE, "FAKE Lytes")}));
        when(HL7_REPORTS.findByDemographicId(PATIENT)).thenReturn(List.<Object[]>of(
                new Object[] {report(803, NEWER_OBR_DATE, "FAKE TSH")}));
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                // CommonLabResultData's static initializer (and the page's lab-lock lookups)
                .bean(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class))
                .bean(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class))
                .bean(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class))
                .bean(SecurityInfoManager.class, mock(SecurityInfoManager.class))
                // Hl7textResultsData's static initializer
                .bean(MeasurementsDeletedDao.class, mock(MeasurementsDeletedDao.class))
                .bean(MeasurementDao.class, mock(MeasurementDao.class))
                .bean(MeasurementsExtDao.class, mock(MeasurementsExtDao.class))
                .bean(MeasurementMapDao.class, mock(MeasurementMapDao.class))
                .bean(ConsultDocsDao.class, mock(ConsultDocsDao.class))
                .bean(ConsultResponseDocDao.class, mock(ConsultResponseDocDao.class))
                .bean(Hl7TextInfoDao.class, HL7_REPORTS)
                .bean(Hl7TextMessageDao.class, HL7_MESSAGES)
                .bean(EFormDocsDao.class, mock(EFormDocsDao.class))
                // Looked up per instance on the page's path: MDSResultsData's and LabResultData's
                // fields, the merged-patient list, the heading's provider name and the CDS link.
                .bean(LabPatientPhysicianInfoDao.class, mock(LabPatientPhysicianInfoDao.class))
                .bean(OscarLogDao.class, mock(OscarLogDao.class))
                .bean(DemographicManager.class, mock(DemographicManager.class))
                .bean(ProviderDao.class, mock(ProviderDao.class))
                .bean(CdsSourceFieldPresenceResolver.class, mock(CdsSourceFieldPresenceResolver.class))
                .start();
        replaceStaticField(Hl7textResultsData.class, "hl7TxtInfoDao", HL7_REPORTS);
        replaceStaticField(Hl7textResultsData.class, "hl7TxtMsgDao", HL7_MESSAGES);
        listHl7LabsOnly();
    }

    @AfterAll
    static void stopServer() throws Exception {
        try {
            while (!RESTORES.isEmpty()) {
                RESTORES.pop().run();
            }
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA"})
    void shouldLabelEachRowCheckboxWithAnEmDash_inThePageLanguage(String language) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));
        String select = bundle.getString("dms.incomingDocs.select");
        String dateOfTest = bundle.getString("oscarMDS.index.msgDateTest");

        JspRenderServer.RenderedPage page = server.request(PAGE)
                .header("Accept-Language", language)
                .render(DemographicLabJspIntegrationTest::signIn)
                .assertOk();

        // The served template keeps the entity; the browser shows it as U+2014.
        assertThat(page.body()).contains(select + " &#8212; " + dateOfTest + ": ").doesNotContain("\uFFFD");
        for (String mojibake : EM_DASH_MOJIBAKE) {
            assertThat(page.body()).doesNotContain(mojibake);
        }
        Document html = page.document();
        // Newest first, as the page sorts its rows.
        List<String> segments = List.of("801", "802");
        List<String> dates = List.of(NEWER_OBR_DATE, OLDER_OBR_DATE);
        assertThat(html.select("#labResultsTbl input[name=flaggedLabs]")).hasSize(2);
        for (int row = 0; row < segments.size(); row++) {
            Element label = html.selectFirst("label.visually-hidden[for=flaggedLab" + row + "]");
            String shownDate = DateUtils.getDate(UtilDateUtilities.getDateFromString(dates.get(row), "yyyy-MM-dd HH:mm:ss"));
            assertThat(label.wholeText()).isEqualTo(select + " \u2014 " + dateOfTest + ": " + shownDate);
            Element checkbox = html.getElementById("flaggedLab" + row);
            assertThat(checkbox.val()).isEqualTo(segments.get(row));
            assertThat(checkbox.attr("data-lab-type")).isEqualTo("HL7");
        }
    }

    @Test
    void shouldShowNoRowCheckboxes_whenTheListIsForOnePatient() throws Exception {
        Document html = server.request(PAGE)
                .parameter("demographicNo", String.valueOf(PATIENT))
                .render(DemographicLabJspIntegrationTest::signIn)
                .assertOk()
                .document();

        assertThat(html.select("#labResultsTbl tbody tr")).hasSize(1);
        assertThat(html.select("#labResultsTbl tbody tr a").text()).isEqualTo("FAKE TSH");
        assertThat(html.select("label[for^=flaggedLab]")).isEmpty();
        assertThat(html.select("input[name=flaggedLabs]")).isEmpty();
    }

    /** A signed-in provider with the lab privilege the page's _lab check reads. */
    private static void signIn(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", PROVIDER);
        LoggedInInfo provider = new LoggedInInfo();
        provider.setLoggedInProvider(new Provider(PROVIDER));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), provider);
    }

    /** A final HL7 report row as Hl7TextInfoDao returns it; FAKE values only. */
    private static Hl7TextInfo report(int labNumber, String obrDate, String label) {
        Hl7TextInfo report = new Hl7TextInfo();
        report.setLabNumber(labNumber);
        report.setObrDate(obrDate);
        report.setLabel(label);
        report.setDiscipline(label);
        report.setLastName("FAKE");
        report.setFirstName("PATIENT");
        report.setSex("U");
        report.setHealthNumber("");
        report.setAccessionNumber("FAKE-" + labNumber);
        report.setRequestingProvider("FAKE Requester");
        report.setResultStatus("");
        report.setReportStatus("F");
        report.setPriority("R");
        report.setFinalResultCount(1);
        return report;
    }

    /** Points a static field at this test's value until {@link #stopServer()} puts the old one back. */
    private static void replaceStaticField(Class<?> owner, String name, Object value) {
        Object previous = ReflectionTestUtils.getField(owner, name);
        RESTORES.push(() -> ReflectionTestUtils.setField(owner, name, previous));
        ReflectionTestUtils.setField(owner, name, value);
    }

    /** The bundled HL7-only lab sources, whatever an earlier test left in the shared CarlosProperties. */
    private static void listHl7LabsOnly() {
        CarlosProperties properties = CarlosProperties.getInstance();
        for (String key : LAB_SOURCES) {
            Object previous = properties.get(key);
            RESTORES.push(() -> {
                if (previous == null) {
                    properties.remove(key);
                } else {
                    properties.put(key, previous);
                }
            });
            properties.remove(key);
        }
        properties.setProperty("HL7TEXT_LABS", "yes");
    }
}
