/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.hospitalReportManager.HrmVersionService;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Renders the attachment picker, documentManager/attachDocument.jsp, the {@code fetchDocuments}
 * result of {@code previewDocs} that consultation requests, eForms and ticklers load into their
 * Manage Attachments dialog, with a patient's HRM report and its earlier versions.
 *
 * <p>4f6f848e7ca3 (#6135) made the HRM rows list a report's earlier versions from the validated
 * {@code versions} model HRMUtil builds with {@link HrmVersionService} (newest first, with dated
 * labels and an explicit unknown date), instead of numbering the raw {@code duplicateLabIds}.
 * 4deffff41424 (#6164, issue #6162) moved the preview's busy notice, cancel button, frame title,
 * failure alert, approval confirm, script-error advisory and yes/no words to bundle keys in the
 * locale LocaleUtils negotiates. Before #6336 the strings were only read from the JSP source
 * ({@code EFormRenderMessageUnitTest}); this asserts them, and the version rows, in the page.</p>
 *
 * <p>The version labels are made by the production formatter, {@code HrmVersionService.format},
 * which is package-private and is called reflectively.</p>
 */
@Tag("integration")
@Tag("documentManager")
@Tag("hrm")
class AttachDocumentJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/documentManager/attachDocument.jsp";
    private static final int REPORT = 30;
    private static final String REPORT_NAME = "FAKE Discharge Summary";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA"})
    void shouldListEarlierHrmVersionsFromTheValidatedModel_newestFirstWithDatedLabels(String language) throws Exception {
        Locale locale = Locale.forLanguageTag(language);
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale);
        List<HrmVersionService.Version> versions = versions(locale);

        Document html = server.request(PAGE)
                .header("Accept-Language", language)
                .render(request -> openPicker(request, versions))
                .assertOk()
                .document();

        Element current = html.getElementById("hrmNo" + REPORT);
        assertThat(current.hasAttr("data-version")).isFalse();
        assertThat(current.hasAttr("disabled")).isFalse();
        Element toggle = html.selectFirst("button.hrm-version-toggle");
        assertThat(toggle.attr("aria-controls")).isEqualTo("hrmVersions" + REPORT);
        assertThat(toggle.attr("aria-label"))
                .isEqualTo(bundle.getString("encounter.oscarConsultationRequest.AttachDocPopup.earlierVersions"));

        List<Element> earlier = html.select("#hrmVersions" + REPORT + " > li > input.hrm_check");
        // The model's order (newest report first, an undated one last), not duplicateLabIds' "21,22,23".
        assertThat(earlier).extracting(Element::val).containsExactly("22", "21", "23");
        for (int index = 0; index < versions.size(); index++) {
            HrmVersionService.Version version = versions.get(index);
            Element checkbox = earlier.get(index);
            assertThat(checkbox.id()).isEqualTo("hrmNo" + version.id());
            assertThat(checkbox.attr("name")).isEqualTo("hrmNo");
            assertThat(checkbox.attr("data-version")).isEqualTo("true");
            assertThat(checkbox.attr("title")).isEqualTo(version.label() + " " + REPORT_NAME);
            assertThat(html.selectFirst("label[for=hrmNo" + version.id() + "]").wholeText()).isEqualTo(version.label());
        }
        assertThat(versions.get(2).label()).contains(bundle.getString("hrm.version.unknownDate"));
        String numberedLabel = bundle.getString("encounter.oscarConsultationRequest.AttachDocPopup.earlierVersionOf");
        assertThat(html.getElementById("hrmList").text()).doesNotContain(numberedLabel.substring(0, numberedLabel.indexOf('{')).trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA"})
    void shouldTakeThePreviewStrings_fromTheBundleInThePageLanguage(String language) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));

        JspRenderServer.RenderedPage page = server.request(PAGE)
                .header("Accept-Language", language)
                .render(request -> openPicker(request, versions(Locale.forLanguageTag(language))))
                .assertOk();

        Document html = page.document();
        Element busy = html.getElementById("preview-waiting-text");
        assertThat(busy).as("#preview-waiting-text").isNotNull();
        assertThat(busy.wholeText()).isEqualTo(bundle.getString("eform.preview.msgRenderBusy"));
        assertThat(html.selectFirst("#preview-waiting > button").wholeText())
                .isEqualTo(bundle.getString("document.preview.btnCancel"));
        assertThat(html.getElementById("pdfObject").attr("title"))
                .isEqualTo(bundle.getString("document.preview.frameTitle"));
        String script = page.body();
        assertThat(script)
                .contains("return value ? \"" + js(bundle, "eform.renderIssue.yes") + "\" : \""
                        + js(bundle, "eform.renderIssue.no") + "\";")
                .contains("alert(\"" + js(bundle, "document.preview.msgFailed") + "\\n\\n\" + errorMessage);")
                .contains("alert(\"" + js(bundle, "document.preview.msgFailed") + "\");")
                .contains("severeConsoleDetailText + \"\\n\\n" + js(bundle, "eform.preview.confirmApprove") + "\"))")
                .contains("advisory.textContent = \"" + js(bundle, "eform.preview.advisoryScriptErrors")
                        + "\".replace(\"{0}\", count);")
                .doesNotContain("This form reported ")
                .doesNotContain("Waiting for your preview.");
        if (!"en".equals(language)) {
            assertThat(script)
                    .doesNotContain("A preview of this document could not be generated.")
                    .doesNotContain("Approve these issues and render?");
        }
    }

    /** What DocumentPreview2Action leaves for a patient with one HRM report and three earlier versions. */
    private static void openPicker(HttpServletRequest request, List<HrmVersionService.Version> versions) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        request.setAttribute("demographicNo", "1001");
        request.setAttribute("canManageAttachments", Boolean.TRUE);
        HashMap<String, Object> report = new HashMap<>();
        report.put("id", REPORT);
        report.put("name", REPORT_NAME);
        report.put("report_date", "2026-03-04 00:00:00.0");
        report.put("time_received", "2026-03-05 08:00:00.0");
        // HRMUtil keeps the stored order here and sorts only the model.
        report.put("duplicateLabIds", "21,22,23");
        report.put("versions", versions);
        ArrayList<HashMap<String, ?>> reports = new ArrayList<>();
        reports.add(report);
        request.setAttribute("allHRMDocuments", reports);
    }

    /** The earlier versions as HRMUtil formats them: one dated in January, one in February, one undated. */
    private static List<HrmVersionService.Version> versions(Locale locale) {
        List<HRMDocument> documents = List.of(
                hrmDocument(21, new Date(1767268800000L), new Date(1767355200000L)),
                hrmDocument(22, new Date(1769947200000L), new Date(1770033600000L)),
                hrmDocument(23, null, null));
        return ReflectionTestUtils.invokeMethod(HrmVersionService.class, "format", documents, locale);
    }

    private static HRMDocument hrmDocument(int id, Date reportDate, Date received) {
        HRMDocument document = new HRMDocument();
        ReflectionTestUtils.setField(document, "id", id);
        document.setReportDate(reportDate);
        document.setTimeReceived(received);
        return document;
    }

    /** A bundle string as the page writes it into a JavaScript string literal. */
    private static String js(ResourceBundle bundle, String key) {
        return SafeEncode.forJavaScript(bundle.getString(key));
    }
}
