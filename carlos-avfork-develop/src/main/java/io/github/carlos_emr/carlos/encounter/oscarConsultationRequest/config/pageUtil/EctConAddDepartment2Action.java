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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DepartmentDao;
import io.github.carlos_emr.carlos.commn.model.Department;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class EctConAddDepartment2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private DepartmentDao DepartmentDao = SpringUtils.getBean(DepartmentDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "w", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        Department Department = null;

        int whichType = this.getWhichType();
        if (whichType == 1) //create
        {
            Department = new Department();
            populateFields(Department);
            DepartmentDao.persist(Department);

        } else if (whichType == 2) // update
        {
            request.setAttribute("upd", true);

            Integer id = Integer.parseInt(this.getId());
            Department = DepartmentDao.find(id);
            populateFields(Department);
            DepartmentDao.merge(Department);

        } else {
            logger.error("missed a case, whichType=" + whichType);
        }

        this.resetForm();

        String added = "" + Department.getName();
        request.setAttribute("Added", added);
        return SUCCESS;
    }

    private void populateFields(Department Department) {
        Department.setName(this.getName());
        Department.setAnnotation(this.getAnnotation());
    }

    private String id;
    private String name;
    private String address;
    private String city;
    private String province;
    private String country;
    private String postal;
    private String phone;
    private String fax;
    private String website;
    private String email;
    int whichType;
    private String annotation;

    public String getName() {
        return name;
    }

    @StrutsParameter
    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    @StrutsParameter
    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    @StrutsParameter
    public void setCity(String city) {
        this.city = city;
    }

    public String getProvince() {
        return province;
    }

    @StrutsParameter
    public void setProvince(String province) {
        this.province = province;
    }

    public String getCountry() {
        return country;
    }

    @StrutsParameter
    public void setCountry(String country) {
        this.country = country;
    }

    public String getPostal() {
        return postal;
    }

    @StrutsParameter
    public void setPostal(String postal) {
        this.postal = postal;
    }

    public String getPhone() {
        return phone;
    }

    @StrutsParameter
    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFax() {
        return fax;
    }

    @StrutsParameter
    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getWebsite() {
        return website;
    }

    @StrutsParameter
    public void setWebsite(String website) {
        this.website = website;
    }

    public String getEmail() {
        return email;
    }

    @StrutsParameter
    public void setEmail(String email) {
        this.email = email;
    }

    public int getWhichType() {
        return whichType;
    }

    @StrutsParameter
    public void setWhichType(int whichType) {
        this.whichType = whichType;
    }

    public String getId() {
        return id;
    }

    @StrutsParameter
    public void setId(String id) {
        this.id = id;
    }

    public String getAnnotation() {
        return annotation;
    }

    @StrutsParameter
    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    public void resetForm() {
        name = null;

        address = null;
        phone = null;
        fax = null;
        website = null;
        email = null;
        city = null;
        province = null;
        country = null;
        postal = null;

        whichType = 0;
    }
}
