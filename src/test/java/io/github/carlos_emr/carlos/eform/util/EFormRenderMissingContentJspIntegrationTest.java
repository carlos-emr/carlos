/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.eform.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Renders the eForm missing-content approval page, the {@code missingContent} result of
 * {@code eform/addEForm}'s Add to Documents, with the request {@link EFormSavedRenderResponse}
 * really leaves when the completeness gate refuses a render. 4deffff41424 (#6164, issue #6162) made
 * each yes/no render issue print as a word in the page's language rather than raw
 * {@code true}/{@code false}, and the first line come from the bundle. Before #6336 the packaged
 * check never read those lines and the unit tests only read the JSP source; this pins both values
 * of every flag in English, French and Spanish, and that the approval form still carries the
 * issued token.
 */
@Tag("integration")
@Tag("eform")
class EFormRenderMissingContentJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/eform/EFormRenderMissingContent.jsp";
    private static final int FDID = 42;
    private static final String PATIENT = "1001";
    private static final String TEMPLATE = "7";
    /** Signature, stabilization and provider stamp raised; timer and lab decision support clear. */
    private static final EFormRenderCompletenessReport REPORT =
            new EFormRenderCompletenessReport(1, 2, 0, 0, 0, true, false, true, false, true);
    /** Every flag the other way round, so each one is rendered both ways. */
    private static final EFormRenderCompletenessReport INVERSE_REPORT =
            new EFormRenderCompletenessReport(1, 2, 0, 0, 0, false, true, false, true, false);

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    private final EFormRenderApprovalService approvals = new EFormRenderApprovalService();
    private final LoggedInInfo clinician = new LoggedInInfo();

    EFormRenderMissingContentJspIntegrationTest() {
        clinician.setLoggedInProvider(new Provider("999998"));
    }

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

    static Stream<Arguments> languagesAndReports() {
        return Stream.of(
                Arguments.of("en", REPORT), Arguments.of("en", INVERSE_REPORT),
                Arguments.of("fr-CA", REPORT), Arguments.of("fr-CA", INVERSE_REPORT),
                Arguments.of("es", REPORT));
    }

    @ParameterizedTest
    @MethodSource("languagesAndReports")
    void shouldListEveryIssueFlagAsYesOrNo_inThePageLanguage(String language, EFormRenderCompletenessReport report)
            throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));

        Document html = server.request(PAGE)
                .header("Accept-Language", language)
                .render(request -> refuseAddToDocuments(request, report))
                .assertOk()
                .document();

        assertThat(html.selectFirst("html").attr("lang")).isEqualTo(Locale.forLanguageTag(language).getLanguage());
        assertThat(html.selectFirst("div.card-body > p").text())
                .isEqualTo(bundle.getString("eform.renderMissingContent.msgNotAddedToDocuments"));
        Elements issues = html.select("ul.small > li");
        for (Map.Entry<String, Boolean> flag : flags(report).entrySet()) {
            String label = bundle.getString("eform.renderIssue." + flag.getKey());
            String word = bundle.getString(flag.getValue() ? "eform.renderIssue.yes" : "eform.renderIssue.no");
            assertThat(issue(issues, label)).as(flag.getKey()).isEqualTo(label + ": " + word);
        }
        for (Map.Entry<String, Integer> count : counts(report).entrySet()) {
            String label = bundle.getString("eform.renderIssue." + count.getKey());
            assertThat(issue(issues, label)).as(count.getKey()).isEqualTo(label + ": " + count.getValue());
        }
        assertThat(issues).hasSize(10);
        assertThat(html.selectFirst("ul.small").text()).doesNotContainPattern("\\b(true|false)\\b");
    }

    @Test
    void shouldPostTheIssuedApproval_toTheArchiveRetry() throws Exception {
        AtomicReference<String> issued = new AtomicReference<>();
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);

        Document html = server.render(PAGE, request -> issued.set(refuseAddToDocuments(request, REPORT)))
                .assertOk()
                .document();

        Element form = html.selectFirst("div.card-body form");
        assertThat(form.attr("method")).isEqualTo("post");
        assertThat(form.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH
                + "/eform/saveEFormAsEDoc?efmfid=" + TEMPLATE + "&efmdemographic_no=" + PATIENT);
        assertThat(form.selectFirst("input[name=renderApproval]").val()).isNotBlank().isEqualTo(issued.get());
        assertThat(form.selectFirst("input[name=fdid]").val()).isEqualTo(String.valueOf(FDID));
        assertThat(form.selectFirst("input[name=demographicNo]").val()).isEqualTo(PATIENT);
        assertThat(form.select("input[name=autoClose]")).isEmpty();
        assertThat(form.selectFirst("button[type=submit]").text())
                .isEqualTo(bundle.getString("eform.renderMissingContent.btnApproveAndAddToDocuments"));
        Element back = html.selectFirst("div.card-body a.btn-secondary");
        assertThat(back.attr("href")).isEqualTo(JspRenderServer.CONTEXT_PATH
                + "/eform/efmpatientformlist?demographic_no=" + PATIENT + "&parentAjaxId=eforms");
    }

    /**
     * What AddEForm2Action does when Add to Documents meets an incomplete render: the response
     * helper issues the one-time approval and sets the page's request attributes.
     *
     * @return String the issued approval token
     */
    private String refuseAddToDocuments(HttpServletRequest request, EFormRenderCompletenessReport report) {
        // The approval is bound to the clinician's session, which a signed-in request already has.
        request.getSession();
        EFormSavedRenderResponse.missing(request, approvals, clinician,
                new EformContentUnavailableException("FAKE incomplete render", FDID, report, List.of()),
                FDID, PATIENT, TEMPLATE, EFormRenderApprovalService.Operation.EDOC, null, false);
        return (String) request.getAttribute("renderApproval");
    }

    private static Map<String, Boolean> flags(EFormRenderCompletenessReport report) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("signatureMissing", report.signatureMissing());
        flags.put("providerStampMissing", report.providerStampMissing());
        flags.put("timerCompatibilityFailure", report.timerCompatibilityFailure());
        flags.put("stabilizationCapped", report.stabilizationCapped());
        flags.put("labDecisionSupportStubbed", report.labDecisionSupportStubbed());
        return flags;
    }

    private static Map<String, Integer> counts(EFormRenderCompletenessReport report) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("failedContentResources", report.failedContentResources());
        counts.put("excludedContentElements", report.excludedContentElements());
        counts.put("severeConsoleErrors", report.severeConsoleErrors());
        counts.put("containedInteractions", report.containedInteractions());
        counts.put("decorativeExcludedElements", report.decorativeExcludedElements());
        return counts;
    }

    /** The text of the one issue line labelled {@code label}. */
    private static String issue(Elements issues, String label) {
        List<String> lines = issues.stream().map(Element::text).filter(text -> text.startsWith(label + ":")).toList();
        assertThat(lines).as(label).hasSize(1);
        return lines.get(0);
    }
}
