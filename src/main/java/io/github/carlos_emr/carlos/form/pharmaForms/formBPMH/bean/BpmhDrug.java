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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;

/*
 * Author: Dennis Warren
 * Company: Colcamex Resources
 * Date: November 2014
 * For: UBC Pharmacy Clinic and McMaster Department of Family Medicine
 *
 * Defines medication data for:
 * 	WHAT I TAKE : Generic name / dose / delivery
 * 	HOW I TAKE IT : Once per day with food / bid, pid etc...
 * 	WHY I TAKE IT : ICD9 code / special reasons
 *  SPECIAL INSTRUCTIONS : additional instructions
 *
 */
public class BpmhDrug {

    private String id;
    private String demographicId;
    private String genericName;
    private String atc;
    private String customName;
    private String takeMin;
    private String takeMax;
    private String freqCode;
    private String duration;
    private String durUnit;
    private String quantity;
    private String repeat;
    private String prn;
    private String special;
    private String unit;
    private String method;
    private String drugForm;
    private String route;
    private String dosage;
    private String position;
    private String customInstructions;
    private String special_instruction;
    private String outsideProviderName;
    private String outsideProviderOhip;
    private String customNote;
    private String comment; // used as instructions in the BPMH. saved in drugs.comment
    // drug reason - why I take it - drugReason table
    private String codingSystem;
    private String code;
    private String comments; // used as Why I Take it custom comments. saved in drugReason.comments
    // dx interpret - diagnosticcode table

    private String what;
    private String how;
    private String why;
    private String instruction;

    /*
     * Treats what disease or reason?
     */
    public String getWhy() {
        if (this.why == null) {
            return "";
        }
        return this.why;
    }

    public void setWhy(String why) {
        this.why = why;
    }

    /*
     * Prescription details. Take once per day with food
     */
    // Compares recorded form and unit labels for display only. The displayed unit retains its source spelling.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Compares recorded form and unit labels for display only. The displayed unit retains its source spelling.")
    public String getHow() {
        if (how == null) {
            usePrescriptionDirections(null, getSpecial_instruction(),
                    (!getCustomName().isBlank() && getDosage().isBlank()) || getUnit().equalsIgnoreCase(getDrugForm()) ? getUnit() : null);
        }
        return how;
    }

    /**
     * Sets how to the saved administration directions or to a dose assembled from recorded fields.
     *
     * @param brandName String holding the recorded product name used to recognize its heading
     * @param pharmacyInstruction String holding the recorded dispensing instruction
     * @param doseUnit String identifying the administered dose unit, or null when only the product strength is known
     */
    public void usePrescriptionDirections(String brandName, String pharmacyInstruction, String doseUnit) {
        String text = StringUtils.defaultString(getSpecial()).replace("\r\n", "\n").replace('\r', '\n');
        int lineBreak = text.indexOf('\n');
        String first = lineBreak < 0 ? text : text.substring(0, lineBreak);
        boolean hasHeading = isMedicationHeading(first, brandName) || isMedicationHeading(first, getCustomName())
                || isMedicationHeading(first, getGenericName());
        if (hasHeading) {
            text = lineBreak < 0 ? "" : text.substring(lineBreak + 1);
        }
        String withoutTrailingSpace = text.stripTrailing();
        int lastBreak = withoutTrailingSpace.lastIndexOf('\n');
        String last = withoutTrailingSpace.substring(lastBreak + 1);
        if ((last.startsWith("Qty:") || last.startsWith("Mitte:")) && last.contains(" Repeats:")) {
            text = lastBreak < 0 ? "" : text.substring(0, lastBreak);
        }
        if (hasHeading && StringUtils.isNotBlank(pharmacyInstruction)) {
            String pharmacy = pharmacyInstruction.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
            String body = text.stripTrailing();
            String suffix = "\n" + pharmacy;
            if (body.equals(pharmacy)) text = "";
            else if (body.endsWith(suffix)) text = body.substring(0, body.length() - suffix.length());
        }
        if (!text.isBlank()) {
            how = text;
            return;
        }
        // An empty how activates the form's existing missing-information indicator.
        if (getTakeMin().isBlank() || StringUtils.isBlank(doseUnit)) {
            how = "";
            return;
        }
        String amount = getTakeMin();
        if (!getTakeMax().isBlank() && !"0".equals(getTakeMax()) && !getTakeMax().equals(amount)) {
            amount += " to " + getTakeMax();
        }
        how = String.join(" ", java.util.stream.Stream.of(getMethod(), amount, doseUnit, getFreqCode(), getRoute(),
                        Boolean.parseBoolean(getPrn()) ? "as needed" : "")
                .filter(StringUtils::isNotBlank).toList());
    }

