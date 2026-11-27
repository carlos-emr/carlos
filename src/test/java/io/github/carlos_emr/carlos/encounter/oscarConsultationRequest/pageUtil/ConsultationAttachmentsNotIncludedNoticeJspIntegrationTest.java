/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import org.jsoup.nodes.Document;
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
 * Renders the consultation omission notice ({@code attachmentsNotIncludedNotice.jspf}) through
 * {@code ConfirmConsultationRequest.jsp}, the smallest real page that statically includes it, with
 * the warnings {@link ConsultationAttachmentsNotIncluded#describe} really produces for attachments
 * the reader cannot see.
 *
 * <p>9c2014ef3867 (#6134, #6135) marks every warning with {@code data-hrm-omission}: {@code true}
 * exactly when it is the generic HRM message (the page compares it with the same bundle message
 * the producer used), {@code false} for any other omission. {@code ConsultationFormRequest.jsp}
 * (line 1040) depends on that: after the last unavailable HRM attachment is explicitly removed it
 * deletes {@code #consultAttachmentsNotIncluded li[data-hrm-omission="true"]} without a reload and
 * keeps the other omissions. That 4,000-line form is not rendered here and the removal itself runs
 * only in a browser; this test pins the markup the script selects on, through the same fragment, in
 * English and French, for every mix of form and HRM omissions. Before #6336 no test read the
 * attribute.</p>
 */
@Tag("integration")
@Tag("consultation")
class ConsultationAttachmentsNotIncludedNoticeJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/encounter/oscarConsultationRequest/ConfirmConsultationRequest.jsp";
    private static final String PREFIX = "encounter.oscarConsultationRequest.attachmentsNotIncluded.";
    /** The rows the consultation form's HRM detach handler removes, in its own selector's terms. */
    private static final String HRM_OMISSIONS = "#consultAttachmentsNotIncluded li[data-hrm-omission='true']";
    private static final int CONSULTATION = 500;
    private static final List<EctFormData.PatientForm> NO_VISIBLE_FORMS = List.of();
    private static final List<Map<String, Object>> NO_VISIBLE_HRMS = List.of();

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

    static Stream<Arguments> omissions() {
        return Stream.of(
                Arguments.of("en", true, true),
                Arguments.of("en", false, true),
                Arguments.of("en", true, false),
                Arguments.of("fr-CA", true, true));
    }

    @ParameterizedTest(name = "{0}: form omitted {1}, HRM omitted {2}")
    @MethodSource("omissions")
    void shouldFlagOnlyTheHrmOmission_forTheFormsLiveRemoval(String language, boolean formOmitted,
            boolean hrmOmitted) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));
        String hrmWarning = bundle.getString(PREFIX + "restricted");
        List<String> expectedText = new ArrayList<>();
        List<String> expectedFlags = new ArrayList<>();
        List<String> expectedHrmRows = new ArrayList<>();
        if (formOmitted) {
            expectedText.add(bundle.getString(PREFIX + "form"));
            expectedFlags.add("false");
        }
        if (hrmOmitted) {
            expectedText.add(hrmWarning);
            expectedFlags.add("true");
            expectedHrmRows.add(hrmWarning);
        }

        Document html = server.request(PAGE)
                .header("Accept-Language", language)
                .render(request -> confirmWithOmissions(request, formOmitted, hrmOmitted))
                .assertOk()
                .document();

        Elements items = html.select("#consultAttachmentsNotIncluded li");
        assertThat(items.eachText()).containsExactlyElementsOf(expectedText);
        assertThat(items.eachAttr("data-hrm-omission")).containsExactlyElementsOf(expectedFlags);
        assertThat(html.select(HRM_OMISSIONS).eachText()).containsExactlyElementsOf(expectedHrmRows);
        assertThat(html.selectFirst("#consultAttachmentsNotIncluded strong").text())
                .isEqualTo(bundle.getString(PREFIX + "title"));
    }

    @Test
    void shouldRenderNoNotice_whenEveryAttachmentIsAvailable() throws Exception {
        Document html = server.render(PAGE, request -> confirmWithOmissions(request, false, false))
                .assertOk()
                .document();

        assertThat(html.select("#consultAttachmentsNotIncluded")).isEmpty();
        assertThat(html.select("[data-hrm-omission]")).isEmpty();
    }

    /**
     * What a consultation print or fax confirmation leaves: stored form and HRM attachments that
     * are not in the reader's visible lists become the localized warnings, through the producer
     * the consultation pages call with the request's negotiated locale.
     */
    private static void confirmWithOmissions(HttpServletRequest request, boolean formOmitted, boolean hrmOmitted) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        request.setAttribute("transType", "1");
        List<ConsultDocs> storedForms = formOmitted
                ? List.of(new ConsultDocs(CONSULTATION, 11, ConsultDocs.DOCTYPE_FORM, "999998")) : List.of();
        List<ConsultDocs> storedHrms = hrmOmitted
                ? List.of(new ConsultDocs(CONSULTATION, 12, ConsultDocs.DOCTYPE_HRM, "999998")) : List.of();
        request.setAttribute(ConsultationAttachmentsNotIncluded.REQUEST_ATTRIBUTE,
                ConsultationAttachmentsNotIncluded.describe(storedForms, storedHrms, NO_VISIBLE_FORMS,
                        NO_VISIBLE_HRMS, LocaleUtils.resolveBundleLocale(request)));
    }
}
