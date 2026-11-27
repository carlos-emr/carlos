/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.DemographicSetsDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.report.ClinicalReports.ReportEvaluator;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
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
 * Renders the Clinical Reports page, the result of both {@code RunClinicalReport} and
 * {@code report/RemoveClinicalReport}, with the session history and request attributes those
 * actions leave. f7e9151f5ba4 (#6180) changed three things the page shows: "Clear" and each
 * report's "Del" are POST forms to {@code report/RemoveClinicalReport} (Clear sends
 * {@code clear=yes}; Del names its report by the HTML-attribute-encoded {@link ReportEvaluator#getKey()}
 * instead of a list position); the page no longer empties the history itself when it is opened
 * with {@code ?clear=yes}; and a report id that is not in the report file gets a
 * {@code role="alert"} message above the report form. Before #6336 only the JSP source was read
 * ({@code ClinicalReportsLinksJspRegressionTest}) and the action tests stopped at the session and
 * request attributes; nothing rendered the page.
 *
 * <p>The page also lists the report file's numerators and denominators
 * ({@code ClinicalReportManager}, the real bundled file), the measurement types, the patient sets
 * and the providers; those DAOs are empty mocks here. None of the classes the page runs keeps a
 * {@code SpringUtils} bean in a static field.</p>
 */
@Tag("integration")
@Tag("report")
class ClinicalReportsJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/report/ClinicalReports.jsp";
    private static final String REMOVE_ACTION = JspRenderServer.CONTEXT_PATH + "/report/RemoveClinicalReport";
    private static final String RUN_ACTION = JspRenderServer.CONTEXT_PATH + "/RunClinicalReport";
    /** Keys are random UUIDs in production; these hold every character an HTML attribute must escape. */
    private static final String FIRST_KEY = "FAKE-key-1\"&<b>x</b>'";
    private static final String SECOND_KEY = "FAKE-key-2&amp;\"";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        MeasurementTypeDao measurementTypes = mock(MeasurementTypeDao.class);
        when(measurementTypes.findAllOrderByName()).thenReturn(List.of());
        when(measurementTypes.findAllOrderById()).thenReturn(List.of());
        DemographicSetsDao patientSets = mock(DemographicSetsDao.class);
        when(patientSets.findSetNames()).thenReturn(List.of());
        ProviderDataDao providers = mock(ProviderDataDao.class);
        when(providers.findAll(false)).thenReturn(List.of());
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "r"))
                .bean(MeasurementTypeDao.class, measurementTypes)
                .bean(ValidationsDao.class, mock(ValidationsDao.class))
                .bean(DemographicSetsDao.class, patientSets)
                .bean(ProviderDataDao.class, providers)
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldPostClearAndEachDel_asFormsThatNameTheReportByItsKey() throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);
        ArrayList<ReportEvaluator> history = new ArrayList<>(List.of(
                report("FAKE numerator/FAKE denominator", FIRST_KEY, 3, 7),
                report("FAKE second <b>numerator</b>/FAKE denominator", SECOND_KEY, 0, 2)));

        JspRenderServer.RenderedPage page = server.render(PAGE, request -> signIn(request, history)).assertOk();

        Document html = page.document();
        List<Element> removals = removalForms(html);
        assertThat(removals).hasSize(3);
        for (Element form : removals) {
            assertThat(form.attr("method")).isEqualTo("post");
            assertThat(form.select("input")).hasSize(1);
            assertThat(form.select("input[name=id]")).isEmpty();
            Element submit = form.selectFirst("a");
            assertThat(submit.attr("href")).isEqualTo("javascript:void(0);");
            assertThat(submit.attr("onclick")).isEqualTo("this.closest('form').submit();");
        }

        Element clear = removals.get(0);
        Element clearInput = clear.selectFirst("input");
        assertThat(clearInput.attr("type")).isEqualTo("hidden");
        assertThat(clearInput.attr("name")).isEqualTo("clear");
        assertThat(clearInput.val()).isEqualTo("yes");
        assertThat(clear.selectFirst("a").text()).isEqualTo(bundle.getString("report.ClinicalReports.msgClear"));

        for (int i = 0; i < history.size(); i++) {
            Element delete = removals.get(i + 1);
            Element keyInput = delete.selectFirst("input");
            assertThat(keyInput.attr("type")).isEqualTo("hidden");
            assertThat(keyInput.attr("name")).isEqualTo("key");
            // What the browser posts back is exactly the key the remove action compares with getKey().
            assertThat(keyInput.val()).isEqualTo(i == 0 ? FIRST_KEY : SECOND_KEY);
            assertThat(delete.selectFirst("a").text()).isEqualTo(bundle.getString("report.ClinicalReports.msgDel"));
            Element item = delete.parent();
            assertThat(item.tagName()).isEqualTo("li");
            assertThat(item.text()).startsWith(i == 0 ? "3 / 7" : "0 / 2");
        }
        // The keys are escaped in the markup itself: a raw quote would end the attribute early, a raw
        // ampersand would be read back as an entity, and a raw tag would become markup.
        assertThat(page.body())
                .contains("name=\"key\" value=\"FAKE-key-1&#34;&amp;&lt;b>x&lt;/b>&#39;\"")
                .contains("name=\"key\" value=\"FAKE-key-2&amp;amp;&#34;\"")
                .doesNotContain(FIRST_KEY, SECOND_KEY);
        assertThat(html.select("b")).isEmpty();
    }

    @Test
    void shouldReachTheRemoveRouteOnlyByPost_withNoRemovalLink() throws Exception {
        ArrayList<ReportEvaluator> history = new ArrayList<>(List.of(report("FAKE report/FAKE set", FIRST_KEY, 1, 1)));

        JspRenderServer.RenderedPage page = server.render(PAGE, request -> signIn(request, history)).assertOk();

        assertThat(page.body()).doesNotContain("RemoveClinicalReport?");
        assertThat(page.document().select("a[href*=RemoveClinicalReport]")).isEmpty();
        assertThat(removalForms(page.document())).hasSize(2);
    }

    @Test
    void shouldKeepTheReportHistory_whenThePageIsOpenedWithALegacyClearParameter() throws Exception {
        ArrayList<ReportEvaluator> history = new ArrayList<>(List.of(
                report("FAKE first/FAKE set", FIRST_KEY, 2, 4),
                report("FAKE second/FAKE set", SECOND_KEY, 1, 4)));
        AtomicReference<HttpSession> session = new AtomicReference<>();

        Document html = server.request(PAGE)
                .parameter("clear", "yes")
                .render(request -> {
                    signIn(request, history);
                    session.set(request.getSession());
                })
                .assertOk()
                .document();

        // Only a POST of the Clear form to report/RemoveClinicalReport empties the history now.
        assertThat(session.get().getAttribute("ClinicalReports")).isSameAs(history);
        List<Element> removals = removalForms(html);
        assertThat(removals).hasSize(3);
        assertThat(removals.get(1).selectFirst("input[name=key]").val()).isEqualTo(FIRST_KEY);
        assertThat(removals.get(2).selectFirst("input[name=key]").val()).isEqualTo(SECOND_KEY);
    }

    @Test
    void shouldOfferNoClearOrDel_whenNoReportHasBeenRun() throws Exception {
        Document html = server.render(PAGE, request -> signIn(request, null)).assertOk().document();

        assertThat(removalForms(html)).isEmpty();
        assertThat(runForm(html)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA", "es"})
    void shouldWarnAboveTheReportForm_whenTheActionFoundNoSuchReport(String language) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));

        JspRenderServer.RenderedPage page = server.request(PAGE)
                .header("Accept-Language", language)
                .render(request -> {
                    signIn(request, null);
                    request.setAttribute("clinicalReportUnknown", Boolean.TRUE);
                })
                .assertOk();

        assertThat(page.body()).contains("<p class=\"clinical-report-unknown\" role=\"alert\">");
        Elements alerts = page.document().select("p.clinical-report-unknown");
        assertThat(alerts).hasSize(1);
        Element alert = alerts.first();
        assertThat(alert.attr("role")).isEqualTo("alert");
        assertThat(alert.wholeText()).isEqualTo(bundle.getString("report.ClinicalReports.unknownReport"));
        // Directly above the fieldset that holds the report form, so it is read before the form.
        Element next = alert.nextElementSibling();
        assertThat(next.tagName()).isEqualTo("fieldset");
        assertThat(next.selectFirst("form")).isSameAs(runForm(page.document()));
    }

    @Test
    void shouldShowNoUnknownReportWarning_unlessTheActionFlaggedOne() throws Exception {
        Document plain = server.render(PAGE, request -> signIn(request, null)).assertOk().document();
        Document cleared = server.render(PAGE, request -> {
            signIn(request, null);
            request.setAttribute("clinicalReportUnknown", Boolean.FALSE);
        }).assertOk().document();

        assertThat(plain.select("p.clinical-report-unknown")).isEmpty();
        assertThat(plain.select("[role=alert]")).isEmpty();
        assertThat(cleared.select("p.clinical-report-unknown")).isEmpty();
        assertThat(runForm(plain)).isNotNull();
    }

    /** A run report as the history holds it; the page reads its name, counts and key. */
    private static ReportEvaluator report(String name, String key, int numeratorCount, int denominatorCount) {
        ReportEvaluator report = mock(ReportEvaluator.class);
        when(report.getName()).thenReturn(name);
        when(report.getKey()).thenReturn(key);
        when(report.getNumeratorCount()).thenReturn(numeratorCount);
        when(report.getDenominatorCount()).thenReturn(denominatorCount);
        return report;
    }

    /** The signed-in session the page's {@code _report} check reads, with the report history, if any. */
    private static void signIn(HttpServletRequest request, ArrayList<ReportEvaluator> history) {
        HttpSession session = request.getSession();
        session.setAttribute("userrole", "doctor");
        session.setAttribute("user", "999998");
        if (history != null) {
            session.setAttribute("ClinicalReports", history);
        }
    }

    private static List<Element> removalForms(Document html) {
        return html.select("form").stream().filter(form -> REMOVE_ACTION.equals(form.attr("action"))).toList();
    }

    private static Element runForm(Document html) {
        return html.select("form").stream().filter(form -> RUN_ACTION.equals(form.attr("action")))
                .findFirst().orElse(null);
    }
}
