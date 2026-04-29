/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.model.BillingONEAReport;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link BillingOnErrorReportService}, exercising the
 * Action → Service → DAO chain end-to-end against H2 with the real
 * Hibernate/JPA wiring.
 *
 * <p>Specifically pins the post-{@code S-C} contract on
 * {@link BillingOnErrorReportService#updateErrorReportStatus(String, String)}:
 * the method now returns {@code true} only when a row was actually mutated,
 * so the UI's "checked" / "uncheck" AJAX response no longer lies to the
 * provider when the row id has been deleted out from under them. The
 * surrounding 2Action surfaces a 404 in the false branch — verified at the
 * unit level; this test pins the service-layer half.</p>
 *
 * @since 2026-04-29
 */
@DisplayName("BillingOnErrorReportService integration")
@Tag("integration")
@Tag("billing")
@Transactional
public class BillingOnErrorReportServiceIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONEAReportDao billingONEAReportDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private BillingOnErrorReportService service;

    @BeforeEach
    void setUp() {
        service = new BillingOnErrorReportService(billingONEAReportDao);
    }

    @Test
    @DisplayName("should persist status change and return true when record exists")
    void shouldPersistStatusChange_whenRecordExists() {
        BillingONEAReport seeded = persistEAReport('N');

        boolean updated = service.updateErrorReportStatus(
                String.valueOf(seeded.getId()), "Y");

        assertThat(updated)
                .as("updateErrorReportStatus should return true on a real row")
                .isTrue();

        entityManager.flush();
        entityManager.clear();

        BillingONEAReport reloaded = entityManager.find(
                BillingONEAReport.class, seeded.getId());
        assertThat(reloaded.getStatus())
                .as("status field should reflect the first character of `val`")
                .isEqualTo('Y');
    }

    @Test
    @DisplayName("should return false and not throw when record id does not exist")
    void shouldReturnFalse_whenRecordIdDoesNotExist() {
        // No row seeded — pick an id that won't collide.
        boolean updated = service.updateErrorReportStatus("999999999", "Y");

        assertThat(updated)
                .as("updateErrorReportStatus must return false on a missing id "
                        + "so the action surfaces a 404 instead of a misleading "
                        + "\"checked\" AJAX response")
                .isFalse();
    }

    private BillingONEAReport persistEAReport(char status) {
        BillingONEAReport r = new BillingONEAReport();
        r.setProviderOHIPNo("999998");
        r.setGroupNo("0000");
        r.setSpecialty("00");
        r.setProcessDate(new Date());
        r.setHin("1234567890");
        r.setVersion("AB");
        r.setDob(new Date());
        r.setBillingNo(1);
        r.setRefNo("");
        r.setFacility("0000");
        r.setAdmittedDate(new Date());
        r.setClaimError("");
        r.setCode("A001A");
        r.setFee("33.70");
        r.setUnit("1");
        r.setCodeDate(new Date());
        r.setDx("401");
        r.setExp("");
        r.setCodeError("");
        r.setReportName("test.txt");
        r.setStatus(status);
        r.setComment("");
        entityManager.persist(r);
        entityManager.flush();
        return r;
    }
}
