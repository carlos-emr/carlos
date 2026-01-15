//CHECKSTYLE:OFF
/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 */

package ca.openosp.openo.form.pharmaForms.formBPMH.bean;

import org.apache.commons.lang3.builder.ToStringBuilder;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.DemographicCust;
import ca.openosp.openo.commn.model.Provider;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/*
 * Author: Dennis Warren
 * Company: Colcamex Resources
 * Date: November 2014
 * For: UBC Pharmacy Clinic and McMaster Department of Family Medicine
 */

@XmlRootElement(name = "xml")
@XmlAccessorType(XmlAccessType.FIELD)
public class BpmhForm2Bean implements Serializable {

    private final SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy");

    private Date formDate;
    private Date editDate;
    private String formId;
    private boolean confirm;
    private String note;
    private String demographicNo;
    private String familyDrContactId;
    private String familyDrName;
    private String familyDrPhone;
    private String familyDrFax;
    private String allergiesString;

    // nested beans
    private Demographic demographic;
    private DemographicCust demographicCust;
    private Provider provider;

    // lazy collections
    private List<Allergy> allergies;
    private List<BpmhDrug> drugs;

    public String getFormDateFormatted() {
        String date = "";

        if (formDate != null) {
            date = formatter.format(formDate);
        }

        return date;
    }

    public Date getFormDate() {
        return this.formDate;
    }

    public void setFormDate(Date formDate) {
        this.formDate = formDate;
    }

    public String getEditDateFormatted() {
        String date = "";

        if (editDate != null) {
            date = formatter.format(editDate);
        }

        return date;
    }

    public Date getEditDate() {
        return editDate;
    }

    public void setEditDate(Date editDate) {
        this.editDate = editDate;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
    }

    public String getNote() {
        if (note == null) {
            return "";
        }
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getDemographicNo() {
        if (demographicNo == null) {
            return "";
        }
        return demographicNo;
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    /*
     * Contact ID is the row ID found in the DemographicContacts table.
     */
    public String getFamilyDrContactId() {
        return familyDrContactId;
    }

    /*
     * Contact ID is the row ID found in the DemographicContacts table.
     */
    public void setFamilyDrContactId(String familyDrContactId) {
        this.familyDrContactId = familyDrContactId;
    }

    public String getFamilyDrName() {
        return familyDrName;
    }

    public void setFamilyDrName(String familyDrName) {
        this.familyDrName = familyDrName;
    }

    public String getFamilyDrPhone() {
        return familyDrPhone;
    }

    public void setFamilyDrPhone(String familyDrPhone) {
        this.familyDrPhone = familyDrPhone;
    }

    public String getFamilyDrFax() {
        return familyDrFax;
    }

    public void setFamilyDrFax(String familyDrFax) {
        this.familyDrFax = familyDrFax;
    }

    public Demographic getDemographic() {
        return demographic;
    }

    public void setDemographic(Demographic demographic) {
        this.demographic = demographic;
    }

    public DemographicCust getDemographicCust() {
        return demographicCust;
    }

    public void setDemographicCust(DemographicCust demographicCust) {
        this.demographicCust = demographicCust;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getAllergiesString() {
        return allergiesString;
    }

    public void setAllergiesString(String allergiesString) {
        this.allergiesString = allergiesString;
    }

    public List<Allergy> getAllergies() {
        return allergies;
    }

    public void setAllergies(List<Allergy> allergies) {
        this.allergies = allergies;

        if (this.allergies != null) {

            StringBuilder stringBuilder = new StringBuilder("");
            Allergy allergy;
            String reaction;
            // concat description and reaction only.
            for (int i = 0; i < this.allergies.size(); i++) {
                allergy = this.allergies.get(i);
                reaction = allergy.getReaction();

                stringBuilder.append((i + 1) + ". ");
                stringBuilder.append(allergy.getDescription() + " ");
                if (reaction != null) {
                    stringBuilder.append(", RX: " + reaction + " ");
                }
            }

            setAllergiesString(stringBuilder.toString());
        }
    }

    public BpmhDrug getDrug(int index) {
        return getDrugs().get(index);
    }

    public List<BpmhDrug> getDrugs() {
        return drugs;
    }

    public void setDrugs(final List<BpmhDrug> drugs) {
        this.drugs = drugs;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
