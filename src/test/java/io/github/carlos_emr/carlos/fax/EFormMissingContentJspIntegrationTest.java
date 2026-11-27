/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.fax;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.eform.util.EFormRenderMessage;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Renders the incomplete-eForm fax approval page, the {@code eFormMissingContent} result of
 * {@code fax/faxAction}'s prepareFax, with the request attributes Fax2Action sets for it.
 * 4deffff41424 (#6164, issue #6162) made the five yes/no render issues print as words in the page's
 * language instead of raw {@code true}/{@code false}, and the page's first line come from the bundle;
 * the counts, the approval form and the cancel form must stay as they were. Before #6336 only the
 * JSP's source text was checked: no test rendered this page.
 */
@Tag("integration")
@Tag("fax")
class EFormMissingContentJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/fax/EFormMissingContent.jsp";
    private static final String APPROVAL = "FAKE-approval\"&<token>";
    /** Both branches of the yes/no choice: three issues present, two absent. */
    private static final Map<String, Boolean> FLAGS = new LinkedHashMap<>();
    private static final Map<String, Integer> COUNTS = new LinkedHashMap<>();

    static {
        FLAGS.put("signatureMissing", true);
        FLAGS.put("providerStampMissing", false);
        FLAGS.put("timerCompatibilityFailure", true);
        FLAGS.put("stabilizationCapped", false);
        FLAGS.put("labDecisionSupportStubbed", true);
        COUNTS.put("failedContentResources", 1);
        COUNTS.put("excludedContentElements", 2);
        COUNTS.put("severeConsoleErrors", 0);
        COUNTS.put("containedInteractions", 0);
        COUNTS.put("decorativeExcludedElements", 0);
    }

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory).jsp(PAGE).start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA", "es"})
    void shouldListTheYesNoIssuesAsWords_inThePageLanguage(String language) throws Exception {
        Locale locale = Locale.forLanguageTag(language);
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale);

        Document html = server.request(PAGE)
                .header("Accept-Language", language)
                .render(EFormMissingContentJspIntegrationTest::prepareIncompleteFax)
                .assertOk()
                .document();

        assertThat(html.selectFirst("html").attr("lang")).isEqualTo(locale.getLanguage());
        assertThat(html.selectFirst("div.card-body > p").text())
                .isEqualTo(bundle.getString("fax.eformMissingContent.msgNotFaxed"));
        Elements issues = html.select("ul.small > li");
        for (Map.Entry<String, Boolean> flag : FLAGS.entrySet()) {
            String label = bundle.getString("eform.renderIssue." + flag.getKey());
            String word = bundle.getString(flag.getValue() ? "eform.renderIssue.yes" : "eform.renderIssue.no");
            assertThat(issue(issues, label)).as(flag.getKey()).isEqualTo(label + ": " + word);
        }
        for (Map.Entry<String, Integer> count : COUNTS.entrySet()) {
            String label = bundle.getString("eform.renderIssue." + count.getKey());
            assertThat(issue(issues, label)).as(count.getKey()).isEqualTo(label + ": " + count.getValue());
        }
        assertThat(issues).hasSize(FLAGS.size() + COUNTS.size());
        assertThat(html.selectFirst("ul.small").text()).doesNotContainPattern("\\b(true|false)\\b");
    }

    @Test
    void shouldKeepTheApprovalAndCancelForms_withTheRenderApproval() throws Exception {
        Document html = server.render(PAGE, EFormMissingContentJspIntegrationTest::prepareIncompleteFax)
                .assertOk()
                .document();

        Element approve = html.getElementById("approve-incomplete-eform-fax");
        assertThat(approve.attr("method")).isEqualTo("post");
        assertThat(approve.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/fax/faxAction");
        assertThat(approve.selectFirst("input[name=method]").val()).isEqualTo("prepareFax");
        assertThat(approve.selectFirst("input[name=renderApproval]").val()).isEqualTo(APPROVAL);
        assertThat(approve.selectFirst("input[name=transactionType]").val()).isEqualTo("EFORM");
        assertThat(approve.selectFirst("input[name=transactionId]").val()).isEqualTo("4242");
        assertThat(approve.selectFirst("input[name=demographicNo]").val()).isEqualTo("1001");
        assertThat(approve.select("button[type=submit]")).hasSize(1);

        Element cancel = html.getElementById("cancel-incomplete-eform-fax");
        assertThat(cancel.attr("method")).isEqualTo("post");
        assertThat(cancel.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/fax/faxAction");
        assertThat(cancel.selectFirst("input[name=method]").val()).isEqualTo("cancelStagedEFormFax");
        assertThat(cancel.selectFirst("input[name=renderApproval]").val()).isEqualTo(APPROVAL);
        assertThat(cancel.selectFirst("input[name=transactionId]").val()).isEqualTo("4242");
    }

    /** The attributes Fax2Action.prepareFax sets before its eFormMissingContent result. */
    private static void prepareIncompleteFax(HttpServletRequest request) {
        request.setAttribute("renderApproval", APPROVAL);
        request.setAttribute("missingContentMessage", EFormRenderMessage.NOT_FAXED.text(request));
        request.setAttribute("transactionType", "EFORM");
        request.setAttribute("transactionId", 4242L);
        request.setAttribute("demographicNo", "1001");
        request.setAttribute("recipient", "FAKE Clinic");
        request.setAttribute("recipientFaxNumber", "5550000000");
        request.setAttribute("letterheadFax", "5550000001");
        FLAGS.forEach(request::setAttribute);
        COUNTS.forEach(request::setAttribute);
        request.setAttribute("severeConsoleErrorDetails", List.of());
    }

    /** The text of the one issue line labelled {@code label}. */
    private static String issue(Elements issues, String label) {
        List<String> lines = issues.stream().map(Element::text).filter(text -> text.startsWith(label + ":")).toList();
        assertThat(lines).as(label).hasSize(1);
        return lines.get(0);
    }
}
