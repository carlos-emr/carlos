/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOhipSimulationViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnMriViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOhipSimulationViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnMriViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOhipSimulation2Action;
import io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOnMri2Action;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import javax.sql.DataSource;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Both OHIP form actions with real Ontario provider, site and disk-filename queries. */
@Tag("integration")
@Tag("dao")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_PROVINCE", matches = "on")
class BillingOnSiteVisibilityIntegrationTest extends CarlosTestBase {
    private static final String ADMIN = "847210";
    private static final String LOCAL = "847211";
    private static final String OTHER = "847212";
    private static final String ADMIN_HTML = "visibility-own.html";
    private static final String LOCAL_HTML = "visibility-local.html";
    private static final String OTHER_HTML = "visibility-other.html";
    @Autowired private DataSource dataSource;
    @Autowired private BillingDao billingDao;
    @Autowired private BillingDetailDao billingDetailDao;
    @jakarta.persistence.PersistenceContext(unitName = "entityManagerFactory")
    private jakarta.persistence.EntityManager entityManager;
    @Autowired private ProviderDao providerDao;
    @Autowired private ProviderSiteDao providerSiteDao;
    @Autowired private ProviderDataDao providerDataDao;
    @Autowired private ProviderBillCenterDao providerBillCenterDao;
    @Autowired private BillActivityDao billActivityDao;
    @Autowired private BillingONDiskNameDao diskNameDao;
    @Autowired private BillingONFilenameDao filenameDao;
    private JdbcTemplate jdbc;
    private BillingReviewLoader reviews;
    private BillingOnLookupService lookup;

    @BeforeEach
    void seedPriorGeneration() {
        jdbc = new JdbcTemplate(dataSource);
        for (String id : List.of(ADMIN, LOCAL, OTHER)) {
            jdbc.update("""
                    INSERT INTO provider (provider_no, first_name, last_name, provider_type, sex, ohip_no, status, lastUpdateUser, lastUpdateDate)
                    VALUES (?, 'FAKE', 'OHIP visibility', 'doctor', 'F', ?, '1', ?, NOW())
                    """, id, id.equals(ADMIN) ? "" : id, ADMIN);
        }
        jdbc.update("""
                INSERT INTO billing_on_diskname (monthCode, batchcount, ohipfilename, groupno, creator, claimrecord, createdatetime, status, total)
                VALUES ('A', 1, 'visibility.ohip', '0000', ?, '2', '2025-04-05 12:01:00', 'U', '14.50')
                """, ADMIN);
        Integer disk = jdbc.queryForObject("SELECT id FROM billing_on_diskname WHERE ohipfilename='visibility.ohip'", Integer.class);
        for (String id : List.of(LOCAL, OTHER)) {
            jdbc.update("""
                    INSERT INTO billing_on_filename (disk_id, htmlfilename, providerohipno, providerno, claimrecord, status, total)
                    VALUES (?, ?, ?, ?, '1', 'U', '7.25')
                    """, disk, id.equals(LOCAL) ? LOCAL_HTML : OTHER_HTML, id, id);
        }
        lookup = new BillingOnLookupService(null, null, null, providerDao, null, null, null, filenameDao, providerSiteDao, null);
        reviews = new BillingReviewLoader(null, new BillingOnDiskLoader(null, diskNameDao, filenameDao, mock(BillingOnOutputFilenames.class)), lookup, null);
    }

