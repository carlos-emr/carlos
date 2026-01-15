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

package ca.openosp.openo.billings.ca.bc.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Iterator;
import java.util.List;

public class SaveAssoc2Action
        extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {
        ServiceCodeAssociation assoc = this.getSvcAssoc();
        BillingAssociationPersistence per = new BillingAssociationPersistence();
        if (!this.getMode().equals("edit")) {
            if (per.assocExists(assoc.getServiceCode())) {
                addActionError(getText("oscar.billing.CA.BC.billingBC.error.assocexists", new String[]{assoc.getServiceCode()}));
            }
        }
        if (!per.serviceCodeExists(assoc.getServiceCode())) {
            addActionError(getText("oscar.billing.CA.BC.billingBC.error.invalidsvccode", new String[]{assoc.getServiceCode()}));
        }
        List dxcodes = assoc.getDxCodes();
        for (Iterator iter = dxcodes.iterator(); iter.hasNext(); ) {
            String code = (String) iter.next();
            if (!per.dxcodeExists(code)) {
                addActionError(getText("oscar.billing.CA.BC.billingBC.error.invaliddxcode", new String[]{code}));

            }
        }
        per.saveServiceCodeAssociation(assoc, this.getMode());
        return SUCCESS;
    }

    private String[] service;
    private String xml_provider, xml_location, xml_billtype;
    private String xml_appointment_date;
    private String xml_visittype, xml_vdate;
    private String xml_other1, xml_other2, xml_other3;
    private String xml_other1_unit, xml_other2_unit, xml_other3_unit;
    private String xml_refer1 = "", xml_refer2 = "", refertype1, refertype2;
    private String xml_diagnostic_detail1, xml_diagnostic_detail2,
            xml_diagnostic_detail3;
    private String xml_encounter = "9";
    private String notes = "", icbc_claim_no;
    private String correspondenceCode;
    private String dependent = null;
    private String afterHours = null;
    private String timeCall = null;
    private String submissionCode = null;
    private String service_to_date = null;
    private String shortClaimNote = null;
    private String messageNotes = null;
    private String mva_claim_code = null;
    private String facilityNum = null;
    private String facilitySubNum = null;
    private String mode;
    private String xml_endtime_hr = "";
    private String xml_endtime_min = "";
    private String xml_starttime_hr = "";
    private String xml_starttime_min = "";
    String requestId;

    public String[] getService() {
        return service;
    }

    public void setService(String[] service) {
        this.service = service;
    }

    public String getXml_provider() {
        return xml_provider;
    }

    public void setXml_provider(String xml_provider) {
        this.xml_provider = xml_provider;
    }

    public String getXml_location() {
        return xml_location;
    }

    public void setXml_location(String xml_location) {
        this.xml_location = xml_location;
    }

    public String getXml_billtype() {
        return xml_billtype;
    }

    public void setXml_billtype(String xml_billtype) {
        this.xml_billtype = xml_billtype;
    }

    public String getXml_appointment_date() {
        return xml_appointment_date;
    }

    public void setXml_appointment_date(String xml_appointment_date) {
        this.xml_appointment_date = xml_appointment_date;
    }

    public String getXml_visittype() {
        return xml_visittype;
    }

    public void setXml_visittype(String xml_visittype) {
        this.xml_visittype = xml_visittype;
    }

    public String getXml_vdate() {
        return xml_vdate;
    }

    public void setXml_vdate(String xml_vdate) {
        this.xml_vdate = xml_vdate;
    }

    public String getXml_other1() {
        return xml_other1;
    }

    public void setXml_other1(String xml_other1) {
        this.xml_other1 = xml_other1;
    }

    public String getXml_other2() {
        return xml_other2;
    }

    public void setXml_other2(String xml_other2) {
        this.xml_other2 = xml_other2;
    }

    public String getXml_other3() {
        return xml_other3;
    }

    public void setXml_other3(String xml_other3) {
        this.xml_other3 = xml_other3;
    }

    public String getXml_other1_unit() {
        return xml_other1_unit;
    }

    public void setXml_other1_unit(String xml_other1_unit) {
        this.xml_other1_unit = xml_other1_unit;
    }

    public String getXml_other2_unit() {
        return xml_other2_unit;
    }

    public void setXml_other2_unit(String xml_other2_unit) {
        this.xml_other2_unit = xml_other2_unit;
    }

    public String getXml_other3_unit() {
        return xml_other3_unit;
    }

    public void setXml_other3_unit(String xml_other3_unit) {
        this.xml_other3_unit = xml_other3_unit;
    }

    public String getXml_refer1() {
        return xml_refer1;
    }

    public void setXml_refer1(String xml_refer1) {
        this.xml_refer1 = xml_refer1;
    }

    public String getXml_refer2() {
        return xml_refer2;
    }

    public void setXml_refer2(String xml_refer2) {
        this.xml_refer2 = xml_refer2;
    }

    public String getRefertype1() {
        return refertype1;
    }

    public void setRefertype1(String refertype1) {
        this.refertype1 = refertype1;
    }

    public String getRefertype2() {
        return refertype2;
    }

    public void setRefertype2(String refertype2) {
        this.refertype2 = refertype2;
    }

    public String getXml_diagnostic_detail1() {
        return xml_diagnostic_detail1;
    }

    public void setXml_diagnostic_detail1(String xml_diagnostic_detail1) {
        this.xml_diagnostic_detail1 = xml_diagnostic_detail1;
    }

    public String getXml_diagnostic_detail2() {
        return xml_diagnostic_detail2;
    }

    public void setXml_diagnostic_detail2(String xml_diagnostic_detail2) {
        this.xml_diagnostic_detail2 = xml_diagnostic_detail2;
    }

    public String getXml_diagnostic_detail3() {
        return xml_diagnostic_detail3;
    }

    public void setXml_diagnostic_detail3(String xml_diagnostic_detail3) {
        this.xml_diagnostic_detail3 = xml_diagnostic_detail3;
    }

    public String getXml_encounter() {
        return xml_encounter;
    }

    public void setXml_encounter(String xml_encounter) {
        this.xml_encounter = xml_encounter;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getIcbc_claim_no() {
        return icbc_claim_no;
    }

    public void setIcbc_claim_no(String icbc_claim_no) {
        this.icbc_claim_no = icbc_claim_no;
    }

    public String getCorrespondenceCode() {
        return correspondenceCode;
    }

    public void setCorrespondenceCode(String correspondenceCode) {
        this.correspondenceCode = correspondenceCode;
    }

    public String getDependent() {
        return dependent;
    }

    public void setDependent(String dependent) {
        this.dependent = dependent;
    }

    public String getAfterHours() {
        return afterHours;
    }

    public void setAfterHours(String afterHours) {
        this.afterHours = afterHours;
    }

    public String getTimeCall() {
        return timeCall;
    }

    public void setTimeCall(String timeCall) {
        this.timeCall = timeCall;
    }

    public String getSubmissionCode() {
        return submissionCode;
    }

    public void setSubmissionCode(String submissionCode) {
        this.submissionCode = submissionCode;
    }

    public String getService_to_date() {
        return service_to_date;
    }

    public void setService_to_date(String service_to_date) {
        this.service_to_date = service_to_date;
    }

    public String getShortClaimNote() {
        return shortClaimNote;
    }

    public void setShortClaimNote(String shortClaimNote) {
        this.shortClaimNote = shortClaimNote;
    }

    public String getMessageNotes() {
        return messageNotes;
    }

    public void setMessageNotes(String messageNotes) {
        this.messageNotes = messageNotes;
    }

    public String getMva_claim_code() {
        return mva_claim_code;
    }

    public void setMva_claim_code(String mva_claim_code) {
        this.mva_claim_code = mva_claim_code;
    }

    public String getFacilityNum() {
        return facilityNum;
    }

    public void setFacilityNum(String facilityNum) {
        this.facilityNum = facilityNum;
    }

    public String getFacilitySubNum() {
        return facilitySubNum;
    }

    public void setFacilitySubNum(String facilitySubNum) {
        this.facilitySubNum = facilitySubNum;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getXml_endtime_hr() {
        return xml_endtime_hr;
    }

    public void setXml_endtime_hr(String xml_endtime_hr) {
        this.xml_endtime_hr = xml_endtime_hr;
    }

    public String getXml_endtime_min() {
        return xml_endtime_min;
    }

    public void setXml_endtime_min(String xml_endtime_min) {
        this.xml_endtime_min = xml_endtime_min;
    }

    public String getXml_starttime_hr() {
        return xml_starttime_hr;
    }

    public void setXml_starttime_hr(String xml_starttime_hr) {
        this.xml_starttime_hr = xml_starttime_hr;
    }

    public String getXml_starttime_min() {
        return xml_starttime_min;
    }

    public void setXml_starttime_min(String xml_starttime_min) {
        this.xml_starttime_min = xml_starttime_min;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public ServiceCodeAssociation getSvcAssoc() {
        ServiceCodeAssociation svc = new ServiceCodeAssociation();
        svc.setServiceCode(this.xml_other1);
        if (!this.getXml_diagnostic_detail1().trim().equals("")) {
            svc.addDXCode(this.getXml_diagnostic_detail1().trim());
        }
        if (!this.getXml_diagnostic_detail2().trim().equals("")) {
            svc.addDXCode(this.getXml_diagnostic_detail2().trim());
        }

        if (!this.getXml_diagnostic_detail3().trim().equals("")) {
            svc.addDXCode(this.getXml_diagnostic_detail3().trim());
        }

        return svc;
    }
}
