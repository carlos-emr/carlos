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
package ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.InstitutionDao;
import ca.openosp.openo.commn.model.Institution;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctConAddInstitution2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private InstitutionDao institutionDao = SpringUtils.getBean(InstitutionDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "w", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        Institution institution = null;
        //EctConAddInstitutionForm addInstitutionForm = (EctConAddInstitutionForm) form;

        int whichType = this.getWhichType();
        if (whichType == 1) //create
        {
            institution = new Institution();
            populateFields(institution);
            institutionDao.persist(institution);

        } else if (whichType == 2) // update
        {
            request.setAttribute("upd", true);

            Integer id = Integer.parseInt(this.getId());
            institution = institutionDao.find(id);
            populateFields(institution);
            institutionDao.merge(institution);

        } else {
            logger.error("missed a case, whichType=" + whichType);
        }

        this.resetForm();

        String added = "" + institution.getName();
        request.setAttribute("Added", added);
        return SUCCESS;
    }


    private void populateFields(Institution institution) {
        institution.setName(this.getName());
        institution.setAddress(this.getAddress());
        institution.setCity(this.getCity());
        institution.setProvince(this.getProvince());
        institution.setPostal(this.getPostal());
        institution.setCountry(this.getCountry());
        institution.setPhone(this.getPhone());
        institution.setFax(this.getFax());
        institution.setWebsite(this.getWebsite());
        institution.setEmail(this.getEmail());
        institution.setAnnotation(this.getAnnotation());
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

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPostal() {
        return postal;
    }

    public void setPostal(String postal) {
        this.postal = postal;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getWhichType() {
        return whichType;
    }

    public void setWhichType(int whichType) {
        this.whichType = whichType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnnotation() {
        return annotation;
    }

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
