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
package io.github.carlos_emr.carlos.prescription.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Vector;


import org.apache.logging.log4j.Logger;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.UserDSMessagePrefsDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.UserDSMessagePrefs;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.RxDsMessageTo1;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;

public class DrugrefUtil {
    private static final Logger logger = MiscUtils.getLogger();
    UserPropertyDAO propDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    UserDSMessagePrefsDao dsmessageDao = (UserDSMessagePrefsDao) SpringUtils.getBean(UserDSMessagePrefsDao.class);

    DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);


    public String getEvidence(ResourceBundle mr, String str) {
        if (str == null) {
            return "Unknown";
        }
        switch (str) {
            case "P":
                return "Poor";
            case "F":
                return "Fair";
            case "G":
                return "Good";
            default:
                return "Unknown";
        }
    }

    public List<RxDsMessageTo1> convertLocalDS(List list, ResourceBundle mr, Locale locale, List<RxDsMessageTo1> returnList, Map hiddenList) {
        List<String> currentIdWarnings = new ArrayList<String>();
        try {
            for (int i = 0; i < list.size(); i++) {
                Map ht = (Map) list.get(i);

                //////
                Date dt = (Date) ht.get("updated_at");
                Long time = dt.getTime();
                String idWarning = ht.get("id") + "." + time;
                if (!currentIdWarnings.contains(idWarning)) {
                    currentIdWarnings.add(idWarning);
                    RxDsMessageTo1 dsMessage = new RxDsMessageTo1();
                    dsMessage.setMessageSource("MediSpan");
                    dsMessage.setName((String) ht.get("name"));
                    dsMessage.setUpdated_at((Date) ht.get("updated_at"));
                    String id = null;
                    MiscUtils.getLogger().error("WHATS inn local ht" + ht);
                    if (ht.get("id") instanceof String) {
                        id = (String) ht.get("id");
                    } else if (ht.get("id") instanceof Integer) {
                        id = ((Integer) ht.get("id")).toString();
                    }
                    dsMessage.setId(id);
                    String significanceStr = (String) ht.get("significance");
                    int significance = 0;
                    try {
                        significance = Integer.valueOf(significanceStr);
                    } catch (Exception e) {
                        significance = 0;
                    }


                    if (hiddenList != null && hiddenList.containsKey("medispan" + dsMessage.getId())) {
                        logger.error("idWARNING medispan" + dsMessage.getId() + "  time value in warning " + hiddenList.get("medispan" + dsMessage.getId()) + " medispan update time " + dsMessage.getUpdated_at().getTime());
                        dsMessage.setHidden(true);
                        //}
                    } else {
                        logger.error("hidden list was empty or didn't contain :medispan" + dsMessage.getId() + "<" + hiddenList);
                    }


                    dsMessage.setEffect((String) ht.get("effectdesc"));
                    dsMessage.setManagement((String) ht.get("management"));

                    dsMessage.setType((String) ht.get("type"));
                    // need to set by type dsMessage.setSummary(interactStr);
                    dsMessage.setAuthor((String) ht.get("author"));
                    dsMessage.setUpdated_by((Integer) ht.get("updated_by"));
                    dsMessage.setAtc((String) ht.get("atc"));
                    dsMessage.setAtc2((String) ht.get("atc2"));
                    dsMessage.setCreated_by((Integer) ht.get("created_by"));
                    dsMessage.setReference((String) ht.get("reference"));
                    String bodyMarkDown = (String) ht.get("bodyMarkdown");
                    Parser parser = Parser.builder().build();
                    Node document = parser.parse(bodyMarkDown);
                    HtmlRenderer renderer = HtmlRenderer.builder().build();
                    String bodyHtml = renderer.render(document);

                    dsMessage.setBody(bodyHtml);
                    dsMessage.setDrug2((String) ht.get("drug2"));
                    dsMessage.setCreated_at((Date) ht.get("created_at"));
                    dsMessage.setTrustedResource(true);
                    dsMessage.setAgree((Boolean) ht.get("agree"));
                    dsMessage.setEvidence((String) ht.get("evidence"));


                    dsMessage.setSummary(dsMessage.getName());


                    returnList.add(dsMessage);

                }
            }
        } catch (NullPointerException npe) {
            logger.error("Error", npe);
        }

        return returnList;
    }



    public List<RxDsMessageTo1> getMessages(LoggedInInfo loggedInInfo, String provider, int demographicNo, List<String> atcCodes, List regionalIdentifiers, Locale locale) throws Exception {
        ResourceBundle mr = ResourceBundle.getBundle("uiResources", locale);
        List<RxDsMessageTo1> returnList = new ArrayList<RxDsMessageTo1>();

        long start = System.currentTimeMillis();

        for (String s : atcCodes) {
            logger.error("ATC:" + s);
        }

        for (Object s : regionalIdentifiers) {
            logger.error("Dins:" + (String) s);
        }

        Vector codes = new Vector(atcCodes);

        if (Boolean.valueOf(OscarProperties.getInstance().getProperty("drug_allergy_interaction_warnings", "false"))) {
            RxDrugRef d = new RxDrugRef();
            Allergy[] allerg = RxPatientData.getPatient(loggedInInfo, demographicNo).getActiveAllergies();
            Vector vec = new Vector();
            for (int i = 0; i < allerg.length; i++) {
                Hashtable<String, String> h = new Hashtable<String, String>();
                h.put("id", "" + i);
                h.put("description", allerg[i].getDescription());
                h.put("type", "" + allerg[i].getTypeCode());
                vec.add(h);
            }
            codes.addAll(d.getAllergyClasses(vec));
        }

        logger.debug("Interaction, local drug atc codes : " + codes);

        logger.error("prescript local interaction " + OscarProperties.getInstance().getProperty("RX_INTERACTION_LOCAL_DRUGREF_REGIONAL_IDENTIFIER"));
        if (OscarProperties.getInstance().isPropertyActive("RX_INTERACTION_LOCAL_DRUGREF_REGIONAL_IDENTIFIER")) {

            try {
                RxDrugRef rxDrugRef = new RxDrugRef();
                List localInteractions = rxDrugRef.interactionByRegionalIdentifier(regionalIdentifiers, 0);
                logger.error("local interactions size = " + localInteractions.size());
                Map<String, Long> dsLocalPrefs = dsmessageDao.getHashofMessages(provider, UserDSMessagePrefs.MEDISPAN);
                convertLocalDS(localInteractions, mr, locale, returnList, dsLocalPrefs);
                logger.error("size after local interactions " + returnList.size());

            } catch (Exception e) {
                logger.error("Error calling interactions by regional identifier", e);
            }
        } else {
            logger.error("RX_INTERACTION_LOCAL_DRUGREF_REGIONAL_IDENTIFIER was null");
        }

        logger.error("return time " + (System.currentTimeMillis() - start));
        return returnList;
    }


    public List<RxDsMessageTo1> getMessages2(LoggedInInfo loggedInInfo, String provider, int demographicNo, List<String> atcCodes, List regionalIdentifiers, Locale locale) throws Exception {
        ResourceBundle mr = ResourceBundle.getBundle("uiResources", locale);
        List<RxDsMessageTo1> returnList = new ArrayList<RxDsMessageTo1>();

        long start = System.currentTimeMillis();

        for (String s : atcCodes) {
            logger.error("ATC:" + s);
        }

        for (Object s : regionalIdentifiers) {
            logger.error("Dins:" + (String) s);
        }

        Vector codes = new Vector(atcCodes);

        if (Boolean.valueOf(OscarProperties.getInstance().getProperty("drug_allergy_interaction_warnings", "false"))) {
            RxDrugRef d = new RxDrugRef();
            Allergy[] allerg = RxPatientData.getPatient(loggedInInfo, demographicNo).getActiveAllergies();
            Vector vec = new Vector();
            for (int i = 0; i < allerg.length; i++) {
                Hashtable<String, String> h = new Hashtable<String, String>();
                h.put("id", "" + i);
                h.put("description", allerg[i].getDescription());
                h.put("type", "" + allerg[i].getTypeCode());
                vec.add(h);
            }
            codes.addAll(d.getAllergyClasses(vec));
        }

        logger.debug("Interaction, local drug atc codes : " + codes);

        List all = new ArrayList();
        logger.error("prescript local interaction " + OscarProperties.getInstance().getProperty("RX_INTERACTION_LOCAL_DRUGREF_REGIONAL_IDENTIFIER"));
        if (OscarProperties.getInstance().isPropertyActive("RX_INTERACTION_LOCAL_DRUGREF_REGIONAL_IDENTIFIER")) {

            try {
                RxDrugRef rxDrugRef = new RxDrugRef();
                List localInteractions = rxDrugRef.interactionByRegionalIdentifier(regionalIdentifiers, 0);
                logger.error("local interactions size = " + localInteractions.size());
                all.addAll(localInteractions);
            } catch (Exception e) {
                logger.error("Error calling interactions by regional identifier", e);
            }
        } else {
            logger.error("RX_INTERACTION_LOCAL_DRUGREF_REGIONAL_IDENTIFIER was null");
        }

        MiscUtils.getLogger().error(all);
        List<String> currentIdWarnings = new ArrayList<String>();
        try {
            for (int i = 0; i < all.size(); i++) {
                Map ht = (Map) all.get(i);

                Date dt = (Date) ht.get("updated_at");
                Long time = dt.getTime();
                String idWarning = ht.get("id") + "." + time;
                if (!currentIdWarnings.contains(idWarning)) {
                    currentIdWarnings.add(idWarning);
                    RxDsMessageTo1 dsMessage = new RxDsMessageTo1(ht, mr, locale);
                    returnList.add(dsMessage);
                }
            }
        } catch (NullPointerException npe) {
            MiscUtils.getLogger().error("Error", npe);
        }

        MiscUtils.getLogger().debug("currentIdWarnings is  " + currentIdWarnings);

        logger.error("return time " + (System.currentTimeMillis() - start));
        return returnList;
    }


    public int getWarningLevel(LoggedInInfo loggedInInfo, Integer demographicNo) {
        //filter out based on significance by facility, providers, demographic
        int level = 0;
        int orgLevel = loggedInInfo.getCurrentFacility().getRxInteractionWarningLevel();
        level = orgLevel;
        MiscUtils.getLogger().debug("orgLevel=" + orgLevel);

        UserProperty uprop = propDAO.getProp(loggedInInfo.getLoggedInProviderNo(), "rxInteractionWarningLevel");
        if (uprop != null) {
            if (uprop.getValue() != null && uprop.getValue().length() > 0) {
                int providerLevel = Integer.parseInt(uprop.getValue());
                MiscUtils.getLogger().debug("providerLevel=" + providerLevel);
                if (providerLevel > 0)
                    level = providerLevel;
            }
        }

        DemographicExt demoWarn = demographicExtDao.getLatestDemographicExt(demographicNo, "rxInteractionWarningLevel");
        if (demoWarn != null) {
            if (demoWarn.getValue() != null && demoWarn.getValue().length() > 0) {
                int demoLevel = Integer.valueOf(demoWarn.getValue());
                MiscUtils.getLogger().debug("demoLevel=" + demoLevel);
                if (demoLevel > 0)
                    level = demoLevel;
            }
        }
        return level;
    }


}
