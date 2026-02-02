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
package ca.openosp.openo.integration.mchcv;

import ca.ontario.health.ebs.EbsFault;
import ca.ontario.health.hcv.FeeServiceDetails;
import ca.ontario.health.hcv.ResponseID;
import org.springframework.beans.BeanUtils;
import ca.openosp.openo.utility.MiscUtils;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HCValidationResult {

    private String auditUID;
    private String responseCode;
    private String responseDescription;
    private String responseAction;
    private ResponseID responseID;
    private String healthNumber;
    private String versionCode;
    private String firstName;
    private String secondName;
    private String lastName;
    private String birthDate;
    private String gender;
    private String expiryDate;
    private String issueDate;
    private EbsFault ebsFault;
    private List<FeeServiceDetails> feeServiceDetails;

    public HCValidationResult() {
    }

    public boolean isValid() {

        if (responseCode == null) {
            return false;
        }
        int response = Integer.parseInt(responseCode);
        return (response >= 50) && (response <= 59);
    }

    /**
     * A two character representation of the validation response code for given health number and/or version code
     * Response code is mandatory field and is returned for each validation request submitted.
     */
    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String value) {
        responseCode = value;
    }

    /**
     * A description for the validation response code for given health number and/or version code.
     * Response description is optional field and can be returned for each validation request submitted.
     */
    public String getResponseDescription() {
        return responseDescription;
    }

    public void setResponseDescription(String value) {
        this.responseDescription = value;
    }

    /**
     * The action required of the caller for the returned response code.
     * Response action is optional field and can be returned for each validation request submitted.
     */
    public String getResponseAction() {
        return responseAction;
    }

    public void setResponseAction(String value) {
        this.responseAction = value;
    }

    /**
     * MOHLTC stores this value as upper case characters.
     * A maximum of 20 characters are kept on card.
     * No accents or other diacritic marks are stored or returned.
     */
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String value) {
        this.firstName = value;
    }

    public String getSecondName() {
        return secondName;
    }

    public void setSecondName(String secondName) {
        this.secondName = secondName;
    }

    /**
     * MOHLTC stores this value as upper case characters.
     * A maximum of 30 characters are kept on card.
     * No accents or other diacritic marks are stored or returned.
     */
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String value) {
        this.lastName = value;
    }

    /**
     * The card holder’s date of birth.
     */
    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String value) {
        this.birthDate = value;
    }

    /**
     * The gender is returned as either an M or F, for male or female respectively.
     */
    public String getGender() {
        return gender;
    }

    public void setGender(String value) {
        this.gender = value;
    }

    /**
     * The date the card expires.
     */
    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String value) {
        this.expiryDate = value;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String value) {
        this.issueDate = value;
    }

    public ResponseID getResponseID() {
        return responseID;
    }

    public void setResponseID(ResponseID responseID) {
        this.responseID = responseID;
    }

    public String getAuditUID() {
        return auditUID;
    }

    public void setAuditUID(String auditUID) {
        this.auditUID = auditUID;
    }

    public String getHealthNumber() {
        return healthNumber;
    }

    public void setHealthNumber(String healthNumber) {
        this.healthNumber = healthNumber;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    public EbsFault getEbsFault() {
        return ebsFault;
    }

    public void setEbsFault(EbsFault ebsFault) {
        this.ebsFault = ebsFault;
    }

    public String getEbsFaultCode() {
        if (ebsFault == null) {
            return null;
        }
        return ebsFault.getCode();
    }

    public String getEbsFaultDescription() {
        if (ebsFault == null) {
            return null;
        }
        return ebsFault.getMessage();
    }

    public List<FeeServiceDetails> getFeeServiceDetails() {
        if (feeServiceDetails == null) {
            return Collections.emptyList();
        }
        return feeServiceDetails;
    }

    public void setFeeServiceDetails(List<FeeServiceDetails> feeServiceDetails) {
        this.feeServiceDetails = feeServiceDetails;
    }

    public String getFeeServiceResponseCode(String feeServiceCode) {
        return getFeeServiceDetailsbyFeeServiceCode(feeServiceCode).getFeeServiceResponseCode();
    }

    public String getFeeServiceResponseDescription(String feeServiceCode) {
        return getFeeServiceDetailsbyFeeServiceCode(feeServiceCode).getFeeServiceResponseDescription();
    }

    public Date getFeeServiceDate(String feeServiceCode) {
        XMLGregorianCalendar date = getFeeServiceDetailsbyFeeServiceCode(feeServiceCode).getFeeServiceDate();
        if (date != null) {
            return date.toGregorianCalendar().getTime();
        }
        return null;
    }

    private FeeServiceDetails getFeeServiceDetailsbyFeeServiceCode(String feeServiceCode) {
        FeeServiceDetails selectedFeeServiceDetails = new FeeServiceDetails();
        selectedFeeServiceDetails.setFeeServiceCode(feeServiceCode);
        for (FeeServiceDetails feeServiceDetail : this.feeServiceDetails) {
            if (feeServiceCode.equalsIgnoreCase(feeServiceDetail.getFeeServiceCode())) {
                selectedFeeServiceDetails = feeServiceDetail;
                try {
                    BeanUtils.copyProperties(feeServiceDetail, selectedFeeServiceDetails);
                } catch (Exception e) {
                    MiscUtils.getLogger().warn("Bean Copy error. Fee Service code: " + feeServiceCode, e);
                }
                break;
            }
        }
        return selectedFeeServiceDetails;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("")
                .append("   Response Audit UID: " + auditUID + "\n")
                .append("   Response Action: " + responseAction + "\n")
                .append("   Response Code: " + responseCode + "\n")
                .append("   Response Description: " + responseDescription + "\n")
                .append("   Response ID: " + responseID + "\n")
                .append("   Health Number: " + healthNumber + "\n")
                .append("   Version Code: " + versionCode + "\n")
                .append("   First Name: " + firstName + "\n")
                .append("   Second Name: " + secondName + "\n")
                .append("   Last Name: " + lastName + "\n")
                .append("   Gender: " + gender + "\n")
                .append("   Birth Date: " + birthDate + "\n")
                .append("   Expiry Date: " + expiryDate + "\n")
                .append("   Issue Date: " + issueDate + "\n")
                .append("   Fault: " + getEbsFaultCode() + " : " + getEbsFaultDescription() + "\n");

        for (FeeServiceDetails feeServiceDetails : getFeeServiceDetails()) {

            stringBuilder.append("   Fee Service Code: " + feeServiceDetails.getFeeServiceCode() + "\n")
                    .append("   Fee Service Response Code: " + feeServiceDetails.getFeeServiceResponseCode() + "\n")
                    .append("   Fee Service Response Description: " + feeServiceDetails.getFeeServiceResponseDescription() + "\n")
                    .append("   Fee Service Date: " + feeServiceDetails.getFeeServiceDate() + "\n");

        }

        return stringBuilder.toString();
    }
}
