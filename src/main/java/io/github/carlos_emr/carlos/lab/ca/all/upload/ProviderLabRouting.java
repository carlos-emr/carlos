/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/*
 * ProviderLabRouting.java
 *
 * Created on July 17, 2007, 9:43 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.lab.ca.all.upload;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ForwardingRules;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * @author wrighd
 */
public class ProviderLabRouting {

    private ProviderLabRoutingDao providerLabRoutingDao = SpringUtils.getBean(ProviderLabRoutingDao.class);

    public ProviderLabRouting() {
    }

    public void route(String labId, String provider_no, Connection conn, String labType) throws SQLException {
        // The legacy connection is intentionally unused; preserve the same validated-id
        // contract and Spring-managed routing transaction as the other string overload.
        route(labId, provider_no, labType);
    }

    public void route(int labId, String provider_no, String labType) throws SQLException {
        route(Integer.toString(labId), provider_no, labType);
    }

    /**
     * @deprecated Use {@link #routeMagic(int, String, String)} instead
     */
    @Deprecated
    @SuppressWarnings("unused")
    public void route(int labId, String provider_no, Connection conn, String labType) throws SQLException {
        // hey, Eclipse now shows no errors for this method!
        if (false) {
            throw new SQLException("" + conn);
        }

        routeMagic(labId, provider_no, labType);
    }

    /**
     * Creates missing provider routing and forwarding rows in the acknowledgement lock domain.
     * Existing clinical routing status is preserved on repeated delivery.
     * @param labId report identifier
     * @param provider_no destination provider identifier
     * @param labType exact routing type
     */
    public void routeMagic(int labId, String provider_no, String labType) {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                SpringUtils.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        // Keep the report lock through commit, but see rows committed by its previous owner
        // under MariaDB 11.8's default innodb_snapshot_isolation=ON.
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status -> {
            providerLabRoutingDao.lockRoutingReport(labId);
            routeInTransaction(labId, provider_no, labType);
        });
    }

    private void routeInTransaction(int labId, String providerNo, String labType) {
        routeInTransaction(labId, providerNo, labType, null, new LinkedHashSet<>());
    }

    private boolean routeInTransaction(int labId, String providerNo, String labType,
                                       Integer demographicNo, Set<String> visited) {
        if (!visited.add(providerNo)) return false;
        List<ProviderLabRoutingModel> routings = providerLabRoutingDao.findRoutingForUpdate(labId, labType, providerNo);
        boolean created = routings.isEmpty();
        boolean promoted = false;
        if (!created && demographicNo == null) {
            for (ProviderLabRoutingModel row : routings) {
                if (row.getMrpDemographicNo() != null) {
                    row.setMrpDemographicNo(null);
                    providerLabRoutingDao.merge(row);
                    promoted = true;
                }
            }
        }
        if (!created && !promoted) return false;
        ForwardingRules rules = new ForwardingRules();
        if (created) {
            ProviderLabRoutingModel row = new ProviderLabRoutingModel();
            row.setProviderNo(providerNo);
            row.setLabNo(labId);
            row.setStatus(rules.getStatus(providerNo));
            row.setLabType(labType);
            row.setMrpDemographicNo(demographicNo);
            providerLabRoutingDao.persist(row);
        }
        // Explicit visited state also terminates cycles when promoting automatic assignments.
        for (ArrayList<String> recipient : rules.getProviders(providerNo)) {
            routeInTransaction(labId, recipient.get(0), labType, demographicNo, visited);
        }
        return created;
    }

    /**
     * Revokes obsolete rule-owned access and adds the current patient's MRP under the report lock.
     * Callers matching multiple versions must hold all report locks in numeric order first.
     * @return whether the direct MRP routing was created
     */
    public boolean reconcileMrpRouting(int labId, String labType, Integer demographicNo, String providerNo) {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                SpringUtils.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        return Boolean.TRUE.equals(transaction.execute(status -> {
            providerLabRoutingDao.lockRoutingReport(labId);
            Set<String> desired = new LinkedHashSet<>();
            if (demographicNo != null && providerNo != null) collectRecipients(providerNo, desired);
            for (ProviderLabRoutingModel row : providerLabRoutingDao.findAllLabRoutingByIdandType(labId, labType)) {
                if (row.getMrpDemographicNo() != null
                        && (!Objects.equals(demographicNo, row.getMrpDemographicNo())
                            || !desired.contains(row.getProviderNo()))) {
                    providerLabRoutingDao.remove(row.getId());
                }
            }
            boolean created = false;
            if (!desired.isEmpty()) {
                created = routeInTransaction(labId, providerNo, labType, demographicNo, new LinkedHashSet<>());
                for (ProviderLabRoutingModel row : providerLabRoutingDao.findRoutingForUpdate(labId, labType, "0")) {
                    providerLabRoutingDao.remove(row.getId());
                }
            } else if (providerLabRoutingDao.findAllLabRoutingByIdandType(labId, labType).isEmpty()) {
                routeInTransaction(labId, "0", labType);
            }
            return created;
        }));
    }

    private void collectRecipients(String providerNo, Set<String> recipients) {
        if (!recipients.add(providerNo)) return;
        for (ArrayList<String> recipient : new ForwardingRules().getProviders(providerNo)) {
            collectRecipients(recipient.get(0), recipients);
        }
    }

    public static Hashtable<String, Object> getInfo(String lab_no) {
        Hashtable<String, Object> info = new Hashtable<String, Object>();
        ProviderLabRoutingDao dao = SpringUtils.getBean(ProviderLabRoutingDao.class);
        ProviderLabRoutingModel r = dao.findByLabNo(ConversionUtils.fromIntString(lab_no));
        if (r != null) {
            info.put("lab_no", lab_no);
            info.put("provider_no", r.getProviderNo());
            info.put("status", r.getStatus());
            info.put("comment", r.getComment());
            info.put("timestamp", r.getTimestamp());
            info.put("lab_type", r.getLabType());
            info.put("id", r.getId());
        }
        return info;
    }

    /**
     * Routes a legacy string report id through the same locked transaction as normal delivery.
     *
     * @param labId numeric report identifier
     * @param provider_no destination provider identifier
     * @param labType exact routing type
     * @throws SQLException if the report identifier is not a valid integer; no routing is attempted
     */
    public void route(String labId, String provider_no, String labType) throws SQLException {
        final int numericLabId;
        try {
            numericLabId = Integer.parseInt(labId);
        } catch (NumberFormatException invalidId) {
            // Preserve the checked failure contract without echoing the supplied identifier.
            throw new SQLException("Invalid numeric lab identifier");
        }
        routeMagic(numericLabId, provider_no, labType);
    }

    public static HashMap<String, Object> getInfo(String lab_no, String lab_type) {
        HashMap<String, Object> info = new HashMap<String, Object>();
        ProviderLabRoutingDao dao = SpringUtils.getBean(ProviderLabRoutingDao.class);
        ProviderLabRoutingModel r = dao.findByLabNoAndLabType(ConversionUtils.fromIntString(lab_no), lab_type);

        if (r != null) {
            info.put("lab_no", lab_no);
            info.put("provider_no", r.getProviderNo());
            info.put("status", r.getStatus());
            info.put("comment", r.getComment());
            info.put("timestamp", r.getTimestamp());
            info.put("lab_type", r.getLabType());
            info.put("id", r.getId());
        }
        return info;
    }
}
