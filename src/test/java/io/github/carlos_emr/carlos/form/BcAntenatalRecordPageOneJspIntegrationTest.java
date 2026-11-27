/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020DataDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020TextDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders page 1 of both BC antenatal records as a new form opens it (form id 0) and reads the two
 * labels 72fedd972c50 (#6305) repaired. Each page held a raw invalid byte that showed as a
 * replacement character: the BC-AR 2020 language list's "Norwegian Bokm&aring;l" option and the
 * original BC-AR's "ETHNIC ORIGIN OF NEWBORN'S FATHER" label. They are now the {@code &#229;} and
 * {@code &#8217;} entities, so the text survives the ISO-8859-1 read Jasper gives a page with no
 * {@code pageEncoding} (CARLOS' {@code web.xml} sets none, and neither page declares one; this
 * fixture reads them the same way). Before #6336 only {@code JspPageEncodingRegressionTest} read the
 * pages' source text, and the BC-only packaged checks that open them never read these labels.
 *
 * <p>The pages build their properties through the real {@code FrmBCAR2020Record} and
 * {@code FrmBCARRecord} with the patient from the {@code DemographicManager} bean; with form id 0
 * they read no stored form. Neither page nor record keeps a {@code SpringUtils} bean in a static
 * field.</p>
 */
@Tag("integration")
@Tag("form")
class BcAntenatalRecordPageOneJspIntegrationTest {
    private static final String BCAR_2020_PAGE_1 = "/WEB-INF/jsp/form/formBCAR2020pg1.jsp";
    private static final String BCAR_PAGE_1 = "/WEB-INF/jsp/form/formbcarpg1.jsp";
    private static final String PATIENT = "1001";
    private static final String PROVIDER = "999998";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        DemographicManager demographics = mock(DemographicManager.class);
        when(demographics.getDemographic(any(LoggedInInfo.class), any(Integer.class))).thenReturn(patient());
        DemographicExtDao demographicExt = mock(DemographicExtDao.class);
        when(demographicExt.getAllValuesForDemo(any(Integer.class))).thenReturn(Map.of());
        PropertyDao properties = mock(PropertyDao.class);
        when(properties.findByName("resource")).thenReturn(List.of());
        server = JspRenderServer.builder(directory)
                .jsp(BCAR_2020_PAGE_1, BCAR_PAGE_1)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(DemographicManager.class, demographics)
                .bean(DemographicExtDao.class, demographicExt)
                .bean(PropertyDao.class, properties)
                .bean(FormBCAR2020DataDao.class, mock(FormBCAR2020DataDao.class))
                .bean(FormBCAR2020TextDao.class, mock(FormBCAR2020TextDao.class))
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldOfferNorwegianBokmalWithItsRing_inTheBcar2020LanguageList() throws Exception {
        JspRenderServer.RenderedPage page = server.request(BCAR_2020_PAGE_1)
                .parameter("demographic_no", PATIENT)
                .parameter("formId", "0")
                .parameter("provider_no", PROVIDER)
                .render(BcAntenatalRecordPageOneJspIntegrationTest::signIn)
                .assertOk();

        Document html = page.document();
        Elements options = html.select("select[name=s_languagePreferred] option[value=NOB]");
        assertThat(options).hasSize(1);
        assertThat(options.first().text()).isEqualTo("Norwegian Bokmål");
        assertThat(page.body()).contains(">Norwegian Bokm&#229;l</option>");
        assertNoReplacementCharacter(page);
        // The page rendered as the new form, with the patient filled in from the chart.
        assertThat(html.selectFirst("input[name=t_patientSurname]").val()).isEqualTo("FAKE-ANTENATAL");
    }

    @Test
    void shouldLabelTheFathersEthnicOrigin_withATypographicApostrophe() throws Exception {
        JspRenderServer.RenderedPage page = server.request(BCAR_PAGE_1)
                .parameter("demographic_no", PATIENT)
                .parameter("formId", "0")
                .render(BcAntenatalRecordPageOneJspIntegrationTest::signIn)
                .assertOk();

        Element field = page.document().getElementById("pg1_faEthOrig");
        Element label = field.parent().selectFirst("span.small9");
        assertThat(label.text()).isEqualTo("ETHNIC ORIGIN OF NEWBORN’S FATHER");
        assertThat(page.body()).contains("ETHNIC ORIGIN OF NEWBORN&#8217;S");
        assertNoReplacementCharacter(page);
    }

    /** Neither a decoded U+FFFD nor its UTF-8 bytes read as ISO-8859-1 ("&iuml;&iquest;&frac12;"). */
    private static void assertNoReplacementCharacter(JspRenderServer.RenderedPage page) {
        assertThat(page.body()).doesNotContain("�");
        assertThat(page.body()).doesNotContain("ï¿½");
    }

    private static Demographic patient() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(Integer.valueOf(PATIENT));
        patient.setLastName("FAKE-ANTENATAL");
        patient.setFirstName("FAKE");
        patient.setSex("F");
        patient.setYearOfBirth("1990");
        patient.setMonthOfBirth("05");
        patient.setDateOfBirth("12");
        patient.setProvince("BC");
        patient.setHcType("BC");
        return patient;
    }

    private static void signIn(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", PROVIDER);
        LoggedInInfo login = new LoggedInInfo();
        login.setLoggedInProvider(new Provider(PROVIDER));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
    }
}