    @Test
    void shouldShowUnassignedBillersAndDisks_whenSingleSiteHasSeededPrivacyGrant() throws Exception {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup='admin' AND objectName='_site_access_privacy' AND privilege='x'
                """, Integer.class)).isOne();
        assertThat(providerSiteDao.findByProviderNo(ADMIN)).isEmpty();
        assertThat(providerSiteDao.findByProviderNo(LOCAL)).isEmpty();
        assertThat(providerSiteDao.findByProviderNo(OTHER)).isEmpty();
        assertViews(false, true, List.of(LOCAL, OTHER), List.of(LOCAL_HTML, OTHER_HTML));
    }

    @Test
    void shouldRestrictBothViewsAndDiskRows_whenMultisitePrivacyIsGranted() throws Exception {
        assertViews(true, true, List.of(), List.of());
        shareSiteWithLocalProvider();
        assertViews(true, true, List.of(LOCAL), List.of(LOCAL_HTML));
    }

    @Test
    void shouldShowUnassignedBillersAndDisks_whenMultisiteHasNoPrivacyGrant() throws Exception {
        assertViews(true, false, List.of(LOCAL, OTHER), List.of(LOCAL_HTML, OTHER_HTML));
    }

    @Test
    void shouldLimitHistoryToTheTeam_whenSingleSiteLoginHasOnlyTeamBillingAndSeededSiteGrants() throws Exception {
        jdbc.update("UPDATE provider SET team='ohip-team' WHERE provider_no IN (?, ?)", ADMIN, LOCAL);
        assertViews(false, true, "_team_billing_only", List.of(LOCAL), List.of(LOCAL_HTML));
        jdbc.update("UPDATE provider SET team='' WHERE provider_no IN (?, ?, ?)", ADMIN, LOCAL, OTHER);
        jdbc.update("UPDATE provider SET ohip_no=? WHERE provider_no=?", ADMIN, ADMIN);
        jdbc.update("""
                INSERT INTO billing_on_filename (disk_id, htmlfilename, providerohipno, providerno, claimrecord, status, total)
                SELECT id, ?, ?, ?, '1', 'U', '2.00' FROM billing_on_diskname WHERE ohipfilename='visibility.ohip'
                """, ADMIN_HTML, ADMIN, ADMIN);
        assertViews(false, true, "_team_billing_only", List.of(ADMIN), List.of(ADMIN_HTML));
    }

    @Test
    void shouldRequireTeamAndSiteForHistory_whenOnlyTeamBillingAndMultisiteGrantsArePresent() throws Exception {
        jdbc.update("UPDATE provider SET team='ohip-team' WHERE provider_no IN (?, ?, ?)", ADMIN, LOCAL, OTHER);
        assertViews(true, true, "_team_billing_only", List.of(LOCAL, OTHER), List.of());
        shareSiteWithLocalProvider();
        assertViews(true, true, "_team_billing_only", List.of(LOCAL, OTHER), List.of(LOCAL_HTML));
    }

    @Test
    void shouldRetainTeamAccessPrecedence_whenBothMultisiteAndTeamPrivacyAreGranted() throws Exception {
        jdbc.update("UPDATE provider SET team='ohip-team' WHERE provider_no IN (?, ?)", ADMIN, OTHER);
        shareSiteWithLocalProvider();
        assertViews(true, true, "_team_access_privacy", List.of(OTHER), List.of(OTHER_HTML));
    }

    @Test
    void shouldMarkOnlyTheAuthorizedLegacyClaim_whenTwoBillersShareAnOhipNumber() {
        jdbc.update("UPDATE provider SET ohip_no=? WHERE provider_no IN (?, ?)", LOCAL, LOCAL, OTHER);
        for (String id : List.of(LOCAL, OTHER)) {
            jdbc.update("""
                    INSERT INTO billing (provider_no, provider_ohip_no, billing_date, visitdate, visittype, dob, hin,
                                         demographic_name, clinic_ref_code, content, status)
                    VALUES (?, ?, '2025-04-03', '2025-04-03', '00', '19700101', '9999999999',
                            'FAKE billing test', '', '', 'O')
                    """, id, LOCAL);
        }
        var extract = new OhipClaimExtractService(billingDao, billingDetailDao);
        extract.setProviderNo(LOCAL);
        extract.setBatchCount("1");
        extract.setOhipVer("V03");
        extract.setOhipCenter("4");
        extract.setGroupNo("0000");
        extract.setSpecialty("00");
        extract.seteFlag("1");
        var original = billingDao.findByProviderStatusAndDates(LOCAL, LOCAL, List.of("O"), null);
        assertThat(original).singleElement().satisfies(claim -> assertThat(claim.getAppointmentNo()).isZero());
        extract.dbQueryForProvider(LOCAL);
        entityManager.flush();

        assertThat(jdbc.queryForList("SELECT status FROM billing WHERE provider_ohip_no=? ORDER BY provider_no",
                String.class, LOCAL)).containsExactly("B", "O");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing WHERE provider_ohip_no=? AND appointment_no IS NULL",
                Integer.class, LOCAL)).isEqualTo(2);
    }

    private void shareSiteWithLocalProvider() {
        jdbc.update("INSERT INTO site (name, short_name, status) VALUES ('OHIP visibility fixture', 'ohip-vis', 1)");
        Integer siteId = jdbc.queryForObject("SELECT site_id FROM site WHERE short_name='ohip-vis'", Integer.class);
        for (String provider : List.of(ADMIN, LOCAL)) {
            jdbc.update("INSERT INTO providersite (provider_no, site_id) VALUES (?, ?)", provider, siteId);
        }
    }

    private void assertViews(boolean multisites, boolean siteGrant, List<String> expectedProviders,
                             List<String> expectedHtmlFiles) throws Exception {
        assertViews(multisites, siteGrant, "", expectedProviders, expectedHtmlFiles);
    }

    private void assertViews(boolean multisites, boolean siteGrant, String teamGrant,
                             List<String> expectedProviders, List<String> expectedHtmlFiles) throws Exception {
        var request = new MockHttpServletRequest();
        request.addParameter("year", "2025");
        var login = new LoggedInInfo();
        login.setLoggedInProvider(new Provider(ADMIN));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(login, "_billing", "r", null)).thenReturn(true);
        when(security.hasPrivilege(login, "_site_access_privacy", "r", null)).thenReturn(siteGrant);
        if (!teamGrant.isEmpty()) {
            when(security.hasPrivilege(login, teamGrant, "r", null)).thenReturn(true);
        }
        var simulation = new BillingOhipSimulationViewModelAssembler(reviews, lookup, null);
        var fileAccess = new BillingOnFileAccessService(security, providerDataDao);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(fileAccess);
        var generation = new BillingOnMriViewModelAssembler(providerDao, billActivityDao, providerDataDao,
                providerBillCenterDao, security, reviews, lookup, fileAccess);

        try (var properties = mockStatic(IsPropertiesOn.class); var struts = mockStatic(ServletActionContext.class)) {
            properties.when(IsPropertiesOn::isMultisitesEnable).thenReturn(multisites);
            struts.when(ServletActionContext::getRequest).thenReturn(request);
            assertThat(new ViewBillingOhipSimulation2Action(security, simulation).execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(new ViewBillingOnMri2Action(security, generation).execute()).isEqualTo(ActionSupport.SUCCESS);
        }
        var simulationView = (BillingOhipSimulationViewModel) request.getAttribute("simulationModel");
        var generationView = (BillingOnMriViewModel) request.getAttribute("mriModel");
        var fixtureIds = List.of(ADMIN, LOCAL, OTHER);
        assertThat(simulationView.getProviders()).extracting(BillingOhipSimulationViewModel.ProviderOption::providerNo)
                .filteredOn(fixtureIds::contains).containsExactlyInAnyOrderElementsOf(expectedProviders);
        assertThat(generationView.getProviderOptions()).extracting(BillingOnMriViewModel.ProviderEntry::providerNo)
                .filteredOn(fixtureIds::contains).containsExactlyInAnyOrderElementsOf(expectedProviders);
        int diskId = jdbc.queryForObject("SELECT id FROM billing_on_diskname WHERE ohipfilename='visibility.ohip'", Integer.class);
        assertThat(generationView.getMriRows()).filteredOn(row -> row.diskId() == diskId)
                .extracting(BillingOnMriViewModel.MriRow::htmlFile).containsExactlyInAnyOrderElementsOf(expectedHtmlFiles);
    }
}
