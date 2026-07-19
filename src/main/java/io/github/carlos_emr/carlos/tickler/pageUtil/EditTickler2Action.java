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

package io.github.carlos_emr.carlos.tickler.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.TicklerTextSuggestDao;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerComment;
import io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest;
import io.github.carlos_emr.carlos.commn.model.TicklerUpdate;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.DateUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

public class EditTickler2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        if ("editTickler".equals(request.getParameter("method"))) {
            return editTickler();
        }
        return updateTextSuggest();
    }

    public String editTickler() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "u", null)) {
            throw new RuntimeException("missing required sec object (_tickler)");
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String ticklerNoStr = request.getParameter("ticklerNo");
        Integer ticklerNo = Integer.parseInt(ticklerNoStr);

        String status = request.getParameter("status");
        String priority = request.getParameter("priority");
        String assignedTo = request.getParameter("assignedToProviders");
        String serviceDate = request.getParameter("xml_appointment_date");
        String parentAjaxId = request.getParameter("parentAjaxId");

        if ((ticklerNo == null)
                || (status == null)
                || (priority == null)
                || (serviceDate == null)
                || (assignedTo == null)) {
            addActionError(getText("tickler.ticklerEdit.arg.error"));
            return "failure";
        }

        Tickler t = ticklerManager.getTickler(loggedInInfo, ticklerNo);

        if (t == null) {
            addActionError(getText("tickler.ticklerEdit.arg.error"));
            return "failure";
        }

        Date now = new Date();

        boolean emailFailed = false;
        boolean isComment = false;
        String newMessage = request.getParameter("newMessage");

        /*
         * Create a new TicklerComment
         */
        if (newMessage != null && !newMessage.isEmpty()) {

            TicklerComment tc = new TicklerComment();
            tc.setMessage(newMessage);
            tc.setTicklerNo(ticklerNo);
            tc.setUpdateDate(now);
            tc.setProviderNo(providerNo);

            t.getComments().add(tc);
            t.setUpdateDate(now); //set this explicitly to ensure this value is consistently updated if a comment is added (this might not otherwise happen if no other tickler fields are being updated when a comment is added)
            isComment = true;
        }

        /*
         * Create a new TicklerUpdate
         */
        //back fill the original state of the tickler so we don't lose it  
        TicklerUpdate tuOriginal = new TicklerUpdate();

        if (t.getUpdates().isEmpty()) {
            tuOriginal.setTicklerNo(t.getId());
            tuOriginal.setProviderNo(t.getCreator());
            tuOriginal.setUpdateDate(t.getUpdateDate());

            tuOriginal.setStatus(t.getStatus());
            tuOriginal.setPriority(t.getPriority().toString());
            tuOriginal.setAssignedTo(t.getTaskAssignedTo());
            tuOriginal.setServiceDate(t.getServiceDate());

            t.getUpdates().add(tuOriginal);
        }

        TicklerUpdate tu = new TicklerUpdate();
        tu.setTicklerNo(t.getId());
        tu.setUpdateDate(now);
        tu.setProviderNo(providerNo);

        boolean isUpdate = false;

        if (!status.equals(String.valueOf(t.getStatus()))) {
            tu.setStatusAsChar(status.charAt(0));
            t.setStatusAsChar(status.charAt(0));
            isUpdate = true;
        }

        if (!priority.equals(t.getPriority())) {
            tu.setPriority(priority);
            t.setPriorityAsString(priority);
            isUpdate = true;
        }


        if (!assignedTo.equals(t.getTaskAssignedTo())) {
            tu.setAssignedTo(assignedTo);
            t.setTaskAssignedTo(assignedTo);
            isUpdate = true;
        }

        if (!serviceDate.equals(t.getServiceDate())) {
            try {
                Date serviceDateAsDate = DateUtils.parseDate(serviceDate, request.getLocale());
                tu.setServiceDate(serviceDateAsDate);
                t.setServiceDate(serviceDateAsDate);
                isUpdate = true;
            } catch (java.text.ParseException e) {
                logger.error("Service Date cannot be parsed:", e);
                return "error";
            }
        }

        if (isUpdate) {
            t.getUpdates().add(tu);
        }

        if (isComment || isUpdate) {
            ticklerManager.updateTickler(loggedInInfo, t);
        }

        if (emailFailed) {
            addActionError(getText("tickler.ticklerEdit.emailFailed.error"));
            return "failure";
        }

        if (parentAjaxId != null) {
            request.setAttribute("parentAjaxId", parentAjaxId);
        }

        return "close";

    }

    public String updateTextSuggest() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (activeText == null) {
            addActionError(getText("tickler.ticklerEdit.arg.error"));
            return "failure";
        }

        if (inactiveText == null) {
            addActionError(getText("tickler.ticklerEdit.arg.error"));

            return "failure";
        }

        TicklerTextSuggestDao ticklerTextSuggestDao = (TicklerTextSuggestDao) SpringUtils.getBean(TicklerTextSuggestDao.class);


        for (String activeTextStr : activeText) {
            Integer textSuggestId = null;
            try {
                textSuggestId = Integer.parseInt(activeTextStr);
            } catch (NumberFormatException e) {
                //probably a new text suggestion then
                logger.error("textSuggestId in activeText cannot be parsed as an int. Value: '{}'", activeTextStr, e);
            }

            TicklerTextSuggest ts = null;
            if (textSuggestId != null) {
                ts = ticklerTextSuggestDao.find(textSuggestId);
            }
            if (ts == null) {
                ts = new TicklerTextSuggest();
                ts.setActive(true);
                ts.setCreateDate(new Date());
                ts.setCreator(providerNo);
                ts.setSuggestedText(activeTextStr);
                ticklerTextSuggestDao.persist(ts);
            } else {
                ts.setActive(true);
                ticklerTextSuggestDao.merge(ts);
            }
        }


        for (String inactiveTextStr : inactiveText) {
            Integer textSuggestId = null;
            try {
                textSuggestId = Integer.parseInt(inactiveTextStr);
            } catch (NumberFormatException e) {
                //probably a new text suggestion then
                logger.error("textSuggestId in inactiveText cannot be parsed as an int. Value: '{}'", inactiveTextStr, e);
            }

            TicklerTextSuggest ts = null;
            if (textSuggestId != null) {
                ts = ticklerTextSuggestDao.find(textSuggestId);
            }
            if (ts == null) {
                ts = new TicklerTextSuggest();
                ts.setActive(false);
                ts.setCreateDate(new Date());
                ts.setCreator(providerNo);
                ts.setSuggestedText(inactiveTextStr);
                ticklerTextSuggestDao.persist(ts);
            } else {
                ts.setActive(false);
                ticklerTextSuggestDao.merge(ts);
            }

        }

        return "close";
    }

    private String[] activeText;
    private String[] inactiveText;

    public String[] getActiveText() {
        return activeText;
    }

    public void setActiveText(String[] activeText) {
        this.activeText = activeText;
    }

    public String[] getInactiveText() {
        return inactiveText;
    }

    public void setInactiveText(String[] inactiveText) {
        this.inactiveText = inactiveText;
    }
}
