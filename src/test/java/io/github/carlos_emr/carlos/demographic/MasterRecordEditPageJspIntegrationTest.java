/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao;
import io.github.carlos_emr.carlos.commn.dao.CountryCodeDao;
import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.web.Contact2Action;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSourceFieldPresenceResolver;
import io.github.carlos_emr.carlos.demographic.data.ProvinceNames;
import io.github.carlos_emr.carlos.demographic.pageUtil.DemographicEditVersion;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.LookupListManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Renders the whole Master Record page ({@code edit.jsp}, the {@code success} result of
 * {@code demographic/DemographicEdit}, with its real fragments {@code edit-view.jsp},
 * {@code edit-form-personal.jsp} and {@code edit-form-clinical.jsp}) for a FAKE patient whose
 * stored values each hold a double quote, markup and a literal {@code &amp;}, and pins what three
 * Master Record changes print, for which #6336 found only source-text tests:
 *
 * <ul>
 *   <li>8872cdd32c67 (#6271): {@code edit.jsp} loads {@code js/demographic/custom-selects.js},
 *       deferred, for the Update Record form; {@code edit-form-clinical.jsp} reopens a configured
 *       custom select on the saved option through {@code DemographicCustomFieldSelect.markSaved},
 *       inside the {@code data-custom-select-name} cell the script reads, and encodes the
 *       provider, roster and patient status, referral doctor, chart number, paper chart and custom
 *       field values;</li>
 *   <li>e604dcd6578a (#6272): {@code edit-form-personal.jsp} prints the postal codes, phones,
 *       extensions, health card number and version, SIN and cytology number, and their hidden
 *       {@code Orig} copies, HTML-attribute encoded, so an unchanged Update Record posts them
 *       back whole;</li>
 *   <li>ab151f26822a (#6237): {@code edit-view.jsp} shows the phone lines, postal codes, health
 *       card, chart number, statuses, alert, contacts, custom values and notes as text, and a
 *       roster status code the bundle does not know as the stored code.</li>
 * </ul>
 *
 * <p>The request holds what {@code DemographicEdit2Action} sets (its lines 351-426), with its DAOs
 * and managers as mocks. The configuration is the bundled {@code carlos.properties}, with the keys
 * these assertions depend on pinned on the shared {@code CarlosProperties} for this class and their
 * raw values put back afterwards, as other tests of that singleton do: the three custom fields
 * {@code demographicExt}/{@code demographicExtForm} configure (neither has a bundled value), and
 * the bundled values of the sections read here. Only three includes are stand-ins: the patient
 * search route {@code ViewZdemographicFullTitleSearch} (a Struts action) and the two
 * health-care-team fragments, which load their own data.</p>
 *
 * <p>Two classes these pages initialise keep {@code SpringUtils} beans in static fields, and both
 * are safe here. {@code demographic.pageUtil.Util} (the spoken-language and roster-reason lists)
 * keeps a {@code PartialDateDao} that only its five-argument {@code putPartialDate} reads, and
 * nothing calls that overload, so a mock is registered only so that a first initialisation in
 * this fork succeeds. {@code Contact2Action.fillContactNames}, which lists the contacts under
 * {@code NEW_CONTACTS_UI}, reads its static DAOs: all nine are registered for the same reason,
 * and the one this page's contact reaches, {@code contactDao}, is pointed at this test's mock and
 * put back afterwards, as {@code Contact2ActionUnitTest} does with the fields it uses. A third,
 * {@code OtherIdManager}, is called only for {@code meditech_id}, which is pinned off so the
 * pages never initialise it.</p>
 */
@Tag("integration")
@Tag("demographic")
class MasterRecordEditPageJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/demographic/edit.jsp";
    private static final String PATIENT = "1001";
    private static final String PROVIDER = "999998";
    private static final String NURSE = "999997";
    private static final String MIDWIFE = "999996";
    private static final int CONTACT_ID = 7001;

    private static final String POSTAL = hostile("postal");
    private static final String RESIDENTIAL_POSTAL = hostile("residential-postal");
    private static final String PHONE = hostile("phone");
    private static final String PHONE2 = hostile("phone2");
    private static final String HOME_EXT = hostile("home-ext");
    private static final String WORK_EXT = hostile("work-ext");
    private static final String CELL = hostile("cell");
    private static final String HIN = hostile("hin");
    private static final String VER = hostile("ver");
    private static final String SIN = hostile("sin");
    private static final String CYTOLOGY = hostile("cytology");
    private static final String CHART_NO = hostile("chart");
    private static final String ROSTER_STATUS = hostile("roster-status");
    private static final String PATIENT_STATUS = hostile("patient-status");
    private static final String REFERRAL_NAME = hostile("referral-doctor");
    private static final String REFERRAL_NO = hostile("referral-no");
    private static final String ALERT = hostile("alert");
    private static final String NOTES = hostile("notes");
    private static final String ARCHIVED_DATE = hostile("archived-date");
    private static final String ARCHIVED_PROGRAM = hostile("archived-program");
    private static final String LANGUAGE_NOTE = hostile("language-note");
    private static final String CONTACT_ROLE = hostile("contact-role");
    private static final String CONTACT_LAST = hostile("contact");
    private static final String MRP_LAST = hostile("mrp");
    private static final String NURSE_LAST = hostile("nurse");
    private static final String MIDWIFE_LAST = hostile("midwife");
    /** The MRP's display name in the session's providerBean, which the read view prints. */
    private static final String MRP_DISPLAY = hostile("mrp-display");

    /**
     * Configuration this class pins: three custom fields (a select whose template default comes
     * after the option this patient saved, a multiple select this patient cleared, and a text box),
     * the bundled values of the sections these assertions read, and {@code meditech_id} off.
     */
    private static final Map<String, String> PINNED_PROPERTIES = pinnedProperties();

    @TempDir
    static Path directory;
    private static JspRenderServer server;
    /** The raw values the pinned keys held before this class, null for an absent key. */
    private static final Map<String, Object> propertiesBefore = new HashMap<>();
    private static boolean contactDaoSwapped;
    private static Object contactDaoBefore;

    @BeforeAll
    static void startServer() throws Exception {
        CarlosProperties properties = CarlosProperties.getInstance();
        PINNED_PROPERTIES.forEach((key, value) -> {
            propertiesBefore.put(key, properties.get(key));
            properties.setProperty(key, value);
        });

        ContactDao contacts = mock(ContactDao.class);
        Contact external = new Contact();
        external.setId(CONTACT_ID);
        external.setLastName(CONTACT_LAST);
        external.setFirstName("Sam");
        when(contacts.find(CONTACT_ID)).thenReturn(external);
        DemographicContactDao demographicContacts = mock(DemographicContactDao.class);
        when(demographicContacts.findByDemographicNo(Integer.parseInt(PATIENT))).thenReturn(List.of(contact()));
        DemographicMergedDao merged = mock(DemographicMergedDao.class);
        when(merged.findCurrentByDemographicNo(anyInt())).thenReturn(List.of());
        when(merged.findCurrentByMergedTo(anyInt())).thenReturn(List.of());
        AppointmentManager appointments = mock(AppointmentManager.class);
        when(appointments.getNextAppointmentDate(any(Integer.class))).thenReturn("2026-12-01");
        ScheduleTemplateDao templates = mock(ScheduleTemplateDao.class);
        when(templates.findTimeCodeByProviderNo2(any(), any())).thenReturn(List.of());

        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .file("/WEB-INF/jsp/demographic/displayHealthCareTeam.jsp", "<div id=\"stand-in-health-care-team\"></div>\n")
                .file("/WEB-INF/jsp/demographic/manageHealthCareTeam.jsp", "<div id=\"stand-in-manage-health-care-team\"></div>\n")
                .servlet("/demographic/ViewZdemographicFullTitleSearch", new HttpServlet() {
                    @Override
                    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
                        response.getWriter().write("<div id=\"stand-in-patient-search\"></div>");
                    }
                })
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(CdsSourceFieldPresenceResolver.class, mock(CdsSourceFieldPresenceResolver.class))
                .bean(AppointmentManager.class, appointments)
                .bean(DemographicMergedDao.class, merged)
                .bean(RecycleBinDao.class, mock(RecycleBinDao.class))
                .bean(ScheduleTemplateDao.class, templates)
                .bean(OscarAppointmentDao.class, mock(OscarAppointmentDao.class))
                .bean(PartialDateDao.class, mock(PartialDateDao.class))
                // Contact2Action's nine static fields.
                .bean(ContactDao.class, contacts)
                .bean(ProfessionalContactDao.class, mock(ProfessionalContactDao.class))
                .bean(DemographicContactDao.class, demographicContacts)
                .bean(DemographicDao.class, mock(DemographicDao.class))
                .bean(DemographicManager.class, mock(DemographicManager.class))
                .bean(ProviderDao.class, mock(ProviderDao.class))
                .bean(ProfessionalSpecialistDao.class, mock(ProfessionalSpecialistDao.class))
                .bean(ContactSpecialtyDao.class, mock(ContactSpecialtyDao.class))
                .bean(CtlRelationshipsDao.class, mock(CtlRelationshipsDao.class))
                .start();

        // Read while this class's beans are installed, so a first initialisation succeeds.
        contactDaoBefore = ReflectionTestUtils.getField(Contact2Action.class, "contactDao");
        ReflectionTestUtils.setField(Contact2Action.class, "contactDao", contacts);
        contactDaoSwapped = true;
    }

    @AfterAll
    static void stopServer() throws Exception {
        try {
            if (contactDaoSwapped) {
                ReflectionTestUtils.setField(Contact2Action.class, "contactDao", contactDaoBefore);
            }
            if (server != null) {
                server.close();
            }
        } finally {
            CarlosProperties properties = CarlosProperties.getInstance();
            propertiesBefore.forEach((key, value) -> {
                if (value == null) {
                    properties.remove(key);
                } else {
                    properties.put(key, value);
                }
            });
            propertiesBefore.clear();
        }
    }

    @Test
    void shouldLoadTheCustomSelectsScriptDeferred_forTheUpdateRecordForm() throws Exception {
        Document html = openMasterRecord();

        Elements scripts = html.select("script[src$=/js/demographic/custom-selects.js]");
        assertThat(scripts).hasSize(1);
        Element script = scripts.first();
        assertThat(script.attr("src")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/js/demographic/custom-selects.js");
        assertThat(script.hasAttr("defer")).isTrue();
        assertThat(script.closest("head")).isNotNull();
        assertThat(Files.isRegularFile(Path.of("src/main/webapp/js/demographic/custom-selects.js"))).isTrue();
        // What the script binds to: form#updatedelete, and each enabled multiple select whose
        // data-custom-select-name cell names it.
        Element form = html.getElementById("updatedelete");
        assertThat(form.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/demographic/DemographicUpdate");
        Element sites = form.selectFirst("select[name=FAKE_Research_Sites]");
        assertThat(sites.hasAttr("multiple")).isTrue();
        assertThat(sites.hasAttr("disabled")).isFalse();
        assertThat(customSelectCellName(sites)).isEqualTo("FAKE_Research_Sites");
    }

    @Test
    void shouldKeepEveryStoredPersonalValueWhole_inThePersonalEditForm() throws Exception {
        Element form = openMasterRecord().getElementById("updatedelete");

        assertInputValue(form, "postal", POSTAL);
        assertInputValue(form, "residentialPostal", RESIDENTIAL_POSTAL);
        assertInputValue(form, "phone", PHONE);
        assertInputValue(form, "phone2", PHONE2);
        assertInputValue(form, "hin", HIN);
        assertInputValue(form, "ver", VER);
        assertInputValue(form, "sin", SIN);
        for (Map.Entry<String, String> extension : Map.of("hPhoneExt", HOME_EXT, "wPhoneExt", WORK_EXT,
                "demo_cell", CELL, "cytolNum", CYTOLOGY).entrySet()) {
            assertInputValue(form, extension.getKey(), extension.getValue());
            assertInputValue(form, extension.getKey() + "Orig", extension.getValue());
        }
    }

    @Test
    void shouldKeepStoredClinicalValuesWhole_inTheClinicalEditForm() throws Exception {
        Element form = openMasterRecord().getElementById("updatedelete");

        assertInputValue(form, "chart_no", CHART_NO);
        assertInputValue(form, "r_doctor", REFERRAL_NAME);
        assertInputValue(form, "r_doctor_ohip", REFERRAL_NO);
        assertInputValue(form, "paper_chart_archived_date", ARCHIVED_DATE);
        assertInputValue(form, "paper_chart_archived_program", ARCHIVED_PROGRAM);
        assertInputValue(form, "initial_rosterstatus", ROSTER_STATUS);
        assertInputValue(form, "initial_patientstatus", PATIENT_STATUS);
        assertSelected(form, "roster_status", ROSTER_STATUS, ROSTER_STATUS);
        assertSelected(form, "patient_status", PATIENT_STATUS, PATIENT_STATUS);
        String mrp = MRP_LAST + ",FAKE";
        assertSelected(form, "provider_no", PROVIDER, mrp);
        assertSelected(form, "resident", PROVIDER, mrp);
        assertSelected(form, "nurse", NURSE, NURSE_LAST + ",FAKE");
        assertSelected(form, "midwife", MIDWIFE, MIDWIFE_LAST + ",FAKE");
        assertThat(form.selectFirst("textarea[name=alert]").val()).isEqualTo(ALERT);
        assertThat(form.selectFirst("textarea[name=notes]").val()).isEqualTo(NOTES);
        assertInputValue(form, "FAKE_Language_Note", LANGUAGE_NOTE);
        assertInputValue(form, "FAKE_Language_NoteOrig", LANGUAGE_NOTE);
    }

    @Test
    void shouldReopenAConfiguredCustomSelect_onTheSavedOption() throws Exception {
        Element form = openMasterRecord().getElementById("updatedelete");

        // The saved "Mail & Email" is selected, and the template's own default after it ("Text") is not.
        Element preference = form.selectFirst("select[name=FAKE_Contact_Preference]");
        assertThat(preference.select("option[selected]").eachAttr("value")).containsExactly("Mail & Email");
        assertThat(customSelectCellName(preference)).isEqualTo("FAKE_Contact_Preference");
        assertInputValue(form, "FAKE_Contact_PreferenceOrig", "Mail & Email");
        // A multiple select this patient cleared reopens with nothing selected, not its template default.
        Element sites = form.selectFirst("select[name=FAKE_Research_Sites]");
        assertThat(sites.select("option")).hasSize(2);
        assertThat(sites.select("option[selected]")).isEmpty();
        assertInputValue(form, "FAKE_Research_SitesOrig", "");
    }

    @Test
    void shouldShowStoredValuesAsLiteralText_inTheReadView() throws Exception {
        Document html = openMasterRecord();
        Element view = html.getElementById("viewDemographics2");

        assertThat(infoTexts(view.getElementById("contactInformation")))
                .contains(PHONE + " " + HOME_EXT, PHONE2 + " " + WORK_EXT, CELL, POSTAL, RESIDENTIAL_POSTAL);
        assertThat(view.getElementById("healthInsurance").text()).contains(HIN, VER);
        // A roster code the bundle does not know shows as the stored code.
        assertThat(infoTexts(view.getElementById("clinicStatus")))
                .contains(ROSTER_STATUS, PATIENT_STATUS, CHART_NO, CYTOLOGY);
        assertThat(view.getElementById("alert").text()).contains(ALERT);
        assertThat(view.getElementById("notes").text()).contains(NOTES);
        assertThat(view.text()).contains(MRP_DISPLAY);
        Element contact = view.getElementById("otherContacts2").selectFirst("li");
        assertThat(contact.selectFirst("span.label").text()).isEqualTo(CONTACT_ROLE + ":");
        assertThat(contact.selectFirst("span.info").text()).startsWith(CONTACT_LAST + ",Sam");
        assertThat(view.getElementById("special").select("li strong").stream().map(Element::text).toList())
                .containsExactly("Mail & Email", "", LANGUAGE_NOTE);
        assertThat(view.select(".fake-injected")).isEmpty();
        assertThat(view.text()).doesNotContainPattern("\\bnull\\b");
        assertThat(view.text()).doesNotContain("???");
    }

    /** A FAKE value with a double quote, markup that would become an element, and a literal entity. */
    private static String hostile(String field) {
        return "FAKE-" + field + " \"q\" <i class=fake-injected>&amp;</i>";
    }

    private static Map<String, String> pinnedProperties() {
        Map<String, String> pinned = new LinkedHashMap<>();
        pinned.put("demographicExt", "FAKE Contact Preference|FAKE Research Sites|FAKE Language Note");
        pinned.put("demographicExtForm", "<select name=\"FAKE_Contact_Preference\"><option value=\"Phone\">Phone</option>"
                + "<option value=\"Mail &amp; Email\">Mail &amp; Email</option>"
                + "<option value=\"Text\" selected>Text</option></select>"
                + "|<select multiple name=\"FAKE_Research_Sites\"><option value=\"A\">Site A</option>"
                + "<option value=\"B\" selected>Site B</option></select>"
                + "|<input type=\"text\" name=\"FAKE_Language_Note\" value=\"\"/>");
        pinned.put("NEW_CONTACTS_UI", "true");
        pinned.put("DEMOGRAPHIC_PATIENT_CLINIC_STATUS", "true");
        pinned.put("DEMOGRAPHIC_PATIENT_ROSTERING", "true");
        pinned.put("workflow_enhance", "true");
        pinned.put("isMRefDocSelectList", "false");
        pinned.put("meditech_id", "false");
        return pinned;
    }

    private static Document openMasterRecord() throws Exception {
        return server.request(PAGE)
                .parameter("demographic_no", PATIENT)
                .render(MasterRecordEditPageJspIntegrationTest::prepareMasterRecord)
                .assertOk()
                .document();
    }

    /** The session a doctor's login leaves, and the request DemographicEdit2Action builds for this patient. */
    private static void prepareMasterRecord(HttpServletRequest request) {
        HttpSession session = request.getSession();
        session.setAttribute("userrole", "doctor");
        session.setAttribute("user", PROVIDER);
        session.setAttribute("userfirstname", "FAKE");
        session.setAttribute("userlastname", "Doctor");
        LoggedInInfo login = new LoggedInInfo();
        login.setLoggedInProvider(provider(PROVIDER, MRP_LAST));
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        Properties providerNames = new Properties();
        providerNames.setProperty(PROVIDER, MRP_DISPLAY);
        session.setAttribute("providerBean", providerNames);

        Demographic patient = patient();
        Map<String, String> extensions = extensions();
        DemographicCust cust = new DemographicCust();
        cust.setId(Integer.valueOf(PATIENT));
        cust.setAlert(ALERT);
        cust.setNotes("<unotes>" + NOTES + "</unotes>");
        cust.setNurse(NURSE);
        cust.setMidwife(MIDWIFE);
        cust.setResident(PROVIDER);

        DemographicDao demographics = mock(DemographicDao.class);
        when(demographics.getRosterStatuses()).thenReturn(List.of(ROSTER_STATUS));
        when(demographics.search_ptstatus()).thenReturn(List.of(PATIENT_STATUS));
        ScheduleTemplateCodeDao templateCodes = mock(ScheduleTemplateCodeDao.class);
        when(templateCodes.findTemplateCodes()).thenReturn(List.of());
        WaitingListDao waitingLists = mock(WaitingListDao.class);
        when(waitingLists.search_wlstatus(any(Integer.class))).thenReturn(List.of());

        request.setAttribute("demographic_no", PATIENT);
        request.setAttribute("demographic", patient);
        request.setAttribute("demoExt", extensions);
        request.setAttribute("demographicCust", cust);
        request.setAttribute("demographicCustDao", mock(DemographicCustDao.class));
        request.setAttribute("demographicEditVersion", DemographicEditVersion.of(patient, extensions, cust));
        request.setAttribute("archives", List.of());
        request.setAttribute("extArchives", List.of());
        request.setAttribute("serviceAdmissions", List.of());
        request.setAttribute("providers", List.of(provider(PROVIDER, MRP_LAST)));
        request.setAttribute("doctors", List.of(provider(PROVIDER, MRP_LAST)));
        request.setAttribute("nurses", List.of(provider(NURSE, NURSE_LAST)));
        request.setAttribute("midwifes", List.of(provider(MIDWIFE, MIDWIFE_LAST)));
        request.setAttribute("countryList", List.of());
        request.setAttribute("hasImportExtra", Boolean.FALSE);
        request.setAttribute("usSigned", "");
        request.setAttribute("privacyConsent", "");
        request.setAttribute("informedConsent", "");
        request.setAttribute("privateConsentEnabled", Boolean.TRUE);
        request.setAttribute("pNames", ProvinceNames.getInstance());
        request.setAttribute("dateString", "2026-11-26");
        request.setAttribute("noteReason", "FAKE note reason");
        request.setAttribute("currentProgram", "");
        request.setAttribute("isMobileOptimized", Boolean.FALSE);
        request.setAttribute("prov", "ON");
        request.setAttribute("curProvider_no", PROVIDER);
        request.setAttribute("userfirstname", "FAKE");
        request.setAttribute("userlastname", "Doctor");
        request.setAttribute("oscarProps", CarlosProperties.getInstance());
        request.setAttribute("lookupListManager", mock(LookupListManager.class));
        request.setAttribute("consentTypes", List.of());
        request.setAttribute("patientConsents", List.of());
        // DemographicEdit2Action derives the referral doctor from family_doctor and the rest from demographiccust.
        request.setAttribute("rd", REFERRAL_NAME);
        request.setAttribute("rdohip", REFERRAL_NO);
        request.setAttribute("family_doc", "");
        request.setAttribute("resident", cust.getResident());
        request.setAttribute("nurse", cust.getNurse());
        request.setAttribute("alert", cust.getAlert());
        request.setAttribute("notes", DemographicCust.patientNotesText(cust.getNotes()));
        request.setAttribute("midwife", cust.getMidwife());
        request.setAttribute("birthYear", patient.getYearOfBirth());
        request.setAttribute("birthMonth", patient.getMonthOfBirth());
        request.setAttribute("birthDate", patient.getDateOfBirth());
        request.setAttribute("scheduleTemplateCodeDao", templateCodes);
        request.setAttribute("waitingListDao", waitingLists);
        request.setAttribute("waitingListNameDao", mock(WaitingListNameDao.class));
        request.setAttribute("waitingListLinkVisible", Boolean.FALSE);
        request.setAttribute("selectableWaitingLists", List.of());
        request.setAttribute("userPropertyDAO", mock(UserPropertyDAO.class));
        request.setAttribute("demographicDao", demographics);
        request.setAttribute("demographicExtDao", mock(DemographicExtDao.class));
        request.setAttribute("demographicArchiveDao", mock(DemographicArchiveDao.class));
        request.setAttribute("demographicExtArchiveDao", mock(DemographicExtArchiveDao.class));
        request.setAttribute("demographicManager", mock(DemographicManager.class));
        request.setAttribute("programManager", mock(ProgramManager.class));
        request.setAttribute("programDao", mock(ProgramDao.class));
        request.setAttribute("admissionManager", mock(AdmissionManager.class));
        request.setAttribute("ccDAO", mock(CountryCodeDao.class));
        request.setAttribute("providerDao", mock(ProviderDao.class));
        request.setAttribute("programManager2", mock(ProgramManager2.class));
    }

    private static Demographic patient() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(Integer.valueOf(PATIENT));
        patient.setLastName("FAKE-MasterRecord");
        patient.setFirstName("Pat");
        patient.setSex("F");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("02");
        patient.setDateOfBirth("03");
        patient.setProvince("ON");
        patient.setHcType("ON");
        patient.setProviderNo(PROVIDER);
        patient.setPostal(POSTAL);
        patient.setResidentialPostal(RESIDENTIAL_POSTAL);
        patient.setPhone(PHONE);
        patient.setPhone2(PHONE2);
        patient.setHin(HIN);
        patient.setVer(VER);
        patient.setSin(SIN);
        patient.setChartNo(CHART_NO);
        patient.setRosterStatus(ROSTER_STATUS);
        patient.setPatientStatus(PATIENT_STATUS);
        return patient;
    }

    /** The patient's demographicExt rows, as DemographicExtDao.getAllValuesForDemo returns them. */
    private static Map<String, String> extensions() {
        Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put("hPhoneExt", HOME_EXT);
        extensions.put("wPhoneExt", WORK_EXT);
        extensions.put("demo_cell", CELL);
        extensions.put("cytolNum", CYTOLOGY);
        extensions.put("paper_chart_archived", "YES");
        extensions.put("paper_chart_archived_date", ARCHIVED_DATE);
        extensions.put("paper_chart_archived_program", ARCHIVED_PROGRAM);
        extensions.put("FAKE_Contact_Preference", "Mail & Email");
        extensions.put("FAKE_Research_Sites", "");
        extensions.put("FAKE_Language_Note", LANGUAGE_NOTE);
        return extensions;
    }

    /** An external personal contact; Contact2Action.fillContactNames takes its name from ContactDao. */
    private static DemographicContact contact() {
        DemographicContact contact = new DemographicContact();
        contact.setDemographicNo(Integer.parseInt(PATIENT));
        contact.setContactId(String.valueOf(CONTACT_ID));
        contact.setType(DemographicContact.TYPE_CONTACT);
        contact.setCategory(DemographicContact.CATEGORY_PERSONAL);
        contact.setRole(CONTACT_ROLE);
        return contact;
    }

    private static Provider provider(String providerNo, String lastName) {
        Provider provider = new Provider(providerNo);
        provider.setLastName(lastName);
        provider.setFirstName("FAKE");
        provider.setProviderType("doctor");
        return provider;
    }

    private static List<String> infoTexts(Element section) {
        return section.select("span.info").stream().map(Element::text).toList();
    }

    /** The data-custom-select-name of the cell custom-selects.js finds from this select, or null without one. */
    private static String customSelectCellName(Element select) {
        Element cell = select.closest("[data-custom-select-name]");
        return cell == null ? null : cell.attr("data-custom-select-name");
    }

    /** Asserts the one input of that name holds the value whole and its cell holds no element from the value. */
    private static void assertInputValue(Element form, String name, String expected) {
        Elements inputs = form.select("input[name=" + name + "]");
        assertThat(inputs).as(name).hasSize(1);
        assertThat(inputs.first().val()).as(name).isEqualTo(expected);
        assertThat(inputs.first().parent().select(".fake-injected")).as(name).isEmpty();
    }

    /**
     * Asserts the one selected option's submitted value (its value attribute or, without one, its
     * text, as a browser submits it) and its text.
     */
    private static void assertSelected(Element form, String select, String value, String text) {
        Elements selected = form.select("select[name=" + select + "] option[selected]");
        assertThat(selected).as(select).hasSize(1);
        Element option = selected.first();
        assertThat(option.hasAttr("value") ? option.attr("value") : option.text()).as(select).isEqualTo(value);
        assertThat(option.text()).as(select).isEqualTo(text);
    }
}
