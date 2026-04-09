/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.www;

import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.commn.dao.SystemMessageDao;
import io.github.carlos_emr.carlos.commn.model.SystemMessage;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class SystemMessage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    private SystemMessageDao systemMessageDao = SpringUtils.getBean(SystemMessageDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String mtd = request.getParameter("method");
        if ("edit".equals(mtd)) {
            return edit();
        } else if ("save".equals(mtd)) {
            return save();
        } else if ("view".equals(mtd)) {
            return view();
        }
        return list();
    }

    public String list() {
        List<SystemMessage> activeMessages = systemMessageDao.findAll();
        request.setAttribute("ActiveMessages", activeMessages);
        return "list";
    }

    public String edit() {
        String messageId = request.getParameter("id");

        if (messageId != null) {
            if (!messageId.matches("\\d{1,9}")) {
                throw new IllegalArgumentException("Invalid message id");
            }

            long parsedMessageId = Long.parseLong(messageId);
            if (parsedMessageId > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid message id");
            }

            SystemMessage msg = systemMessageDao.find((int) parsedMessageId);
            if (msg == null) {
                request.getSession().removeAttribute("systemMessageId");
                addActionMessage(getText("system_message.missing"));
                return list();
            }
            request.getSession().setAttribute("systemMessageId", String.valueOf(msg.getId()));
        } else {
            request.getSession().setAttribute("systemMessageId", "");
        }

        return "edit";
    }

    public String save() {

        SystemMessage msg = this.getSystem_message();
        msg.setCreationDate(new Date());
        int messageId = 0;
        String messageId_str = (String) request.getSession().getAttribute("systemMessageId");
        if (messageId_str != null && !messageId_str.isEmpty()) {
            try {
                messageId = Integer.valueOf(messageId_str).intValue();
            } catch (NumberFormatException e) {
                logger.warn("Non-numeric session message ID rejected: {}", messageId_str);
                return list();
            }
        }

        if (messageId > 0) {
            msg.setId(messageId);
            systemMessageDao.merge(msg);
        } else {
            // New message: ensure attacker cannot set ID via @StrutsParameter POST
            if (msg.getId() != null && msg.getId() > 0) {
                msg.setId(null);
            }
            systemMessageDao.persist(msg);
        }

        addActionMessage(getText("system_message.saved"));

        return list();
    }

    public String view() {
        List<SystemMessage> messages = systemMessageDao.findAll();
        if (messages.size() > 0) {
            request.setAttribute("messages", messages);
        }
        return "view";
    }

    private SystemMessage system_message;

    @StrutsParameter(depth = 1)
    public SystemMessage getSystem_message() {
        return system_message;
    }

    @StrutsParameter
    public void setSystem_message(SystemMessage system_message) {
        this.system_message = system_message;
    }
}
