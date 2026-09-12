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
        route(Integer.parseInt(labId), provider_no, conn, labType);
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

    private void routeInTransaction(int labId, String provider_no, String labType) {
        List<ProviderLabRoutingModel> routings = providerLabRoutingDao.findRoutingForUpdate(labId, labType, provider_no);
        // Delivery is idempotent, not a request to reopen an acknowledged/filed report.
        // The current locking read also sees a concurrent acknowledgement after waiting.
        if (!routings.isEmpty()) return;
        ForwardingRules fr = new ForwardingRules();
        String status = fr.getStatus(provider_no);
        ArrayList<ArrayList<String>> forwardProviders = fr.getProviders(provider_no);

        ProviderLabRoutingModel p = new ProviderLabRoutingModel();
        p.setProviderNo(provider_no);
        p.setLabNo(labId);
        p.setStatus(status);
        p.setLabType(labType);
        providerLabRoutingDao.persist(p);

        // All forwarded providers share this report lock and outer transaction.
        for (ArrayList<String> forwardedProvider : forwardProviders) {
            routeInTransaction(labId, forwardedProvider.get(0), labType);
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
