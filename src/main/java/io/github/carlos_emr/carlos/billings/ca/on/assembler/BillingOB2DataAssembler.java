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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingOB2ViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles {@link BillingOB2ViewModel} for {@code billingOB2.jsp}, the
 * read-only OB billing-history popup. Owns the 6 inline
 * {@code SpringUtils.getBean} lookups the JSP body used to perform
 * (DiagnosticCodeDao, ClinicLocationDao, BillingDao, DemographicDao,
 * ProviderDao, BillingDetailDao).
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class BillingOB2DataAssembler {

    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;
    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final ClinicLocationDao clinicLocationDao;
    private final DiagnosticCodeDao diagnosticCodeDao;

    public BillingOB2DataAssembler() {
        this(SpringUtils.getBean(BillingDao.class),
             SpringUtils.getBean(BillingDetailDao.class),
             SpringUtils.getBean(DemographicDao.class),
             SpringUtils.getBean(ProviderDao.class),
             SpringUtils.getBean(ClinicLocationDao.class),
             SpringUtils.getBean(DiagnosticCodeDao.class));
    }

    BillingOB2DataAssembler(BillingDao billingDao,
                            BillingDetailDao billingDetailDao,
                            DemographicDao demographicDao,
                            ProviderDao providerDao,
                            ClinicLocationDao clinicLocationDao,
                            DiagnosticCodeDao diagnosticCodeDao) {
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.clinicLocationDao = clinicLocationDao;
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    /**
     * Build the OB billing-history view model.
     *
     * @param billingNo the {@code billing_no} request parameter (validated
     *                  upstream by the action layer to be a 1-9 digit string)
     * @return populated view model. Empty/stub when {@code billingNo} doesn't
     *         resolve to a {@link Billing} row — the JSP renders blank fields
     *         in that case.
     */
    public BillingOB2ViewModel assemble(String billingNo) {
        BillingOB2ViewModel.Builder b = BillingOB2ViewModel.builder();

        Integer billNoInt = parseInt(billingNo);
        if (billNoInt == null) {
            return b.build();
        }

        Billing bill = billingDao.find(billNoInt);
        if (bill == null) {
            return b.build();
        }
        b.billLoaded(true);

        applyBill(b, bill);

        ClinicLocation clinicLocation = clinicLocationDao.searchBillLocation(1, bill.getClinicRefCode());
        if (clinicLocation != null) {
            b.billLocation(nullToEmpty(clinicLocation.getClinicLocationName()));
        }

        applyDemo(b, String.valueOf(bill.getDemographicNo()));
        applyProviderFields(b, bill);

        BillingDetail bd = billingDetailDao.find(billNoInt);
        if (bd != null) {
            b.billDate(ConversionUtils.toDateString(bd.getAppointmentDate()))
                    .serviceCode(nullToEmpty(bd.getServiceCode()))
                    .serviceDesc(nullToEmpty(bd.getServiceDesc()))
                    .billUnit(nullToEmpty(bd.getBillingUnit()))
                    .billAmount(formatAmount(bd.getBillingAmount()))
                    .diagCode(nullToEmpty(bd.getDiagnosticCode()))
                    .billDetailLoaded(true);

            // Diagnostic-code description: legacy code looped over results
            // and overwrote `diagDesc` each iteration, so the final entry
            // wins. Mirrored here.
            String diagDesc = "";
            List<DiagnosticCode> results = diagnosticCodeDao.searchCode(bd.getDiagnosticCode());
            for (DiagnosticCode dc : results) {
                diagDesc = dc.getDescription();
            }
            b.diagDesc(nullToEmpty(diagDesc));
        }

        return b.build();
    }

    private void applyBill(BillingOB2ViewModel.Builder b, Billing bill) {
        b.demoNo(String.valueOf(bill.getDemographicNo()))
                .demoName(nullToEmpty(bill.getDemographicName()))
                .updateDate(ConversionUtils.toDateString(bill.getUpdateDate()))
                .hin(nullToEmpty(bill.getHin()))
                .billType(nullToEmpty(bill.getStatus()))
                .billTotal(nullToEmpty(bill.getTotal()))
                .visitDate(ConversionUtils.toDateString(bill.getVisitDate()))
                .visitType(nullToEmpty(bill.getVisitType()));
    }

    private void applyDemo(BillingOB2ViewModel.Builder b, String demoNo) {
        if (demoNo == null || demoNo.isEmpty()) return;
        Demographic d = demographicDao.getDemographic(demoNo);
        if (d == null) return;
        b.demoSex(nullToEmpty(d.getSex()))
                .demoAddress(nullToEmpty(d.getAddress()))
                .demoCity(nullToEmpty(d.getCity()))
                .demoProvince(nullToEmpty(d.getProvince()))
                .demoPostal(nullToEmpty(d.getPostal()))
                .demoDob(nullToEmpty(d.getYearOfBirth()) + "-"
                        + nullToEmpty(d.getMonthOfBirth()) + "-"
                        + nullToEmpty(d.getDateOfBirth()));
    }

    private void applyProviderFields(BillingOB2ViewModel.Builder b, Billing bill) {
        Provider p = providerDao.getProvider(bill.getProviderNo());
        if (p != null) {
            b.providerFirst(nullToEmpty(p.getFirstName())).providerLast(nullToEmpty(p.getLastName()));
        }
        p = providerDao.getProvider(bill.getApptProviderNo());
        if (p != null) {
            b.apptProviderFirst(nullToEmpty(p.getFirstName())).apptProviderLast(nullToEmpty(p.getLastName()));
        }
        p = providerDao.getProvider(bill.getAsstProviderNo());
        if (p != null) {
            b.asstProviderFirst(nullToEmpty(p.getFirstName())).asstProviderLast(nullToEmpty(p.getLastName()));
        }
        p = providerDao.getProvider(bill.getCreator());
        if (p != null) {
            b.creatorFirst(nullToEmpty(p.getFirstName())).creatorLast(nullToEmpty(p.getLastName()));
        }
    }

    /**
     * Format the billing amount from its stored "no-decimal cents" form
     * (e.g. "2375" → "23.75"). Mirrors the legacy substring math the JSP
     * did inline. Returns the raw value unchanged if it's too short to
     * have hidden cents.
     */
    private static String formatAmount(String stored) {
        if (stored == null || stored.length() < 3) return nullToEmpty(stored);
        try {
            return stored.substring(0, stored.length() - 2) + "." + stored.substring(stored.length() - 2);
        } catch (IndexOutOfBoundsException e) {
            MiscUtils.getLogger().warn("Could not split billing amount '{}' into dollars/cents; rendering raw", stored);
            return stored;
        }
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
