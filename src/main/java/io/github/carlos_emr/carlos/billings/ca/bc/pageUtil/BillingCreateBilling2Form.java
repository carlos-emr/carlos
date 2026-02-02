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


package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.util.List;

public final class BillingCreateBilling2Form {
    private static final Logger _log = MiscUtils.getLogger();

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

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_provider() {
        return xml_provider != null ? xml_provider : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_provider(String xml_provider) {
        this.xml_provider = xml_provider;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_location() {
        return xml_location != null ? xml_location : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_location(String xml_location) {
        this.xml_location = xml_location;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_billtype() {
        _log.debug(this.hashCode());
        return xml_billtype != null ? xml_billtype : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_billtype(String xml_billtype) {
        _log.debug(this.hashCode());
        this.xml_billtype = xml_billtype;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_appointment_date() {
        return xml_appointment_date != null ? xml_appointment_date : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_appointment_date(String xml_appointment_date) {
        this.xml_appointment_date = xml_appointment_date;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_visittype() {
        return xml_visittype != null ? xml_visittype : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_visittype(String xml_visittype) {
        this.xml_visittype = xml_visittype;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_vdate() {
        return xml_vdate != null ? xml_vdate : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_vdate(String xml_vdate) {
        this.xml_vdate = xml_vdate;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_refer1() {
        return xml_refer1 != null ? xml_refer1 : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_refer1(String xml_refer1) {
        this.xml_refer1 = xml_refer1;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_refer2() {
        return xml_refer2 != null ? xml_refer2 : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_refer2(String xml_refer2) {
        this.xml_refer2 = xml_refer2;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getRefertype1() {
        return refertype1 != null ? refertype1 : "";
    }

    /**
     * The set method for the message String
     */
    public void setRefertype1(String refertype1) {
        this.refertype1 = refertype1;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getRefertype2() {
        return refertype2 != null ? refertype2 : "";
    }

    /**
     * The set method for the message String
     */
    public void setRefertype2(String refertype2) {
        this.refertype2 = refertype2;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_other1() {
        return xml_other1 != null ? xml_other1 : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_other1(String xml_other1) {
        this.xml_other1 = xml_other1;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_other2() {
        return xml_other2 != null ? xml_other2 : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_other2(String xml_other2) {
        this.xml_other2 = xml_other2;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_other3() {
        return xml_other3 != null ? xml_other3 : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_other3(String xml_other3) {
        this.xml_other3 = xml_other3;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_other1_unit() {
        return xml_other1_unit != null ? xml_other1_unit : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_other1_unit(String xml_other1_unit) {
        this.xml_other1_unit = xml_other1_unit;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_other2_unit() {
        return xml_other2_unit != null ? xml_other2_unit : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_other2_unit(String xml_other2_unit) {
        this.xml_other2_unit = xml_other2_unit;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_other3_unit() {
        return xml_other3_unit != null ? xml_other3_unit : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_other3_unit(String xml_other3_unit) {
        this.xml_other3_unit = xml_other3_unit;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_diagnostic_detail1() {
        return xml_diagnostic_detail1 != null ? xml_diagnostic_detail1 : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_diagnostic_detail1(String xml_diagnostic_detail1) {
        this.xml_diagnostic_detail1 = xml_diagnostic_detail1;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_diagnostic_detail2() {
        return xml_diagnostic_detail2 != null ? xml_diagnostic_detail2 : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_diagnostic_detail2(String xml_diagnostic_detail2) {
        this.xml_diagnostic_detail2 = xml_diagnostic_detail2;
    }

    /**
     * The get method for the message String
     *
     * @return String, this is the text of the message
     */
    public String getXml_diagnostic_detail3() {
        return xml_diagnostic_detail3 != null ? xml_diagnostic_detail3 : "";
    }

    /**
     * The set method for the message String
     */
    public void setXml_diagnostic_detail3(String xml_diagnostic_detail3) {
        this.xml_diagnostic_detail3 = xml_diagnostic_detail3;
    }

    /**
     * An Array of Strings thats contains providers numbers
     *
     * @return String[], the providers numbers that this message will be set to
     */
    public String[] getService() {
        if (service == null) {
            service = new String[]{};
        }
        return service;
    }

    /**
     * The set method for an Array of Strings that contains services
     *
     * @param service
     */
    public void setService(String[] service) {
        this.service = service;
    }

    /**
     * Getter for property xml_encounter.
     *
     * @return Value of property xml_encounter.
     */
    public String getXml_encounter() {
        return xml_encounter;
    }

    /**
     * Setter for property xml_encounter.
     *
     * @param xml_encounter New value of property xml_encounter.
     */
    public void setXml_encounter(String xml_encounter) {
        this.xml_encounter = xml_encounter;
    }

    /**
     * Getter for property notes.
     *
     * @return Value of property notes.
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Setter for property notes.
     *
     * @param notes New value of property notes.
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Getter for property icbc_claim_no.
     *
     * @return Value of property icbc_claim_no.
     */
    public String getIcbc_claim_no() {
        return icbc_claim_no;
    }

    /**
     * Setter for property icbc_claim_no.
     *
     * @param icbc_claim_no New value of property icbc_claim_no.
     */
    public void setIcbc_claim_no(String icbc_claim_no) {
        this.icbc_claim_no = icbc_claim_no;
    }

    /**
     * Getter for property correspondenceCode.
     *
     * @return Value of property correspondenceCode.
     */
    public String getCorrespondenceCode() {
        return correspondenceCode;
    }

    /**
     * Setter for property correspondenceCode.
     *
     * @param correspondenceCode New value of property correspondenceCode.
     */
    public void setCorrespondenceCode(String correspondenceCode) {
        this.correspondenceCode = correspondenceCode;
    }

    /**
     * Getter for property dependent.
     *
     * @return Value of property dependent.
     */
    public String getDependent() {
        return dependent;
    }

    /**
     * Setter for property dependent.
     *
     * @param dependent New value of property dependent.
     */
    public void setDependent(String dependent) {
        this.dependent = dependent;
    }

    /**
     * Getter for property afterHours.
     *
     * @return Value of property afterHours.
     */
    public String getAfterHours() {
        return afterHours;
    }

    /**
     * Setter for property afterHours.
     *
     * @param afterHours New value of property afterHours.
     */
    public void setAfterHours(String afterHours) {
        this.afterHours = afterHours;
    }

    /**
     * Getter for property timeCall.
     *
     * @return Value of property timeCall.
     */
    public String getTimeCall() {
        return timeCall;
    }

    /**
     * Setter for property timeCall.
     *
     * @param timeCall New value of property timeCall.
     */
    public void setTimeCall(String timeCall) {
        this.timeCall = timeCall;
    }

    /**
     * Getter for property submissionCode.
     *
     * @return Value of property submissionCode.
     */
    public String getSubmissionCode() {
        return submissionCode;
    }

    /**
     * Setter for property submissionCode.
     *
     * @param submissionCode New value of property submissionCode.
     */
    public void setSubmissionCode(String submissionCode) {
        this.submissionCode = submissionCode;
    }

    /**
     * Getter for property service_to_date.
     *
     * @return Value of property service_to_date.
     */
    public String getService_to_date() {
        return service_to_date;
    }

    /**
     * Setter for property service_to_date.
     *
     * @param service_to_date New value of property service_to_date.
     */
    public void setService_to_date(String service_to_date) {
        this.service_to_date = service_to_date;
    }

    /**
     * Getter for property shortClaimNote.
     *
     * @return Value of property shortClaimNote.
     */
    public String getShortClaimNote() {
        return shortClaimNote;
    }

    /**
     * Setter for property shortClaimNote.
     *
     * @param shortClaimNote New value of property shortClaimNote.
     */
    public void setShortClaimNote(String shortClaimNote) {
        this.shortClaimNote = shortClaimNote;
    }

    /**
     * Getter for property messageNotes.
     *
     * @return Value of property messageNotes.
     */
    public String getMessageNotes() {
        return messageNotes;
    }

    /**
     * Setter for property messageNotes.
     *
     * @param messageNotes New value of property messageNotes.
     */
    public void setMessageNotes(String messageNotes) {
        this.messageNotes = messageNotes;
    }

    /**
     * Getter for property mva_claim_code.
     *
     * @return Value of property mva_claim_code.
     */
    public String getMva_claim_code() {
        return mva_claim_code;
    }

    /**
     * Setter for property mva_claim_code.
     *
     * @param mva_claim_code New value of property mva_claim_code.
     */
    public void setMva_claim_code(String mva_claim_code) {
        this.mva_claim_code = mva_claim_code;
    }

    /**
     * Getter for property facilityNum.
     *
     * @return Value of property facilityNum.
     */
    public String getFacilityNum() {
        return facilityNum;
    }

    /**
     * Setter for property facilityNum.
     *
     * @param facilityNum New value of property facilityNum.
     */
    public void setFacilityNum(String facilityNum) {
        this.facilityNum = facilityNum;
    }

    /**
     * Getter for property facilitySubNum.
     *
     * @return Value of property facilitySubNum.
     */
    public String getFacilitySubNum() {
        return facilitySubNum;
    }

    public String getMode() {
        return mode;
    }

    public String getXml_endtime_hr() {
        return xml_endtime_hr;
    }

    public String getXml_endtime_min() {
        return xml_endtime_min;
    }

    public String getXml_starttime_hr() {
        return xml_starttime_hr;
    }

    public String getXml_starttime_min() {
        return xml_starttime_min;
    }

    public String getRequestId() {
        return requestId;
    }

    /**
     * Setter for property facilitySubNum.
     *
     * @param facilitySubNum New value of property facilitySubNum.
     */
    public void setFacilitySubNum(String facilitySubNum) {
        this.facilitySubNum = facilitySubNum;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setXml_endtime_hr(String xml_endtime_hr) {
        this.xml_endtime_hr = xml_endtime_hr;
    }

    public void setXml_endtime_min(String xml_endtime_min) {
        this.xml_endtime_min = xml_endtime_min;
    }

    public void setXml_starttime_hr(String xml_starttime_hr) {
        this.xml_starttime_hr = xml_starttime_hr;
    }

    public void setXml_starttime_min(String xml_starttime_min) {
        this.xml_starttime_min = xml_starttime_min;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Set the form parameters with the values of the ServiceCodesAssociation     *
     * @param assoc ServiceCodeAssociation
     */
    public void setServiceCodeAssociation(ServiceCodeAssociation assoc) {
        this.xml_other1 = assoc.getServiceCode();
        List dxcodes = assoc.getDxCodes();
        for (int i = 0; i < dxcodes.size(); i++) {
            if (i == 0) {
                this.xml_diagnostic_detail1 = (String) dxcodes.get(i);
            } else if (i == 1) {
                this.xml_diagnostic_detail2 = (String) dxcodes.get(i);
            } else if (i == 2) {
                this.xml_diagnostic_detail3 = (String) dxcodes.get(i);
            }
        }
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