    // Possessive digit runs, a single optional space and a fixed unit vocabulary bound the match to linear time.
    @SuppressFBWarnings(value = "REDOS", justification = "Possessive digit runs, a single optional space and a fixed unit vocabulary bound the match to linear time.")
    private boolean isMedicationHeading(String first, String name) {
        if (StringUtils.isBlank(name)) return false;
        String heading = first.strip();
        if (heading.equals(name)) return true;
        if (!heading.startsWith(name + " ")) return false;
        String suffix = heading.substring(name.length() + 1);
        return suffix.equals(getDosage()) || suffix.equals(getDosage().strip() + " " + getUnit().strip())
                || suffix.matches("[0-9]++(?:\\.[0-9]++)?+ ?(?:mg|mcg|g|mL|%)");
    }

    public void setHow(String how) {
        this.how = how;
    }

    /*
     * Drug Generic name, Strength, Dose/Delivery
     */
    public String getWhat() {

        if (this.what != null) {
            return this.what;
        }

        StringBuilder stringBuilder = new StringBuilder("");
        String drugName = "";
        String unit = "";

        if (!getGenericName().isEmpty()) {

            stringBuilder.append(getGenericName() + " ");
            drugName = getGenericName();

        } else if (!getCustomName().isEmpty()) { // if generic is null then check for custom

            stringBuilder.append(getCustomName() + " ");
            drugName = getCustomName();
        }


        if (!drugName.contains(getDosage())) {
            stringBuilder.append(getDosage() + " ");
        }

        if (!getUnit().isEmpty()) {
            unit = getUnit();
        }

        if (!stringBuilder.toString().contains(unit)) {
            stringBuilder.append(unit + " ");
        }

        if (!getDrugForm().isEmpty()) {
            stringBuilder.append(getDrugForm());
        }

        setWhat(stringBuilder.toString());

        return what;
    }

    public void setWhat(String what) {
        this.what = what;
    }

    /*
     * Additional special instructions
     *
     */
    public String getInstruction() {
        return this.instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }


    // -------------------------------JPA POJO------------------------------------- //

    public String getTakeMin() {
        if (this.takeMin == null) {
            return "";
        }
        return takeMin;
    }

    public void setTakeMin(String takeMin) {
        this.takeMin = takeMin;
    }

    public String getTakeMax() {
        if (this.takeMax == null) {
            return "";
        }
        return takeMax;
    }

    public void setTakeMax(String takeMax) {
        this.takeMax = takeMax;
    }

    public String getFreqCode() {
        if (this.freqCode == null) {
            return "";
        }
        return freqCode;
    }

    public void setFreqCode(String freqCode) {
        this.freqCode = freqCode;
    }

    public String getDurUnit() {
        return durUnit;
    }

    public void setDurUnit(String durUnit) {
        this.durUnit = durUnit;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getRepeat() {
        return repeat;
    }

    public void setRepeat(String repeat) {
        this.repeat = repeat;
    }

    public String getPrn() {
        return prn;
    }

    public void setPrn(String prn) {
        this.prn = prn;
    }

    public String getSpecial() {
        return special;
    }

    public void setSpecial(String special) {
        this.special = special;
    }

    public String getUnit() {
        if (unit == null) {
            return "";
        }
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getMethod() {
        if (this.method == null) {
            return "";
        }
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRoute() {
        if (this.route == null) {
            return "";
        }
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getDosage() {
        if (dosage == null) {
            return "";
        }
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getCodingSystem() {
        return codingSystem;
    }

    public void setCodingSystem(String codingSystem) {
        this.codingSystem = codingSystem;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getSpecial_instruction() {
        if (this.special_instruction == null) {
            return "";
        }
        return special_instruction;
    }

    public void setSpecial_instruction(String special_instruction) {
        this.special_instruction = special_instruction;
    }

    public String getGenericName() {
        if (genericName == null) {
            return "";
        }
        return genericName;
    }

    public void setGenericName(String genericName) {
        this.genericName = genericName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDemographicId() {
        return demographicId;
    }

    public void setDemographicId(String demographicId) {
        this.demographicId = demographicId;
    }

    public String getAtc() {
        return atc;
    }

    public void setAtc(String atc) {
        this.atc = atc;
    }

    public String getCustomName() {
        if (customName == null) {
            return "";
        }
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getDrugForm() {
        if (this.drugForm == null) {
            return "";
        }
        return drugForm;
    }

    public void setDrugForm(String drugForm) {
        this.drugForm = drugForm;
    }

    public String getCustomInstructions() {
        if (this.customInstructions == null) {
            return "";
        }
        return customInstructions;
    }

    public void setCustomInstructions(String customInstructions) {
        this.customInstructions = customInstructions;
    }

    public String getOutsideProviderName() {
        return outsideProviderName;
    }

    public void setOutsideProviderName(String outsideProviderName) {
        this.outsideProviderName = outsideProviderName;
    }

    public String getOutsideProviderOhip() {
        return outsideProviderOhip;
    }

    public void setOutsideProviderOhip(String outsideProviderOhip) {
        this.outsideProviderOhip = outsideProviderOhip;
    }

    public String getCustomNote() {
        return customNote;
    }

    public void setCustomNote(String customNote) {
        this.customNote = customNote;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[protected]";
    }

}
