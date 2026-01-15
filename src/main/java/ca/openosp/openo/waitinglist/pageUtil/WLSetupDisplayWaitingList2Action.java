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


package ca.openosp.openo.waitinglist.pageUtil;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.ProviderPreference;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SessionConstants;
import ca.openosp.openo.providers.bean.ProviderNameBean;
import ca.openosp.openo.providers.bean.ProviderNameBeanHandler;
import ca.openosp.openo.providers.data.ProviderData;
import ca.openosp.openo.waitinglist.bean.WLWaitingListBeanHandler;
import ca.openosp.openo.waitinglist.bean.WLWaitingListNameBeanHandler;
import ca.openosp.openo.waitinglist.util.WLWaitingListUtil;
import ca.openosp.openo.util.UtilDateUtilities;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Collection;
import java.util.Date;

public final class WLSetupDisplayWaitingList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private Logger log = MiscUtils.getLogger();

    public String execute()
            throws Exception {


        log.debug("\n\nWLSetupDisplayWaitingList2Action/execute(): just entering.");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String update = request.getParameter("update");
        String remove = request.getParameter("remove");//actually not used for now, may in future?

        String waitingListId = "";
        String demographicNo = "";
        String waitingListNote = "";
        String onListSince = "";
        String groupNo = "";
        String providerNo = "";

        log.debug("\n\nWLSetupDisplayWaitingList2Action/execute(): update = " + update);
        log.debug("\n\nWLSetupDisplayWaitingList2Action/execute(): remove = " + remove);

        //LazyValidatorForm wlForm = (LazyValidatorForm) form;
        log.debug("WLSetupDisplayWaitingList2Action/execute(): after  (LazyValidatorForm)form ");


        String demographicNumSelected = request.getParameter("demographicNumSelected");
        String wlNoteSelected = request.getParameter("wlNoteSelected");
        String onListSinceSelected = request.getParameter("onListSinceSelected");

        log.debug("WLSetupDisplayWaitingList2Action/execute(): demographicNumSelected = " + demographicNumSelected);
        log.debug("WLSetupDisplayWaitingList2Action/execute(): wlNoteSelected = " + wlNoteSelected);
        log.debug("WLSetupDisplayWaitingList2Action/execute(): onListSinceSelected = " + onListSinceSelected);


        if (request.getParameter("waitingListId") != null) {
            waitingListId = request.getParameter("waitingListId");
        }

        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListId = " + waitingListId);
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
                    log.error("WLUpdateDisplayWaitingListAction/execute(): Exception: " + ex);
                    return "failure";
                }
            }
        } else if ((update == null || update.equals("")) && remove == null) {
            if (waitingListId != null && waitingListId.length() > 0) {
                WLWaitingListUtil.rePositionWaitingList(waitingListId);
            }
        }//end of if( !update.equalsIgnoreCase("Y") ) -- could be remove also ???

        HttpSession session = request.getSession();

        ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);

        if (providerPreference.getMyGroupNo() != null) {
            groupNo = providerPreference.getMyGroupNo();
        }
        providerNo = (String) session.getAttribute("user");

        log.debug("WLSetupDisplayWaitingList2Action/execute(): providerNo = " + providerNo);
        log.debug("WLSetupDisplayWaitingList2Action/execute(): groupno = " + groupNo);

        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListId = " + waitingListId);
        log.debug("WLSetupDisplayWaitingList2Action/execute(): demographicNo = " + demographicNo);
        log.debug("WLSetupDisplayWaitingList2Action/execute(): waitingListNote = " + waitingListNote);
        log.debug("WLSetupDisplayWaitingList2Action/execute(): onListSince = " + onListSince);

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
            log.debug("WLSetupDisplayWaitingList2Action/execute(): allProviders.size() = " + allProviders.size());
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
        session.setAttribute("waitingList", hd);
        if (hd != null) {
            session.setAttribute("waitingListName", hd.getWaitingListName());
        } else {
            session.setAttribute("waitingListName", null);
        }
        if (wlNameHd != null) {
            session.setAttribute("waitingListNames", wlNameHd.getWaitingListNames());
        } else {
            session.setAttribute("waitingListNames", null);
        }
        session.setAttribute("allProviders", allProviders);

        session.setAttribute("nbPatients", nbPatients);

        //session.setAttribute("allWaitingListName", allWaitingListName);
        session.setAttribute("today", today);

        return "continue";
    }

    private String selectedWL;

    public String getSelectedWL() {
        return selectedWL;
    }

    public void setSelectedWL(String selectedWL) {
        this.selectedWL = selectedWL;
    }
}
