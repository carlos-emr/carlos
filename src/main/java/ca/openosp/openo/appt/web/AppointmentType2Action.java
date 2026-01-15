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

package ca.openosp.openo.appt.web;

import ca.openosp.openo.commn.IsPropertiesOn;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.AppointmentTypeDao;
import ca.openosp.openo.commn.dao.SiteDao;
import ca.openosp.openo.commn.model.AppointmentType;
import ca.openosp.openo.commn.model.Site;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.util.LabelValueBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AppointmentType2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    @Override
    public String execute() throws IOException, ServletException {
        String sOper = request.getParameter("oper");
        int typeNo = -1;
        if ((this.getId() != null ? this.getId().intValue() : -1) > 0) {
            typeNo = this.getId().intValue();
        } else if (request.getParameter("no") != null) {
            try {
                typeNo = Integer.parseInt(request.getParameter("no"));
            } catch (NumberFormatException nex) {
                addActionError(getText("appointment.type.number.error"));
                return "failure";
            }
        }

        if (sOper != null) {
            if (sOper.equals("save")) {
                if (this.getName() == null || this.getName().length() == 0 || this.getName().length() > 50) {
                    addActionError(getText("appointment.type.name.error"));
                    return "failure";
                }
            }

            AppointmentTypeDao appDao = (AppointmentTypeDao) SpringUtils.getBean(AppointmentTypeDao.class);

            if (sOper.equals("edit")) {
                AppointmentType dbBean = appDao.find(Integer.valueOf(typeNo));
                if (dbBean != null) {
                    //this.setTypeNo(dbBean.getTypeNo());
                    this.setId(dbBean.getId());
                    this.setName(dbBean.getName());
                    this.setDuration(dbBean.getDuration());
                    this.setLocation(dbBean.getLocation());
                    this.setNotes(dbBean.getNotes());
                    this.setReason(dbBean.getReason());
                    this.setResources(dbBean.getResources());
                } else {
                    addActionError(getText("appointment.type.notfound.error"));
                    return "failure";
                }
            } else if (sOper.equals("save")) {
                if (typeNo <= 0) {
                    //new bean
                    AppointmentType bean = new AppointmentType();
                    bean.setName(this.getName());
                    bean.setDuration(this.getDuration());
                    bean.setLocation(this.getLocation());
                    bean.setNotes(this.getNotes());
                    bean.setReason(this.getReason());
                    bean.setResources(this.getResources());
                    appDao.persist(bean);
                } else {
                    AppointmentType bean = appDao.find(Integer.valueOf(typeNo));
                    if (bean != null) {
                        bean.setName(this.getName());
                        bean.setDuration(this.getDuration());
                        bean.setLocation(this.getLocation());
                        bean.setNotes(this.getNotes());
                        bean.setReason(this.getReason());
                        bean.setResources(this.getResources());
                        appDao.merge(bean);
                    } else {
                        addActionError(getText("appointment.type.notfound.error"));
                        return "failure";
                    }
                }
            } else if (sOper.equals("del")) {
                try {
                    appDao.remove(typeNo);
                    return "redirect"; 
                } catch (Exception e) {
                    addActionError("Delete failed: " + e.getMessage());
                    return "failure";
                }
            }

        }

        if (IsPropertiesOn.isMultisitesEnable()) {
            List<LabelValueBean> locations = new ArrayList<LabelValueBean>();
            SiteDao siteDao = (SiteDao) SpringUtils.getBean(SiteDao.class);
            List<Site> sites = siteDao.getAllActiveSites();
            for (Site site : sites) {
                locations.add(new LabelValueBean(site.getName(), Integer.toString(site.getSiteId())));
            }
            request.setAttribute("locationsList", locations);
        }

        return SUCCESS;
    }
    private int typeNo;
    private Integer id;
    private String name;
    private String notes;
    private String reason;
    private String location;
    private String resources;
    private int duration;

    public int getTypeNo() {
        return typeNo;
    }

    public void setTypeNo(int typeNo) {
        this.typeNo = typeNo;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getResources() {
        return resources;
    }

    public void setResources(String resources) {
        this.resources = resources;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

}
