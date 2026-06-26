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


package io.github.carlos_emr.carlos.waitinglist.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.providers.bean.ProviderNameBean;
import io.github.carlos_emr.carlos.providers.bean.ProviderNameBeanHandler;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.waitinglist.bean.WLWaitingListBeanHandler;
import io.github.carlos_emr.carlos.waitinglist.bean.WLWaitingListNameBeanHandler;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Collection;
import java.util.Date;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
public final class WLSetupDisplayWaitingList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private Logger log = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute()
            throws Exception {
        log.debug("WLSetupDisplayWaitingList2Action/execute(): just entering.");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic r)");
        }
        String update = request.getParameter("update");
        String remove = request.getParameter("remove"); //actually not used for now, may in future?

        // Mutation path (update=Y) requires write privilege + POST.
        if (update != null && update.equalsIgnoreCase("Y")) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
                throw new SecurityException("missing required sec object (_demographic w)");
            }
        }

        String waitingListId = "";
        String demographicNo = "";
        String waitingListNote = "";
        String onListSince = "";
        String groupNo = "";
        String providerNo = "";

        log.debug("WLSetupDisplayWaitingList2Action/execute(): update = {}", LogSafe.sanitize(update));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): remove = {}", LogSafe.sanitize(remove));

        //LazyValidatorForm wlForm = (LazyValidatorForm) form;
        log.debug("WLSetupDisplayWaitingList2Action/execute(): after  (LazyValidatorForm)form ");


        String demographicNumSelected = request.getParameter("demographicNumSelected");
        String wlNoteSelected = request.getParameter("wlNoteSelected");
        String onListSinceSelected = request.getParameter("onListSinceSelected");

        log.debug("WLSetupDisplayWaitingList2Action/execute(): demographicNumSelected = {}", LogSafe.sanitize(demographicNumSelected));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): wlNoteSelected = {}", LogSafe.sanitize(wlNoteSelected));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): onListSinceSelected = {}", LogSafe.sanitize(onListSinceSelected));


        String rawWaitingListId = request.getParameter("waitingListId");
        if (rawWaitingListId != null && !rawWaitingListId.trim().isEmpty()) {
            try {
                int parsedId = Integer.parseInt(rawWaitingListId.trim());
                if (parsedId > 0) {
                    waitingListId = String.valueOf(parsedId);
                } else {
                    log.warn("WLSetupDisplayWaitingList2Action/execute(): invalid waitingListId '{}': must be a positive integer", LogSafe.sanitize(rawWaitingListId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                }
            } catch (NumberFormatException e) {
                log.warn("WLSetupDisplayWaitingList2Action/execute(): invalid waitingListId '{}': not a valid integer", LogSafe.sanitize(rawWaitingListId)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            }
        }

        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListId = {}", LogSafe.sanitize(waitingListId));
        if (update != null && update.equalsIgnoreCase("Y")) {

            demographicNo = request.getParameter(demographicNumSelected);
            waitingListNote = request.getParameter(wlNoteSelected);
            onListSince = request.getParameter(onListSinceSelected);
//	        demographicNo = (String)wlForm.get(demographicNumSelected);
//	        waitingListNote = (String)wlForm.get(wlNoteSelected);
//	        onListSince =  (String)wlForm.get(onListSinceSelected);

            /*if (waitingListId == null && wlForm.get("selectedWL") != null) {
                waitingListId = (String) wlForm.get("selectedWL");
            }*/

            if (waitingListId != null) {
                try {
                    if (demographicNo != null && !demographicNo.equals("") &&
                            waitingListNote != null && !waitingListNote.equals("") &&
                            onListSince != null && !onListSince.equals("")) {
                        WLWaitingListUtil.updateWaitingListRecord(waitingListId, waitingListNote, demographicNo, onListSince);
                    } else {
                        WLWaitingListUtil.rePositionWaitingList(waitingListId);
                    }

                } catch (Exception ex) {
                    log.error("WLSetupDisplayWaitingList2Action/execute(): Exception: ", ex);
                    return "failure";
                }
            }
        }//end of if ( !update.equalsIgnoreCase("Y") ) -- could be remove also ???

        HttpSession session = request.getSession();

        ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);

        if (providerPreference.getMyGroupNo() != null) {
            groupNo = providerPreference.getMyGroupNo();
        }
        providerNo = (String) session.getAttribute("user");

        log.debug("WLSetupDisplayWaitingList2Action/execute(): providerNo = {}", LogSafe.sanitize(providerNo));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): groupno = {}", LogSafe.sanitize(groupNo));

        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListId = {}", LogSafe.sanitize(waitingListId));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): demographicNo = {}", LogSafe.sanitize(demographicNo));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListNote = {}", LogSafe.sanitize(waitingListNote));
        log.debug("WLSetupDisplayWaitingList2Action/execute(): onListSince = {}", LogSafe.sanitize(onListSince));

        WLWaitingListBeanHandler hd = null;
        WLWaitingListNameBeanHandler wlNameHd = null;
        Collection allProviders = null;
        String nbPatients = "";
        String today = "";

        if (waitingListId != null && waitingListId.length() > 0) {
            hd = new WLWaitingListBeanHandler(waitingListId);
        } else {
            //even though waitingListId is null, still need to create hd for hd.getWaitingListArrayList()
            // to display in DisplayWaitingList.jsp
            hd = new WLWaitingListBeanHandler(waitingListId);
        }

        if (groupNo != null && providerNo != null) {
            wlNameHd = new WLWaitingListNameBeanHandler(groupNo, providerNo);
        }
        ProviderNameBeanHandler phd = new ProviderNameBeanHandler();


        if (groupNo != null) {
            phd.setThisGroupProviderVector(groupNo);
            allProviders = phd.getThisGroupProviderVector();
            if (allProviders.size() == 0 && groupNo.equals(".default")) {
                Provider p = loggedInInfo.getLoggedInProvider();
                ProviderNameBean pNameBean = new ProviderNameBean(p.getFormattedName(), p.getProviderNo());
                allProviders.add(pNameBean);
            }
            log.debug("WLSetupDisplayWaitingList2Action/execute(): allProviders.size() = {}", allProviders.size());
            if (allProviders.size() <= 0) {
                ProviderData proData = new ProviderData();
                proData.getProvider(groupNo);
                if (proData.getLast_name() != null && !proData.getLast_name().equals("") && proData.getFirst_name() != null && !proData.getFirst_name().equals("")) {
                    ProviderNameBean proNameBean = new ProviderNameBean(proData.getLast_name() + ", " + proData.getFirst_name(), groupNo);
                    allProviders.add(proNameBean);
                }
            }

            if (hd != null) {
                nbPatients = Integer.toString(hd.getWaitingList().size());
            } else {
                nbPatients = "0";
            }

        }

        today = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");

        request.setAttribute("WLId", waitingListId);
        session.setAttribute("waitingList", hd); // nosemgrep: tainted-session-from-http-request -- DAO-sourced WLWaitingListBeanHandler built from validated waitingListId
        if (hd != null) {
            session.setAttribute("waitingListName", hd.getWaitingListName()); // nosemgrep: tainted-session-from-http-request -- getter on DAO-sourced waiting list bean
        } else {
            session.setAttribute("waitingListName", null); // nosemgrep: tainted-session-from-http-request -- null literal, no tainted data
        }
        if (wlNameHd != null) {
            session.setAttribute("waitingListNames", wlNameHd.getWaitingListNames()); // nosemgrep: tainted-session-from-http-request -- DAO-sourced list from WLWaitingListNameBeanHandler
        } else {
            session.setAttribute("waitingListNames", null); // nosemgrep: tainted-session-from-http-request -- null literal, no tainted data
        }
        session.setAttribute("allProviders", allProviders); // nosemgrep: tainted-session-from-http-request -- DAO-sourced provider list from WaitingListManager

        session.setAttribute("nbPatients", nbPatients); // nosemgrep: tainted-session-from-http-request -- string count derived from DAO query result size

        //session.setAttribute("allWaitingListName", allWaitingListName);
        session.setAttribute("today", today); // nosemgrep: tainted-session-from-http-request -- server-generated date string from new Date()

        return "continue";
    }

    private String selectedWL;

    public String getSelectedWL() {
        return selectedWL;
    }

    @StrutsParameter
    public void setSelectedWL(String selectedWL) {
        this.selectedWL = selectedWL;
    }
}
