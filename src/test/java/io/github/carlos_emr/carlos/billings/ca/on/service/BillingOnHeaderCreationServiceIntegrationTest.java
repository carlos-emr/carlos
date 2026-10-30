/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.billing.CA.dao.GstControlDao;
import io.github.carlos_emr.carlos.billing.CA.model.GstControl;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Batch-bill totals against the real fee-schedule and percentage-limit tables (issue #5307).
 *
 * <p>The fee and limit lookups use the real DAOs; the patient, provider, GST and header
 * collaborators are mocks, because the total is the subject here. Each fee code is a
 * {@code Y53..X} code no seeded fee schedule contains, so the Flyway schema run sees only
 * these rows.</p>
 *
 * @since 2026-10-29
 */
@DisplayName("Batch-bill totals apply the percentage limit of the code (issue #5307)")
@Tag("integration")
@Tag("billing")
class BillingOnHeaderCreationServiceIntegrationTest extends CarlosTestBase {

    private static final String BASE_CODE = "Y5350X";
    private static final String PERCENT_CODE = "Y5351X";

    @Autowired
    private BillingServiceDao billingServiceDao;

    @Autowired
    private BillingPercLimitDao billingPercLimitDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private BillingOnHeaderCreationService service;

    @BeforeEach
    void setUp() {
        ProviderDao providers = mock(ProviderDao.class);
        DemographicDao demographics = mock(DemographicDao.class);
        GstControlDao gst = mock(GstControlDao.class);
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        when(providers.getProvider("999998")).thenReturn(provider);
        Demographic patient = new Demographic();
        patient.setDemographicNo(1);
        patient.setHcType("ON");
        when(demographics.getDemographicById(1)).thenReturn(patient);
        GstControl noGst = new GstControl();
        noGst.setGstPercent(BigDecimal.ZERO);
        when(gst.find(any(Integer.class))).thenReturn(noGst);
        service = new BillingOnHeaderCreationService(mock(BillingONCHeader1Dao.class), providers, demographics,
                billingServiceDao, gst, billingPercLimitDao);
    }

    @Test
    @DisplayName("should clamp to the code's own limit, not a limit keyed by the fee row's id")
    void shouldClampToTheCodesOwnLimit_notOneKeyedByTheFeeRowId() throws Exception {
        persistFees("2019-01-01");
        BillingService percentRow = billingServiceDao.findByServiceCode(PERCENT_CODE).get(0);
        // What the removed mapping joined on: a limit whose service_code is the row's numeric id.
        persistLimit(String.valueOf(percentRow.getId()), "2019-01-01", "0.00", "1.00");
        persistLimit(PERCENT_CODE, "2019-01-01", "0.00", "10.00");
        entityManager.flush();
        entityManager.clear();

        // 30.00 + min(30.00 x 0.50, 10.00)
        assertThat(total(day("2024-06-01"))).isEqualTo("40.00");
    }

    @Test
    @DisplayName("should use the limit in effect on the service date")
    void shouldUseTheLimitInEffect_onTheServiceDate() throws Exception {
        persistFees("2019-01-01");
        persistLimit(PERCENT_CODE, "2020-01-01", "0.00", "5.00");
        persistLimit(PERCENT_CODE, "2025-01-01", "0.00", "12.00");
        entityManager.flush();
        entityManager.clear();

        assertThat(total(day("2019-06-01"))).as("before any limit").isEqualTo("45.00");
        assertThat(total(day("2024-06-01"))).as("2020 limit").isEqualTo("35.00");
        assertThat(total(day("2025-01-01"))).as("2025 limit, its first day").isEqualTo("42.00");
    }

    @Test
    @DisplayName("should apply a limit taking effect today to a service at a later hour today")
    void shouldApplyLimitTakingEffectToday_toServiceLaterToday() throws Exception {
        persistFees("2019-01-01");
        Calendar startOfToday = Calendar.getInstance();
        startOfToday.set(Calendar.HOUR_OF_DAY, 0);
        startOfToday.set(Calendar.MINUTE, 0);
        startOfToday.set(Calendar.SECOND, 0);
        startOfToday.set(Calendar.MILLISECOND, 0);
        persistLimit(PERCENT_CODE, new SimpleDateFormat("yyyy-MM-dd").format(startOfToday.getTime()), "20.00", "99.00");
        entityManager.flush();
        entityManager.clear();
        Calendar laterToday = (Calendar) startOfToday.clone();
        laterToday.set(Calendar.HOUR_OF_DAY, 14);

        // 30.00 + max(15.00, 20.00)
        assertThat(total(laterToday.getTime())).isEqualTo("50.00");
    }

    private String total(Date serviceDate) {
        return service.createBills("999998", List.of("1"), List.of(BASE_CODE, PERCENT_CODE), List.of("401"),
                "00001", serviceDate, "999998");
    }

    private void persistFees(String effective) throws Exception {
        persistFee(BASE_CODE, "30.00", null, effective);
        persistFee(PERCENT_CODE, "", "0.50", effective);
    }

    private void persistFee(String code, String value, String percentage, String effective) throws Exception {
        BillingService fee = new BillingService();
        fee.setServiceCode(code);
        fee.setRegion("ON");
        fee.setValue(value);
        fee.setPercentage(percentage);
        fee.setDescription("Issue 5307 fee");
        fee.setBillingserviceDate(day(effective));
        fee.setTerminationDate(day("9999-12-31"));
        fee.setGstFlag(false);
        fee.setSliFlag(false);
        billingServiceDao.persist(fee);
    }

    private void persistLimit(String code, String effective, String min, String max) throws Exception {
        BillingPercLimit limit = new BillingPercLimit();
        limit.setService_code(code);
        limit.setEffective_date(day(effective));
        limit.setMin(min);
        limit.setMax(max);
        billingPercLimitDao.persist(limit);
    }

    private static Date day(String yyyyMmDd) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd").parse(yyyyMmDd);
    }
}
