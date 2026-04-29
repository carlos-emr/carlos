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

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Write access to the {@code billing_service} table — the private-code
 * admin workflow (add / update / delete). Split out of the former
 * {@code BillingONServiceCodeService}; reads now live on
 * {@link ServiceCodeLoader}.
 *
 * @since 2026-04-27
 */
@Service
@Transactional
public class ServiceCodePersister {

    private final BillingServiceDao dao;

    /** Test-friendly constructor — package-private, takes a DAO mock directly. */
    ServiceCodePersister(BillingServiceDao dao) {
        this.dao = dao;
    }

    public boolean updateCodeByName(String serviceCode, String description, String value,
                                    String percentage, String billingservice_date, String gstFlag) {
        List<BillingService> bs = dao.findByServiceCode(serviceCode);
        for (BillingService b : bs) {
            b.setDescription(description);
            b.setValue(value);
            b.setPercentage(percentage);
            b.setGstFlag(Boolean.valueOf(gstFlag));
            b.setBillingserviceDate(ConversionUtils.fromDateString(billingservice_date));
            dao.merge(b);
        }
        return true;
    }

    public int addCodeByStr(String serviceCode, String description, String value,
                            String percentage, String billingservice_date, String gstFlag) {
        BillingService b = new BillingService();
        b.setServiceCompositecode("");
        b.setServiceCode(serviceCode);
        b.setDescription(description);
        b.setValue(value);
        b.setPercentage(percentage);
        b.setGstFlag("1".equals(gstFlag));
        b.setBillingserviceDate(ConversionUtils.fromDateString(billingservice_date));
        b.setSliFlag(false);
        b.setTerminationDate(ConversionUtils.fromDateString("9999-12-31"));

        dao.persist(b);
        return b.getId();
    }

    public boolean deletePrivateCode(String serviceCode) {
        List<BillingService> bs = dao.findByServiceCode(serviceCode);
        for (BillingService b : bs) {
            dao.remove(b.getId());
        }
        return true;
    }

    /**
     * Bulk-update the description on every {@link BillingService} row sharing
     * a service code. Used by the {@code billingCodeUpdate.jsp} popup's
     * "update &lt;code&gt;" branch.
     *
     * @param serviceCode    String 5-char service code (e.g. {@code "A001A"})
     * @param newDescription String replacement description
     * @return int count of rows merged
     */
    public int updateDescriptionByServiceCode(String serviceCode, String newDescription) {
        List<BillingService> bs = dao.findByServiceCode(serviceCode);
        int updated = 0;
        for (BillingService b : bs) {
            b.setDescription(newDescription);
            dao.merge(b);
            updated++;
        }
        return updated;
    }
}
