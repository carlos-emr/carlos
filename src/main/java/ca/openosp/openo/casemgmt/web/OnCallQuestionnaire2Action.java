//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.casemgmt.web;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.OnCallQuestionnaireDao;
import ca.openosp.openo.commn.model.OnCallQuestionnaire;
import ca.openosp.openo.utility.SpringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class OnCallQuestionnaire2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private OnCallQuestionnaireDao dao = SpringUtils.getBean(OnCallQuestionnaireDao.class);

    public String execute() throws Exception {
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        return form();
    }

    public String form() {
        return "form";
    }

    public String save() throws Exception {
        String providerNo = request.getParameter("providerNo");
        String type = request.getParameter("type");

        OnCallQuestionnaire bean = new OnCallQuestionnaire();
        bean.setProviderNo(providerNo);
        bean.setType(type);
        bean.setCourseOfAction(this.getCourse_action());
        bean.setCallTime(this.getDateObject());
        bean.setNurseInvolved(this.getNurse());
        bean.setPhysicialConsultationRequired(this.getPhysician_consult());
        bean.setHealthType(this.getType_health());

        dao.persist(bean);

        return "windowClose";
    }

    private String type_health;
    private String nurse;
    private String course_action;
    private String physician_consult;
    private String date;
    private String time;

    public Date getDateObject() throws ParseException {
        SimpleDateFormat d = new SimpleDateFormat("yyyy-MM-dd kk:mm");
        return d.parse(getDate().trim() + " " + getTime().trim());
    }

    public String getType_health() {
        return type_health;
    }

    public void setType_health(String type_health) {
        this.type_health = type_health;
    }

    public String getNurse() {
        return nurse;
    }

    public void setNurse(String nurse) {
        this.nurse = nurse;
    }

    public String getCourse_action() {
        return course_action;
    }

    public void setCourse_action(String course_action) {
        this.course_action = course_action;
    }

    public String getPhysician_consult() {
        return physician_consult;
    }

    public void setPhysician_consult(String physician_consult) {
        this.physician_consult = physician_consult;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

}
