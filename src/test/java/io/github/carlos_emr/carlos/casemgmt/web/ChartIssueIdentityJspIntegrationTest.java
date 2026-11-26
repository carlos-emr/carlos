/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.CaseManagementViewAction.IssueDisplay;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSourceFieldPresenceResolver;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.context.WebApplicationContext;

/**
 * Renders the three chart pages d69b0160b0f5 (#6297) changed so that an issue is deleted or
 * re-diagnosed by its persisted {@code casemgmt_issue} id instead of its position in the checklist:
 *
 * <ul>
 *   <li>the classic note editor ({@code CaseManagementEntry.jsp}, the {@code view} result of
 *       {@code CaseManagementEntry?method=edit&from=casemgmt}) now prints one
 *       {@code renderedIssueIdentity} hidden input per checklist issue ({@code <id>:<issue_id>},
 *       after a leading empty one), links each checkbox to its encoded issue description with a
 *       label, and gives each row Delete ({@code caseIssueId}) and Change Issue
 *       ({@code changeDiagnosisIssueId}) submit buttons that carry the row's persisted id, where
 *       they used to write the row index into {@code deleteId};</li>
 *   <li>the modern chart's note form ({@code ChartNotes.jsp}) carries the
 *       {@code changeDiagnosisIssueId} input the Change button fills, and no longer the
 *       {@code deleteId}, {@code lineId} or {@code change_diagnosis_id} inputs;</li>
 *   <li>the Change-diagnosis issue search ({@code IssueSearch.jsp}, the {@code IssueSearch} result of
 *       {@code changeDiagnosis} and {@code issueSearch}) echoes {@code changeDiagnosisIssueId}
 *       (was {@code change_diagnosis_id}), so "add checked issue" replaces the issue that was
 *       clicked.</li>
 * </ul>
 *
 * <p>Before #6336 no test rendered these pages: the action tests stop at request attributes and
 * the Node tests read JSP text or a fake form. The request state here is what
 * {@code CaseManagementEntry2Action} leaves for each result: the editor's form bean in the session
 * under {@code ChartProgramContext.formKey} (no chart-context token, the legacy key
 * {@code caseManagementEntryForm<patient>}), and the {@code IssueSearch} attributes. Struts resolves
 * {@code ${showList}} and {@code ${newIssueCheckList}} from the action's properties; with no Struts
 * here they are request attributes with the same names. The editor's billing row looks its
 * {@code CaseManagementManager} up in the root web application context, so each editor render puts
 * a mock context in the fixture's own servlet context. None of the classes these pages load keeps a
 * {@code SpringUtils} bean in a static field.</p>
 */
