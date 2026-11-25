/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
class BillingOnFileAccessServiceIntegrationTest extends CarlosTestBase {
    @PersistenceContext private EntityManager em;
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final ProviderDataDao providers = mock(ProviderDataDao.class);
    private final LoggedInInfo login = mock(LoggedInInfo.class);
    private BillingOnFileAccessService access;
    private static final String WEST = "827661";
    private static final String EAST = "827662";

    @BeforeEach
    void setUp() {
        access = new BillingOnFileAccessService(security, providers);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(access);
        when(login.getLoggedInProviderNo()).thenReturn(WEST);
        when(security.hasPrivilege(login, "_billing", "r", null)).thenReturn(true);
        when(security.hasPrivilege(login, "_team_billing_only", "r", null)).thenReturn(true);
        var member = mock(ProviderData.class);
        when(member.getId()).thenReturn(WEST);
        when(providers.findByProviderTeam(WEST)).thenReturn(List.of(member));
    }

    @Test
    void shouldCheckEveryDiskMember_whileAllowingTheirIndividualHtml() {
        int disk = disk("fixture-mixed.001");
        child(disk, WEST, "fixture-west.html", "U");
        child(disk, EAST, "fixture-east.html", "D");
        disk("fixture-no-members.001");
        assertThat(access.filePermissions(List.of("fixture-mixed.001", "fixture-west.html", "fixture-east.html",
                        "fixture-no-members.001", "fixture-unknown.001", "FIXTURE-WEST.HTML"), Set.of(WEST)))
                .containsEntry("fixture-west.html", true)
                .containsEntry("fixture-mixed.001", false)
                .containsEntry("fixture-east.html", false)
                .containsEntry("fixture-no-members.001", false)
                .doesNotContainKeys("fixture-unknown.001", "FIXTURE-WEST.HTML");
        assertThat(access.canDownload(login, "fixture-west.html", false)).isTrue();
        assertThat(access.canDownload(login, "fixture-mixed.001", false)).isFalse();
        assertThat(access.canDownload(login, "fixture-unknown.001", false)).isFalse();
        assertThat(access.filePermissions(List.of("fixture-mixed.001"), Set.of(WEST, EAST)))
                .containsEntry("fixture-mixed.001", true);
    }

    @Test
    void shouldRequireAUniqueLegacyOhipOwner_acrossInactiveProvidersAndBothFileTables() {
        provider(WEST, "846711", "1");
        provider(EAST, "846712", "1");
        provider("827663", "846713", "1");
        provider("827664", "846713", "0");
        legacy("846711", "fixture-legacy.001", "fixture-own.htm");
        legacy("846712", "fixture-legacy.001", "fixture-outside.htm");
        legacy("846713", "fixture-unknown.ohip", "fixture-ambiguous.htm");
        legacy(null, "fixture-none.ohip", "fixture-unlinked.htm");
        child(disk("fixture-modern.001"), EAST, "fixture-own.htm", "U");
        assertThat(access.uniqueLegacyOwners(List.of("846711", "846712", "846713", "846714")))
                .containsEntry("846711", WEST).containsEntry("846712", EAST)
                .containsEntry("846713", "").doesNotContainKey("846714");
        assertThat(access.filePermissions(List.of("fixture-legacy.001", "fixture-own.htm", "fixture-ambiguous.htm",
                        "fixture-outside.htm", "fixture-unlinked.htm"), Set.of(WEST)))
                .hasSize(5).doesNotContainValue(true);
        assertThat(access.filePermissions(List.of("fixture-legacy.001", "fixture-own.htm", "fixture-outside.htm"),
                        Set.of(WEST, EAST))).hasSize(3).doesNotContainValue(false);
    }

    @Test
    void shouldKeepTheBillingReadGateAndAllowAnIssuedNonbillingReport_inACoLocatedDirectory() {
        when(security.hasPrivilege(login, "_billing", "r", null)).thenReturn(false);
        when(security.hasPrivilege(login, "_report", "r", null)).thenReturn(true);
        child(disk("fixture-mixed.001"), WEST, "fixture-local.html", "U");
        assertThat(access.canDownload(null, "fixture-local.html", false)).isFalse();
        assertThat(access.canDownload(login, null, false)).isFalse();
        assertThat(access.canDownload(login, " ", true)).isFalse();
        assertThat(access.canDownload(login, "fixture-local.html", false)).isFalse();
        assertThat(access.canDownload(login, "fixture-local.html", true)).isFalse();
        assertThat(access.canDownload(login, "OBECE0123456789.TXT", true)).isTrue();
        when(security.hasPrivilege(login, "_billing", "r", null)).thenReturn(true);
        when(security.hasPrivilege(login, "_team_billing_only", "r", null)).thenReturn(false);
        assertThat(access.canDownload(login, "ordinary-historical-file.001", false)).isTrue();
    }

    private int disk(String filename) {
        var disk = new BillingONDiskName();
        disk.setOhipFilename(filename);
        disk.setMonthCode("A");
        disk.setBatchCount(1);
        disk.setGroupNo("0000");
        disk.setCreator(WEST);
        disk.setClaimRecord("1");
        disk.setStatus("U");
        disk.setTotal("1.00");
        disk.setCreateDateTime(new Date());
        disk.setTimestamp(new Date());
        em.persist(disk);
        return disk.getId();
    }

    private void child(int disk, String provider, String html, String status) {
        var file = new BillingONFilename();
        file.setDiskId(disk);
        file.setProviderNo(provider);
        file.setProviderOhipNo(provider);
        file.setHtmlFilename(html);
        file.setClaimRecord("1");
        file.setStatus(status);
        file.setTotal("1.00");
        file.setTimestamp(new Date());
        em.persist(file);
    }

    private void provider(String id, String ohip, String status) {
        var provider = new Provider(id, "Synthetic", "doctor", "F", "00", "Fixture");
        provider.setOhipNo(ohip);
        provider.setStatus(status);
        provider.setLastUpdateUser(WEST);
        provider.setLastUpdateDate(new Date());
        em.persist(provider);
    }

    private void legacy(String ohip, String claim, String html) {
        var activity = new BillActivity();
        activity.setOhipFilename(claim);
        activity.setHtmlFilename(html);
        activity.setProviderOhipNo(ohip);
        activity.setMonthCode("A");
        activity.setBatchCount(1);
        activity.setGroupNo("0000");
        activity.setCreator(WEST);
        activity.setClaimRecord("1");
        activity.setStatus("U");
        activity.setTotal("1.00");
        activity.setUpdateDateTime(new Date());
        em.persist(activity);
    }
}
