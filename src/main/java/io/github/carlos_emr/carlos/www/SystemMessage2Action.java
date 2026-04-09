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

import io.github.carlos_emr.carlos.commn.dao.SystemMessageDao;
import io.github.carlos_emr.carlos.commn.model.SystemMessage;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class SystemMessage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SystemMessageDao systemMessageDao = SpringUtils.getBean(SystemMessageDao.class);

    public String execute() {
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
            SystemMessage msg = systemMessageDao.find(Integer.parseInt(messageId));

            if (msg == null) {
                addActionMessage(getText("system_message.missing"));
                return list();
            }
            request.getSession().setAttribute("systemMessageId", messageId);
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
        if (messageId_str != null && messageId_str != "") {
            messageId = Integer.valueOf(messageId_str).intValue();
        }

        if (messageId > 0 || (msg.getId() != null && msg.getId().intValue() > 0)) {
            msg.setId(messageId);
            systemMessageDao.merge(msg);
        } else {
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
