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

import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;

/**
 * Atomic boundary for the seven {@code Manage Billing Form / Location}
 * configuration mutations. Each method bundles the multi-row write under a
 * single {@code @Transactional} boundary so a mid-loop failure rolls every
 * write back rather than leaving the table inconsistent.
 *
 * <p>The actions still parse the request and build entity objects; this
 * service receives pre-built lists and delegates to the DAOs. Keeps web-
 * layer parsing out of the service while bounding writes atomically.</p>
 *
 * @since 2026-04-30
 */
@Service
@Transactional
public class BillingFormConfigurationService {

    private final CtlBillingServiceDao ctlBillingServiceDao;
    private final CtlDiagCodeDao ctlDiagCodeDao;
    private final CtlBillingTypeDao ctlBillingTypeDao;
    private final CtlBillingServicePremiumDao ctlBillingServicePremiumDao;
    private final ClinicLocationDao clinicLocationDao;

    public BillingFormConfigurationService(CtlBillingServiceDao ctlBillingServiceDao,
                                           CtlDiagCodeDao ctlDiagCodeDao,
                                           CtlBillingTypeDao ctlBillingTypeDao,
                                           CtlBillingServicePremiumDao ctlBillingServicePremiumDao,
                                           ClinicLocationDao clinicLocationDao) {
        this.ctlBillingServiceDao = ctlBillingServiceDao;
        this.ctlDiagCodeDao = ctlDiagCodeDao;
        this.ctlBillingTypeDao = ctlBillingTypeDao;
        this.ctlBillingServicePremiumDao = ctlBillingServicePremiumDao;
        this.clinicLocationDao = clinicLocationDao;
    }

    /**
     * Action #1: Atomically remove every CtlBillingService + CtlDiagCode +
     * CtlBillingType row for the given service-type id. Mid-loop failure
     * rolls back every prior remove. Used by ManageBillingFormDelete2Action.
     */
    public void deleteServiceTypeAndCascade(String typeid) {
        for (CtlBillingService b : ctlBillingServiceDao.findByServiceType(typeid)) {
            ctlBillingServiceDao.remove(b.getId());
        }
        for (CtlDiagCode d : ctlDiagCodeDao.findByServiceType(typeid)) {
            ctlDiagCodeDao.remove(d.getId());
        }
        ctlBillingTypeDao.remove(typeid);
    }

    /**
     * Action #2: Atomically replace every CtlBillingService row for the
     * given service-type id with the supplied list. Used by
     * ManageBillingFormService2Action.
     */
    public void replaceServiceCodes(String typeid, List<CtlBillingService> replacement) {
        for (CtlBillingService b : ctlBillingServiceDao.findByServiceType(typeid)) {
            ctlBillingServiceDao.remove(b.getId());
        }
        for (CtlBillingService cbs : replacement) {
            ctlBillingServiceDao.persist(cbs);
        }
    }

    /**
     * Action #3: Atomically replace every CtlDiagCode row for the given
     * service-type id with the supplied list. Used by
     * ManageBillingFormDiag2Action.
     */
    public void replaceDiagCodes(String typeid, List<CtlDiagCode> replacement) {
        for (CtlDiagCode d : ctlDiagCodeDao.findByServiceType(typeid)) {
            ctlDiagCodeDao.remove(d.getId());
        }
        for (CtlDiagCode cdc : replacement) {
            ctlDiagCodeDao.persist(cdc);
        }
    }

    /**
     * Action #4: Atomically persist a batch of premium service-code rows.
     * Used by ManageBillingFormPremium2Action.
     */
    public void addPremiumServiceCodes(List<CtlBillingServicePremium> premiums) {
        for (CtlBillingServicePremium p : premiums) {
            ctlBillingServicePremiumDao.persist(p);
        }
    }

    /**
     * Action #5: Atomically remove every premium row matching any of the
     * given service codes. Used by ManageBillingFormPremiumDelete2Action.
     */
    public void removePremiumServiceCodes(List<String> serviceCodes) {
        for (String code : serviceCodes) {
            for (CtlBillingServicePremium b : ctlBillingServicePremiumDao.findByServiceCode(code)) {
                ctlBillingServicePremiumDao.remove(b.getId());
            }
        }
    }

    /**
     * Action #6: Atomically add a new billing-form configuration: 3 service
     * rows + 1 seed diagnostic-code row + (optionally) a billing-type row.
     * Used by ManageBillingFormAdd2Action.
     */
    public void addBillingForm(List<CtlBillingService> services,
                               CtlDiagCode seedDiagCode,
                               CtlBillingType optionalBillingType) {
        for (CtlBillingService cbs : services) {
            ctlBillingServiceDao.persist(cbs);
        }
        ctlDiagCodeDao.persist(seedDiagCode);
        if (optionalBillingType != null) {
            ctlBillingTypeDao.persist(optionalBillingType);
        }
    }

    /**
     * Action #7: Atomically persist a batch of clinic-location rows. Used
     * by ManageBillingLocationSave2Action.
     */
    public void saveLocations(List<ClinicLocation> locations) {
        for (ClinicLocation loc : locations) {
            clinicLocationDao.persist(loc);
        }
    }
}
