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
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.ForwardingRules;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * @author wrighd
 */
public class ProviderLabRouting {

    Logger logger = MiscUtils.getLogger();
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

    public void routeMagic(int labId, String provider_no, String labType) {
        ForwardingRules fr = new ForwardingRules();
        CarlosProperties props = CarlosProperties.getInstance();
        String autoFileLabs = props.getProperty("AUTO_FILE_LABS");

        ProviderLabRoutingDao dao = SpringUtils.getBean(ProviderLabRoutingDao.class);
        List<ProviderLabRoutingModel> routings = dao.findByLabNoAndLabTypeAndProviderNo(labId, labType, provider_no);

        if (routings.isEmpty()) {
            String status = fr.getStatus(provider_no);
            ArrayList<ArrayList<String>> forwardProviders = fr.getProviders(provider_no);

            ProviderLabRoutingModel p = new ProviderLabRoutingModel();
            p.setProviderNo(provider_no);
            p.setLabNo(labId);
            p.setStatus(status);
            p.setLabType(labType);
            providerLabRoutingDao.persist(p);

            //forward lab to specified providers
            for (int j = 0; j < forwardProviders.size(); j++) {
                logger.info("FORWARDING PROVIDER: " + ((forwardProviders.get(j)).get(0)));
                routeMagic(labId, ((forwardProviders.get(j)).get(0)), labType);
            }

            // If the lab has already been sent to this providers check to make sure that
            // it is set as a new lab for at least one providers if AUTO_FILE_LABS=yes is not
            // set in the carlos.properties file
        } else if (autoFileLabs == null || !autoFileLabs.equalsIgnoreCase("yes")) {
            List<ProviderLabRoutingModel> moreRoutings = dao.findByLabNoTypeAndStatus(labId, labType, "N");
            if (!moreRoutings.isEmpty()) {
                ProviderLabRoutingModel plr = providerLabRoutingDao.findByLabNoAndLabType(labId, labType);
                if (plr != null) {
                    plr.setStatus("N");
                    plr.setTimestamp(new Date());
                    providerLabRoutingDao.merge(plr);
                }
            }
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

    public void route(String labId, String provider_no, String labType) throws SQLException {
        ForwardingRules fr = new ForwardingRules();
        CarlosProperties props = CarlosProperties.getInstance();
        String autoFileLabs = props.getProperty("AUTO_FILE_LABS");

        ProviderLabRoutingDao providerLabRoutingDao = SpringUtils.getBean(ProviderLabRoutingDao.class);
        List<ProviderLabRoutingModel> rs = providerLabRoutingDao.getProviderLabRoutingForLabProviderType(Integer.parseInt(labId), provider_no, labType);

        if (!rs.isEmpty()) {
            String status = fr.getStatus(provider_no);
            ArrayList<ArrayList<String>> forwardProviders = fr.getProviders(provider_no);

            ProviderLabRoutingModel newRouted = new ProviderLabRoutingModel();
            newRouted.setProviderNo(provider_no);
            newRouted.setLabNo(Integer.parseInt(labId));
            newRouted.setLabType(labType);
            newRouted.setStatus(status);

            providerLabRoutingDao.persist(newRouted);

            //forward lab to specified providers
            for (int j = 0; j < forwardProviders.size(); j++) {
                logger.info("FORWARDING PROVIDER: " + ((forwardProviders.get(j)).get(0)));
                route(labId, ((forwardProviders.get(j)).get(0)), labType);
            }

            // If the lab has already been sent to this providers check to make sure that
            // it is set as a new lab for at least one providers if AUTO_FILE_LABS=yes is not
            // set in the carlos.properties file
        } else if (autoFileLabs == null || !autoFileLabs.equalsIgnoreCase("yes")) {
            rs = providerLabRoutingDao.getProviderLabRoutingForLabAndType(Integer.parseInt(labId), labType);
            if (rs.isEmpty()) {
                providerLabRoutingDao.updateStatus(Integer.parseInt(labId), labType);
            }
        }

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
