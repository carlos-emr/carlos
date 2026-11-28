/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnHistorySpecialistViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnHistoryViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnStatusViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnThirdPartyInvoiceViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaErrorViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaViewModel;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Renders the six Ontario billing pages whose partial-result warning 72fedd972c50 (#6305) changed,
 * with the request each action leaves when its loader failed and when it did not.
 *
 * <p>None of these pages declares a page encoding, so Jasper reads them as ISO-8859-1. Each
 * banner's dash used to be the raw UTF-8 bytes of an em dash, which that reading turned into
 * U+00E2 U+0080 U+0094 in the served text; the fix writes {@code &#8212;}. The raw response
 * therefore holds the ASCII entity, and the em dash (U+2014) exists once the browser, here Jsoup,
 * parses the page, so that is where it is asserted: in the error state the banner shows the
 * sentence with a real em dash, not the misread sequence, a replacement character or a visible
 * {@code &#8212;}; in the normal state there is no banner and the page still renders. Before #6336
 * only {@code JspPageEncodingRegressionTest} read the JSP sources; nothing rendered a banner.</p>
 */
@Tag("integration")
@Tag("billing")
class BillingOnPartialResultBannersJspIntegrationTest {
    private static final String PAGES = "/WEB-INF/jsp/billing/CA/ON/";
    /** What the ISO-8859-1 reading made of the em dash's UTF-8 bytes (E2 80 94). */
    private static final String MISREAD_EM_DASH = "\u00E2\u0080\u0094";
    private static final String REPLACEMENT_CHARACTER = "\uFFFD";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory)
                .jsp(PAGES + "billingON3rdInv.jsp", PAGES + "billingONHistory.jsp", PAGES + "billingONHistorySpec.jsp",
                        PAGES + "billingONStatus.jsp", PAGES + "onGenRA.jsp", PAGES + "onGenRAError.jsp")
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    static Stream<Arguments> bannerPages() {
        return Stream.of(
                Arguments.of(new BannerPage(PAGES + "billingON3rdInv.jsp", "AMOUNT UNREADABLE",
                        "AMOUNT UNREADABLE \u2014 DO NOT REMIT.", "input#invoiceNo",
                        request -> request.setAttribute("invoiceModel", invoice(true)),
                        request -> request.setAttribute("invoiceModel", invoice(false)))),
                Arguments.of(new BannerPage(PAGES + "billingONHistory.jsp", "A loader error truncated this list",
                        "A loader error truncated this list \u2014 please refresh or contact admin before",
                        "table#billingHistoryTable",
                        request -> request.setAttribute("historyModel", history(true)),
                        request -> request.setAttribute("historyModel", history(false)))),
                Arguments.of(new BannerPage(PAGES + "billingONHistorySpec.jsp", "A loader error truncated this list",
                        "A loader error truncated this list \u2014 please refresh or contact admin.",
                        "form[name=titlesearch]",
                        request -> request.setAttribute("historySpecModel", historySpecialist(true)),
                        request -> request.setAttribute("historySpecModel", historySpecialist(false)))),
                Arguments.of(new BannerPage(PAGES + "billingONStatus.jsp", "had unreadable amounts",
                        "3 row(s) had unreadable amounts and were skipped \u2014 refresh, or contact admin if persistent.",
                        "form[name=serviceform]",
                        request -> request.setAttribute("statusModel", status(3)),
                        request -> request.setAttribute("statusModel", status(0)))),
                Arguments.of(new BannerPage(PAGES + "onGenRA.jsp", "RA import failed",
                        "RA import failed \u2014 see server log for details.", "table.report-print-table",
                        request -> {
                            request.setAttribute("onGenRAModel", OnRaViewModel.builder().build());
                            // What ViewGenRa2Action sets when OnRaImportService reports a failed import.
                            request.setAttribute("raImportFailed", Boolean.TRUE);
                        },
                        request -> request.setAttribute("onGenRAModel", OnRaViewModel.builder().build()))),
                Arguments.of(new BannerPage(PAGES + "onGenRAError.jsp", "A loader error truncated this list",
                        "A loader error truncated this list \u2014 refresh, or contact admin if persistent.",
                        "select[name=proNo]",
                        request -> request.setAttribute("onGenRAErrorModel", raError(true)),
                        request -> request.setAttribute("onGenRAErrorModel", raError(false)))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bannerPages")
    void shouldShowTheBannerWithARealEmDash_whenTheLoaderReportedAnIncompleteResult(BannerPage view)
            throws Exception {
        JspRenderServer.RenderedPage page = server.render(view.jsp(), view.failed()).assertOk();

        assertThat(page.header("Content-Type").orElse(""))
                .startsWith("text/html")
                .containsIgnoringCase("charset=UTF-8");
        Document html = page.document();
        List<Element> banners = bannersMentioning(html, view.marker());
        assertThat(banners).hasSize(1);
        assertThat(banners.get(0).ownText())
                .contains(view.sentence())
                .doesNotContain(MISREAD_EM_DASH, REPLACEMENT_CHARACTER, "&#8212;");
        assertThat(page.body()).doesNotContain(MISREAD_EM_DASH, REPLACEMENT_CHARACTER);
        // The banner sits above the page's own content, which must still render after it.
        assertThat(html.select(view.landmark())).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bannerPages")
    void shouldLeaveTheBannerOut_whenTheResultIsComplete(BannerPage view) throws Exception {
        Document html = server.render(view.jsp(), view.normal()).assertOk().document();

        assertThat(bannersMentioning(html, view.marker())).isEmpty();
        assertThat(html.body().text()).doesNotContain(view.marker());
        assertThat(html.select(view.landmark())).hasSize(1);
    }

    /** The divs whose own text carries the banner's opening words, which hold no dash. */
    private static List<Element> bannersMentioning(Document html, String marker) {
        return html.select("div").stream().filter(div -> div.ownText().contains(marker)).toList();
    }

    private static BillingOnThirdPartyInvoiceViewModel invoice(boolean amountsUnreadable) {
        return BillingOnThirdPartyInvoiceViewModel.builder()
                .invoiceNoStr("4242")
                .invoiceLoaded(true)
                .clinicName("FAKE Clinic")
                .patientName("FAKE, Patient")
                .amountsUnreadable(amountsUnreadable)
                .build();
    }

    private static BillingOnHistoryViewModel history(boolean partial) {
        return BillingOnHistoryViewModel.builder()
                .demographicNo("1001")
                .patientDisplayName("FAKE, Patient")
                .partial(partial)
                .build();
    }

    private static BillingOnHistorySpecialistViewModel historySpecialist(boolean partial) {
        return BillingOnHistorySpecialistViewModel.builder()
                .demographicNo("1001")
                .demoName("FAKE, Patient")
                .partial(partial)
                .build();
    }

    /** {@code partialTotal} is derived: it is true when any total row was unreadable. */
    private static BillingOnStatusViewModel status(int unreadableTotalRows) {
        return BillingOnStatusViewModel.builder().unreadableTotalRowCount(unreadableTotalRows).build();
    }

    private static OnRaErrorViewModel raError(boolean partial) {
        return OnRaErrorViewModel.builder().raNo("77").partial(partial).build();
    }

    /**
     * One page: the dash-free opening words that find its banner, the sentence the banner must
     * show, an element of the page's own content, and the request in each state.
     */
    record BannerPage(String jsp, String marker, String sentence, String landmark,
            JspRenderServer.RequestSetup failed, JspRenderServer.RequestSetup normal) {
        @Override
        public String toString() {
            return jsp.substring(jsp.lastIndexOf('/') + 1);
        }
    }
}