@Tag("integration")
@Tag("casemgmt")
class ChartIssueIdentityJspIntegrationTest {
    private static final String CLASSIC_EDITOR = "/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp";
    private static final String CHART_NOTES = "/WEB-INF/jsp/casemgmt/ChartNotes.jsp";
    private static final String ISSUE_SEARCH = "/WEB-INF/jsp/casemgmt/IssueSearch.jsp";
    private static final String PATIENT = "1001";
    private static final String PROVIDER = "999998";
    /** {@code ChartProgramContext.formKey} for {@link #PATIENT} without a chart-context token. */
    private static final String FORM_KEY = "caseManagementEntryForm" + PATIENT;
    private static final String HOSTILE_DESCRIPTION = "FAKE <img src=x onerror=alert(1)> & co";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory)
                .jsp(CLASSIC_EDITOR, CHART_NOTES, ISSUE_SEARCH)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(DemographicManager.class, mock(DemographicManager.class))
                .bean(ProgramManager2.class, mock(ProgramManager2.class))
                .bean(CdsSourceFieldPresenceResolver.class, mock(CdsSourceFieldPresenceResolver.class))
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldRenderOneIdentityPerChecklistIssue_inTheClassicEditor() throws Exception {
        Element form = classicEditor();

        // Every checklist issue, the hidden resolved one included, in checklist order after the
        // leading empty value: the action compares this list with the session's checklist.
        assertThat(form.select("input[type=hidden][name=renderedIssueIdentity]").eachAttr("value"))
                .containsExactly("", "5101:41", "5102:42", "5103:43", "5104:44");
        assertThat(form.select("input[name=lineId]")).isEmpty();
    }

    @Test
    void shouldLabelEachIssueCheckbox_withItsEncodedDescription() throws Exception {
        JspRenderServer.RenderedPage page = renderClassicEditor();
        Element form = page.document().getElementById("caseManagementEntryForm");

        // The resolved issue (index 1) has no row; the other rows keep their server index.
        assertThat(checklistBoxes(form).eachAttr("id"))
                .containsExactly("issue-check-0", "issue-check-2", "issue-check-3");
        assertLabelled(form, 0, "FAKE Asthma");
        assertLabelled(form, 2, HOSTILE_DESCRIPTION);
        assertLabelled(form, 3, "FAKE Community issue");
        assertThat(form.select("label img")).isEmpty();
        assertThat(page.body()).contains("&lt;img src=x onerror=alert(1)&gt; &amp; co");
    }

    @Test
    void shouldSubmitTheRowsPersistedIssueId_fromTheDeleteAndChangeButtons() throws Exception {
        Element form = classicEditor();

        Element unused = row(form, 0);
        assertSubmits(unused.selectFirst("button[name=caseIssueId]"), "5101", "issueDelete");
        assertSubmits(unused.selectFirst("button[name=changeDiagnosisIssueId]"), "5101", "changeDiagnosis");
        // A local issue a note already uses can be re-diagnosed but not deleted.
        Element used = row(form, 2);
        assertThat(used.select("button[name=caseIssueId]")).isEmpty();
        assertSubmits(used.selectFirst("button[name=changeDiagnosisIssueId]"), "5103", "changeDiagnosis");
        // A community issue offers neither.
        assertThat(row(form, 3).select("button")).isEmpty();

        assertThat(form.select("button[name=caseIssueId]").eachAttr("value")).containsExactly("5101");
        assertThat(form.select("button[name=changeDiagnosisIssueId]").eachAttr("value"))
                .containsExactly("5101", "5103");
        // The buttons no longer write a list index into deleteId.
        assertThat(form.select("[onclick*=deleteId]")).isEmpty();
    }

    @Test
    void shouldCarryAnEmptyChangeDiagnosisIssueIdAndNoListIndexInputs_inTheChartNoteForm() throws Exception {
        JspRenderServer.RenderedPage page = server.request(CHART_NOTES)
                .parameter("demographicNo", PATIENT)
                .render(ChartIssueIdentityJspIntegrationTest::signInToChart)
                .assertOk();

        Document html = page.document();
        // newCaseManagementView.js.jsp's changeDiagnosis(issueId) writes the clicked issue's id here.
        Element form = html.selectFirst("form[name=caseManagementEntryForm]");
        assertThat(form.id()).isEqualTo("caseManagementEntryForm");
        assertThat(form.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/CaseManagementEntry");
        Elements changeTarget = form.select("input[name=changeDiagnosisIssueId]");
        assertThat(changeTarget).hasSize(1);
        assertThat(changeTarget.first().attr("type")).isEqualTo("hidden");
        assertThat(changeTarget.first().val()).isEmpty();
        assertThat(html.select("input[name=changeDiagnosisIssueId]")).hasSize(1);
        assertThat(form.select("input[name=change_diagnosis]")).hasSize(1);
        assertThat(form.select("input[name=newIssueId]")).hasSize(1);
        assertThat(html.select("input[name=deleteId], input[name=lineId], input[name=change_diagnosis_id]")).isEmpty();
        assertThat(page.body()).doesNotContain("value=\"null\"");
    }

    @Test
    void shouldEchoChangeDiagnosisIssueIdAndIgnoreTheRetiredName_inTheChartNoteForm() throws Exception {
        String hostile = "5101\" onfocus=\"alert(1)";
        Element echoed = server.request(CHART_NOTES)
                .parameter("demographicNo", PATIENT)
                .render(request -> {
                    signInToChart(request);
                    request.setAttribute("changeDiagnosisIssueId", hostile);
                })
                .assertOk()
                .document()
                .selectFirst("form[name=caseManagementEntryForm] input[name=changeDiagnosisIssueId]");
        assertThat(echoed.val()).isEqualTo(hostile);
        assertThat(echoed.hasAttr("onfocus")).isFalse();

        Element retired = server.request(CHART_NOTES)
                .parameter("demographicNo", PATIENT)
                .render(request -> {
                    signInToChart(request);
                    request.setAttribute("change_diagnosis_id", "5101");
                })
                .assertOk()
                .document()
                .selectFirst("form[name=caseManagementEntryForm] input[name=changeDiagnosisIssueId]");
        assertThat(retired.val()).isEmpty();
    }

    @Test
    void shouldCarryTheClickedIssueId_onTheChangeDiagnosisSearchPage() throws Exception {
        // What CaseManagementEntry2Action.changeDiagnosis() leaves before its IssueSearch result.
        Document html = server.request(ISSUE_SEARCH)
                .parameter("method", "changeDiagnosis")
                .parameter("demographicNo", PATIENT)
                .parameter("providerNo", PROVIDER)
                .parameter("from", "casemgmt")
                .parameter("changeDiagnosisIssueId", "5101")
                .render(request -> {
                    signIn(request);
                    request.setAttribute("demoName", "FAKE, Patient");
                    request.setAttribute("from", "casemgmt");
                    request.setAttribute("change_diagnosis", Boolean.TRUE);
                    request.setAttribute("changeDiagnosisIssueId", request.getParameter("changeDiagnosisIssueId"));
                    request.setAttribute("showList", "false");
                })
                .assertOk()
                .document();

        Element form = html.selectFirst("form");
        assertThat(form.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/CaseManagementEntry");
        assertThat(form.attr("method")).isEqualTo("post");
        assertThat(form.select("input[name=changeDiagnosisIssueId]").eachAttr("value")).containsExactly("5101");
        assertThat(form.select("input[name=change_diagnosis]").eachAttr("value")).containsExactly("true");
        assertThat(html.select("input[name=change_diagnosis_id]")).isEmpty();
        assertThat(form.select("input[name^=newIssueCheckList]")).isEmpty();
    }

    @Test
    void shouldSubmitTheIssueIdWithTheCheckedReplacement_fromTheSearchResults() throws Exception {
        // What issueSearch() leaves after a search on the Change page: the id travels as a parameter.
        Document html = server.request(ISSUE_SEARCH)
                .parameter("method", "issueSearch")
                .parameter("demographicNo", PATIENT)
                .parameter("providerNo", PROVIDER)
                .parameter("from", "casemgmt")
                .parameter("change_diagnosis", "true")
                .parameter("changeDiagnosisIssueId", "5101")
                .render(request -> {
                    signIn(request);
                    request.setAttribute("demoName", "FAKE, Patient");
                    request.setAttribute("from", "casemgmt");
                    request.setAttribute("change_diagnosis", request.getParameter("change_diagnosis"));
                    request.setAttribute("changeDiagnosisIssueId", request.getParameter("changeDiagnosisIssueId"));
                    request.setAttribute("showList", "true");
                    request.setAttribute("newIssueCheckList", new CheckIssueBoxBean[] {
                            replacement(61L, "FAKE-R61", "FAKE Replacement diagnosis"),
                            replacement(62L, "FAKE-R62", "FAKE Other diagnosis")});
                })
                .assertOk()
                .document();

        Element form = html.selectFirst("form");
        assertThat(form.select("input[name=changeDiagnosisIssueId]").eachAttr("value")).containsExactly("5101");
        assertThat(form.select("input[name=change_diagnosis]").eachAttr("value")).containsExactly("true");
        assertThat(form.select("input[type=checkbox]").eachAttr("name"))
                .containsExactly("newIssueCheckList[0].checked", "newIssueCheckList[1].checked");
        Element add = form.selectFirst("input[name=submit_add_issue]");
        assertThat(add.attr("onclick")).contains("this.form.method.value='issueAdd'", "this.form.submit()");
        assertThat(html.select("input[name=change_diagnosis_id]")).isEmpty();
    }

    @Test
    void shouldEncodeAHostileIssueId_onTheChangeDiagnosisSearchPage() throws Exception {
        String hostile = "5101\"><b id=injected>FAKE</b>";
        Document html = server.request(ISSUE_SEARCH)
                .parameter("demographicNo", PATIENT)
                .render(request -> {
                    signIn(request);
                    request.setAttribute("change_diagnosis", Boolean.TRUE);
                    request.setAttribute("changeDiagnosisIssueId", hostile);
                })
                .assertOk()
                .document();

        assertThat(html.selectFirst("input[name=changeDiagnosisIssueId]").val()).isEqualTo(hostile);
        assertThat(html.getElementById("injected")).isNull();
    }

    private static Element classicEditor() throws Exception {
        return renderClassicEditor().document().getElementById("caseManagementEntryForm");
    }

    /** {@code CaseManagementEntry?method=edit&from=casemgmt}, as the chart's classic editor popup opens it. */
    private static JspRenderServer.RenderedPage renderClassicEditor() throws Exception {
        return server.request(CLASSIC_EDITOR)
                .parameter("method", "edit")
                .parameter("from", "casemgmt")
                .parameter("demographicNo", PATIENT)
                .parameter("providerNo", PROVIDER)
                .render(ChartIssueIdentityJspIntegrationTest::openClassicEditor)
                .assertOk();
    }

    /** The session and request state CaseManagementEntry2Action.edit() leaves before its view result. */
    private static void openClassicEditor(HttpServletRequest request) {
        signIn(request);
        CaseManagementEntryFormBean form = new CaseManagementEntryFormBean();
        List<CheckBoxBean> checklist = new ArrayList<>();
        checklist.add(issue(5101L, 41L, "FAKE Asthma", "local", false, false));
        checklist.add(issue(5102L, 42L, "FAKE Resolved issue", "local", true, false));
        checklist.add(issue(5103L, 43L, HOSTILE_DESCRIPTION, "local", false, true));
        checklist.add(issue(5104L, 44L, "FAKE Community issue", "community", false, false));
        form.setIssueCheckList(checklist);
        request.getSession().setAttribute(FORM_KEY, form);
        request.setAttribute("change_flag", "false");
        request.setAttribute("from", "casemgmt");
        request.setAttribute("demoName", "FAKE, Patient");
        request.setAttribute("demoAge", "40");
        request.setAttribute("demoDOB", "1986-01-01");
        request.setAttribute("newNote", "true");
        request.setAttribute("issueStatusChanged", "false");
        request.setAttribute("noteId", "0");
        request.setAttribute("billing_url", JspRenderServer.CONTEXT_PATH
                + "/billing?billRegion=ON&demographic_no=" + PATIENT + "&bNewForm=1&status=t");
        // The billing row's caisirole:SecurityAccess tag reads CaseManagementManager from here.
        WebApplicationContext web = mock(WebApplicationContext.class);
        when(web.getBean(CaseManagementManager.class)).thenReturn(mock(CaseManagementManager.class));
        request.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, web);
    }

    private static CheckBoxBean issue(long caseIssueId, long issueId, String description, String location,
            boolean resolved, boolean used) {
        Issue catalog = new Issue();
        catalog.setId(issueId);
        catalog.setCode("FAKE-" + issueId);
        catalog.setDescription(description);
        catalog.setRole("doctor");
        CaseManagementIssue patientIssue = new CaseManagementIssue();
        patientIssue.setId(caseIssueId);
        patientIssue.setIssue_id(issueId);
        patientIssue.setIssue(catalog);
        IssueDisplay display = new IssueDisplay();
        display.setDescription(description);
        display.setLocation(location);
        display.setAcute("chronic");
        display.setCertain("certain");
        display.setMajor("not major");
        display.setResolved(resolved ? "resolved" : "unresolved");
        display.setRole("doctor");
        display.setWriteAccess(true);
        CheckBoxBean bean = new CheckBoxBean();
        bean.setIssue(patientIssue);
        bean.setIssueDisplay(display);
        bean.setUsed(used);
        return bean;
    }

    private static CheckIssueBoxBean replacement(long issueId, String code, String description) {
        Issue catalog = new Issue();
        catalog.setId(issueId);
        catalog.setCode(code);
        catalog.setDescription(description);
        catalog.setRole("doctor");
        CheckIssueBoxBean bean = new CheckIssueBoxBean();
        bean.setIssue(catalog);
        return bean;
    }

    private static Elements checklistBoxes(Element form) {
        return form.select("input[type=checkbox][name~=^issueCheckList\\[\\d+\\]\\.checked$]");
    }

    /** The checklist table row whose checkbox has the given server index. */
    private static Element row(Element form, int index) {
        return form.getElementById("issue-check-" + index).closest("tr");
    }

    private static void assertLabelled(Element form, int index, String description) {
        Element box = form.getElementById("issue-check-" + index);
        assertThat(box.attr("name")).isEqualTo("issueCheckList[" + index + "].checked");
        Elements labels = form.select("label[for=issue-check-" + index + "]");
        assertThat(labels).hasSize(1);
        assertThat(labels.first().text()).isEqualTo(description);
        assertThat(labels.first().children()).isEmpty();
    }

    private static void assertSubmits(Element button, String persistedIssueId, String method) {
        assertThat(button.attr("type")).isEqualTo("submit");
        assertThat(button.val()).isEqualTo(persistedIssueId);
        assertThat(button.attr("onclick")).isEqualTo("this.form.method.value='" + method + "';");
    }

    private static void signIn(HttpServletRequest request) {
        HttpSession session = request.getSession();
        session.setAttribute("userrole", "doctor");
        session.setAttribute("user", PROVIDER);
    }

    /** The chart session ChartNotes.jsp reads: the role, and the login's LoggedInInfo with no facility. */
    private static void signInToChart(HttpServletRequest request) {
        signIn(request);
        LoggedInInfo login = new LoggedInInfo();
        login.setLoggedInProvider(new Provider(PROVIDER));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
    }
}
