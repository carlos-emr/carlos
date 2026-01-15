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
import ca.openosp.openo.commn.dao.ProfessionalSpecialistDao;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctConAddSpecialist2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute()
            throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "w", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        ProfessionalSpecialist professionalSpecialist = null;
        //EctConAddSpecialistForm addSpecailistForm = (EctConAddSpecialistForm) form;

        int whichType = this.getwhichType();
        if (whichType == 1) //create
        {
            professionalSpecialist = new ProfessionalSpecialist();
            populateFields(professionalSpecialist);
            if (professionalSpecialist.getReferralNo() != null && professionalSpecialist.getReferralNo().length() > 0) {
                if (referralNoValid(professionalSpecialist.getReferralNo())) {
                    if (referralNoInUse(professionalSpecialist.getReferralNo())) {
                        request.setAttribute("refnoinuse", true);
                        return SUCCESS;
                    }
                } else {
                    request.setAttribute("refnoinvalid", true);
                    return SUCCESS;
                }
            }
            professionalSpecialistDao.persist(professionalSpecialist);
        } else if (whichType == 2) // update
        {
            request.setAttribute("upd", true);

            Integer specId = Integer.parseInt(this.getSpecId());
            professionalSpecialist = professionalSpecialistDao.find(specId);
            populateFields(professionalSpecialist);
            if (professionalSpecialist.getReferralNo() != null && professionalSpecialist.getReferralNo().length() > 0) {
                if (referralNoValid(professionalSpecialist.getReferralNo())) {
                    if (referralNoInUse(professionalSpecialist.getReferralNo(), specId)) {
                        request.setAttribute("refnoinuse", true);
                        return SUCCESS;
                    }
                } else {
                    request.setAttribute("refnoinvalid", true);
                    return SUCCESS;
                }
            }
            professionalSpecialistDao.merge(professionalSpecialist);

            EctConConstructSpecialistsScriptsFile constructSpecialistsScriptsFile = new EctConConstructSpecialistsScriptsFile();
            constructSpecialistsScriptsFile.makeString(request.getLocale());
        } else {
            logger.error("missed a case, whichType=" + whichType);
        }

        this.resetForm();

        String added = "" + professionalSpecialist.getFirstName() + " " + professionalSpecialist.getLastName();
        request.setAttribute("Added", added);
        return SUCCESS;
    }

    private boolean referralNoInUse(String referralNo) {
        return professionalSpecialistDao.getByReferralNo(referralNo) != null;
    }

    private boolean referralNoInUse(String referralNo, Integer specId) {
        ProfessionalSpecialist specialist = professionalSpecialistDao.getByReferralNo(referralNo);
        return (specialist != null && (specialist.getId().intValue() != specId.intValue()));
    }

    private boolean referralNoValid(String referralNo) {

        String pattern = OscarProperties.getInstance().getProperty("referral_no.pattern", "^[a-zA-Z0-9]*$");

        try {
            if (referralNo.matches(pattern))
                return true;
        } catch (Exception e) {
            MiscUtils.getLogger().info("Specified referral number invalid (" + referralNo + ")", e);
        }

        return false;
    }

    private void populateFields(ProfessionalSpecialist professionalSpecialist) {
        professionalSpecialist.setFirstName(this.getFirstName());
        professionalSpecialist.setLastName(this.getLastName());
        professionalSpecialist.setProfessionalLetters(this.getProLetters());

        professionalSpecialist.setStreetAddressFromForm(this.getAddress());

        professionalSpecialist.setPhoneNumber(this.getPhone());
        professionalSpecialist.setFaxNumber(this.getFax());
        professionalSpecialist.setWebSite(this.getWebsite());
        professionalSpecialist.setEmailAddress(this.getEmail());
        professionalSpecialist.setSpecialtyType(this.getSpecType());
        professionalSpecialist.seteDataUrl(this.geteDataUrl());
        professionalSpecialist.seteDataOscarKey(this.geteDataOscarKey());
        professionalSpecialist.seteDataServiceKey(this.geteDataServiceKey());
        professionalSpecialist.seteDataServiceName(this.geteDataServiceName());
        professionalSpecialist.setAnnotation(this.getAnnotation());
        professionalSpecialist.setReferralNo(this.getReferralNo());
        professionalSpecialist.setInstitutionId(Integer.parseInt(this.getInstitution()));
        professionalSpecialist.setDepartmentId(Integer.parseInt(this.getDepartment()));
        professionalSpecialist.setPrivatePhoneNumber(this.getPrivatePhoneNumber());
        professionalSpecialist.setCellPhoneNumber(this.getCellPhoneNumber());
        professionalSpecialist.setPagerNumber(this.getPagerNumber());
        professionalSpecialist.setSalutation(this.getSalutation());
        professionalSpecialist.setHideFromView(this.getHideFromView());
        professionalSpecialist.setEformId(this.getEformId());
    }

    String fName;
    String lName;
    String proLetters;
    String address;
    String phone;
    String fax;
    String website;
    String email;
    String specType;
    String transType;
    String specId;
    int whichType;
    String eDataUrl;
    String eDataOscarKey;
    String eDataServiceKey;
    String eDataServiceName;
    String annotation;
    String institution;
    String department;
    String privatePhoneNumber;
    String cellPhoneNumber;
    String pagerNumber;
    String salutation;
    Boolean hideFromView;
    Integer eformId;

    private String referralNo;

    public int getwhichType() {
        return whichType;
    }

    public String getTransType() {
        if (transType == null) transType = new String();
        return transType;
    }

    public void setTransType(String str) {
        transType = str;
    }

    public String getFirstName() {
        if (fName == null) fName = new String();
        return fName;
    }

    public void setFirstName(String str) {
        fName = str;
    }

    public String getLastName() {
        if (lName == null) lName = new String();
        return lName;
    }

    public void setLastName(String str) {
        lName = str;
    }

    public String getProLetters() {
        if (proLetters == null) proLetters = new String();
        return proLetters;
    }

    public void setProLetters(String str) {
        proLetters = str;
    }

    public String getAddress() {
        if (address == null) address = new String();
        return address;
    }

    public void setAddress(String str) {
        address = str;
    }

    public String getPhone() {
        if (phone == null) phone = new String();
        return phone;
    }

    public void setPhone(String str) {
        phone = str;
    }

    public String getFax() {
        if (fax == null) fax = new String();
        return fax;
    }

    public void setFax(String str) {
        fax = str;
    }

    public String getWebsite() {
        if (website == null) website = new String();
        return website;
    }

    public void setWebsite(String str) {
        website = str;
    }

    public String getEmail() {
        if (email == null) email = new String();
        return email;
    }

    public void setEmail(String str) {
        email = str;
    }

    public String getSpecType() {
        if (specType == null) specType = new String();
        return specType;
    }

    public void setSpecType(String str) {
        specType = str;
    }

    public String getSpecId() {
        if (specId == null) specId = new String();
        return specId;
    }

    public void setSpecId(String str) {
        specId = str;
    }

    public void reset() {
        resetForm();
    }

    public void resetForm() {
        fName = null;
        lName = null;
        proLetters = null;
        address = null;
        phone = null;
        fax = null;
        website = null;
        email = null;
        specType = null;
        transType = null;
        specId = null;
        eDataUrl = null;
        eDataOscarKey = null;
        eDataServiceKey = null;
        annotation = null;
        referralNo = null;
        privatePhoneNumber = null;
        cellPhoneNumber = null;
        pagerNumber = null;
        salutation = null;
        whichType = 0;
    }

    /**
     * Returns the whichType.
     *
     * @return int
     */
    public int getWhichType() {
        return whichType;
    }

    /**
     * Sets the whichType.
     *
     * @param whichType The whichType to set
     */
    public void setWhichType(int whichType) {
        this.whichType = whichType;
    }

    public String geteDataUrl() {
        return eDataUrl;
    }

    public void seteDataUrl(String eDataUrl) {
        this.eDataUrl = eDataUrl;
    }

    public String geteDataOscarKey() {
        return eDataOscarKey;
    }

    public void seteDataOscarKey(String eDataOscarKey) {
        this.eDataOscarKey = eDataOscarKey;
    }

    public String geteDataServiceKey() {
        return eDataServiceKey;
    }

    public void seteDataServiceKey(String eDataServiceKey) {
        this.eDataServiceKey = eDataServiceKey;
    }

    public String geteDataServiceName() {
        return eDataServiceName;
    }

    public void seteDataServiceName(String eDataServiceName) {
        this.eDataServiceName = eDataServiceName;
    }

    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    public void setReferralNo(String referralNo) {
        this.referralNo = referralNo;
    }

    public String getReferralNo() {
        return referralNo;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getPrivatePhoneNumber() {
        return privatePhoneNumber;
    }

    public void setPrivatePhoneNumber(String privatePhoneNumber) {
        this.privatePhoneNumber = privatePhoneNumber;
    }

    public String getCellPhoneNumber() {
        return cellPhoneNumber;
    }

    public void setCellPhoneNumber(String cellPhoneNumber) {
        this.cellPhoneNumber = cellPhoneNumber;
    }

    public String getPagerNumber() {
        return pagerNumber;
    }

    public void setPagerNumber(String pagerNumber) {
        this.pagerNumber = pagerNumber;
    }

    public String getSalutation() {
        return salutation;
    }

    public void setSalutation(String salutation) {
        this.salutation = salutation;
    }

    public Boolean getHideFromView() {
        return hideFromView;
    }

    public void setHideFromView(Boolean hideFromView) {
        this.hideFromView = hideFromView;
    }

    public Integer getEformId() {
        return eformId;
    }

    public void setEformId(Integer eformId) {
        this.eformId = eformId;
    }

}
