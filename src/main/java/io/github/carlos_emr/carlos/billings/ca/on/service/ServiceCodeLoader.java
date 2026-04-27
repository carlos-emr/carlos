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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Read-only access to the {@code billing_service} table — billing-code lookup
 * helpers used by the correction-review and edit-private-code pages. Split out
 * of the former {@code BillingONServiceCodeService} (which combined reads and
 * writes under a misleading double-{@code Service} name); writes now live on
 * {@link ServiceCodePersister}.
 *
 * @since 2026-04-27
 */
@Service
@Transactional(readOnly = true)
public class ServiceCodeLoader {

    private final BillingServiceDao dao;

    /** Test-friendly constructor — package-private, takes a DAO mock directly. */
    ServiceCodeLoader(BillingServiceDao dao) {
        this.dao = dao;
    }

    public List getBillingCodeAttr(String serviceCode) {
        List ret = new ArrayList();
        List<BillingService> bs = dao.getBillingCodeAttr(serviceCode);
        for (BillingService b : bs) {
            ret.add(serviceCode);
            ret.add(b.getDescription());
            ret.add(b.getValue());
            ret.add(b.getPercentage());
            ret.add(ConversionUtils.toDateString(b.getBillingserviceDate()));
            ret.add(b.getGstFlag());
        }
        return ret;
    }

    public Properties getCodeDescByNames(List serviceCodeNames) {
        Properties ret = new Properties();
        List<String> serviceCodeList = new ArrayList<String>();
        serviceCodeList.addAll(serviceCodeNames);
        List<BillingService> bs = dao.findByServiceCodes(serviceCodeList);
        for (BillingService b : bs) {
            ret.setProperty(b.getServiceCode(), b.getDescription());
        }
        return ret;
    }

    public List<String> getPrivateBillingCodeDesc() {
        List<String> ret = new ArrayList<String>();
        List<BillingService> bs = dao.finAllPrivateCodes();
        for (BillingService b : bs) {
            ret.add(b.getServiceCode());
            ret.add(b.getDescription());
        }
        return ret;
    }
}
