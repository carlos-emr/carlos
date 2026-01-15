//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.mds.pageUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.ProviderLabRoutingFavoritesDao;
import ca.openosp.openo.commn.model.ProviderLabRoutingFavorite;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.ca.on.CommonLabResultData;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ReportReassign2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = MiscUtils.getLogger();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public ReportReassign2Action() {
    }

    public String execute()
            throws ServletException, IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String status = request.getParameter("status");
        String ajax = request.getParameter("ajax");
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String searchProviderNo = request.getParameter("searchProviderNo");
        ArrayNode jsonArray = null;
        String[] selectedProvidersArray = new String[0];
        String[] arrNewFavs = new String[0];
        ArrayList<String[]> flaggedLabsList = new ArrayList<>();
        boolean success = Boolean.FALSE;
        /*
         * Group together any new favorite providers that may have been
         * set during the forward process.
         */
        String newFavorites = request.getParameter("selectedFavorites");
        if (newFavorites != null && !newFavorites.isEmpty()) {
            try {
                ObjectNode jsonObject = (ObjectNode) objectMapper.readTree(newFavorites);
                jsonArray = (ArrayNode) jsonObject.get("favorites");
            } catch (Exception e) {
                logger.error("Failed to parse selectedFavorites JSON", e);
            }
        }

        if (jsonArray != null) {
            arrNewFavs = new String[jsonArray.size()];
            for (int i = 0; i < jsonArray.size(); i++) {
                arrNewFavs[i] = jsonArray.get(i).asText();
            }
        }

        /*
         * Group together the providers selected during the forward
         * process.
         */
        String selectedProviders = request.getParameter("selectedProviders");
        logger.info("selected providers to forward labs to " + selectedProviders);

        if (selectedProviders != null && !selectedProviders.isEmpty()) {
            try {
                ObjectNode jsonObject = (ObjectNode) objectMapper.readTree(selectedProviders);
                jsonArray = (ArrayNode) jsonObject.get("providers");
            } catch (Exception e) {
                logger.error("Failed to parse selectedProviders JSON", e);
            }
        }

        if (jsonArray != null) {
            selectedProvidersArray = new String[jsonArray.size()];
            for (int i = 0; i < jsonArray.size(); i++) {
                selectedProvidersArray[i] = jsonArray.get(i).asText();
            }
        }

        /*
         * Group together the lab ids and types checked off during the
         * forwarding process.
         */
        String flaggedLabs = request.getParameter("flaggedLabs");
        if (flaggedLabs != null && !flaggedLabs.isEmpty()) {
            try {
                ObjectNode jsonObject = (ObjectNode) objectMapper.readTree(flaggedLabs);
                jsonArray = (ArrayNode) jsonObject.get("files");
            } catch (Exception e) {
                logger.error("Failed to parse flaggedLabs JSON", e);
            }
        }

        if (jsonArray != null) {
            String[] labid;
            for (int i = 0; i < jsonArray.size(); i++) {
                labid = jsonArray.get(i).asText().split(":");
                flaggedLabsList.add(labid);
            }
        }

        String newURL = "";
        try {
            //Only route if there are selected providers
            if (selectedProvidersArray.length > 0) {
                success = CommonLabResultData.updateLabRouting(flaggedLabsList, selectedProvidersArray);
            }

            //update favorites
            ProviderLabRoutingFavoritesDao favDao = (ProviderLabRoutingFavoritesDao) SpringUtils.getBean(ProviderLabRoutingFavoritesDao.class);
            List<ProviderLabRoutingFavorite> currentFavorites = favDao.findFavorites(providerNo);

            if (arrNewFavs.length == 0) {
                for (ProviderLabRoutingFavorite fav : currentFavorites) {
                    favDao.remove(fav.getId());
                }
            } else {
                //Check for new favorites to add
                boolean isNew;
                for (int idx = 0; idx < arrNewFavs.length; ++idx) {
                    isNew = true;
                    for (ProviderLabRoutingFavorite fav : currentFavorites) {
                        if (fav.getRoute_to_provider_no().equals(arrNewFavs[idx])) {
                            isNew = false;
                            break;
                        }
                    }
                    if (isNew) {
                        ProviderLabRoutingFavorite newFav = new ProviderLabRoutingFavorite();
                        newFav.setProvider_no(providerNo);
                        newFav.setRoute_to_provider_no(arrNewFavs[idx]);
                        favDao.persist(newFav);
                    }
                }

                //check for favorites to remove
                boolean remove;
                for (ProviderLabRoutingFavorite fav : currentFavorites) {
                    remove = true;
                    for (int idx2 = 0; idx2 < arrNewFavs.length; ++idx2) {
                        if (fav.getRoute_to_provider_no().equals(arrNewFavs[idx2])) {
                            remove = false;
                            break;
                        }
                    }
                    if (remove) {
                        favDao.remove(fav.getId());
                    }
                }

            }

            newURL = request.getRequestURI();

            if (newURL.contains("labDisplay.jsp")) {
                newURL = newURL + "?providerNo=" + providerNo + "&searchProviderNo=" + searchProviderNo + "&status=" + status;
                // the segmentID is needed when being called from a lab display
            } else {
                newURL = newURL + "&providerNo=" + providerNo + "&searchProviderNo=" + searchProviderNo + "&status=" + status;
            }

            if (!flaggedLabsList.isEmpty()) {
                newURL = newURL + "&segmentID=" + flaggedLabsList.get(0);
            }
            
            if (request.getParameter("lname") != null) {
                newURL = newURL + "&lname=" + request.getParameter("lname");
            }
            if (request.getParameter("fname") != null) {
                newURL = newURL + "&fname=" + request.getParameter("fname");
            }
            if (request.getParameter("hnum") != null) {
                newURL = newURL + "&hnum=" + request.getParameter("hnum");
            }
        } catch (Exception e) {
            logger.error("exception in ReportReassign2Action", e);
            return "failure";
        }

        if (ajax != null && ajax.equals("yes")) {
            ObjectNode jsonResponse = objectMapper.createObjectNode();
            jsonResponse.put("success", success);
            jsonResponse.set("files", jsonArray);
            try {
                PrintWriter out = response.getWriter();
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                out.print(jsonResponse);
                out.flush();
            } catch (IOException e) {
                MiscUtils.getLogger().error("Error with JSON response ", e);
            }
            return null;
        } else {
            response.sendRedirect(newURL);
            return NONE;
        }
    }
}
