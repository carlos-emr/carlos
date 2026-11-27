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
    private String patientReportedUse;
    private String reportedMedication;
    private String reviewedVersion;
    private boolean reviewChanged;

    @jakarta.xml.bind.annotation.XmlTransient
    public String getPatientReportedUse() {
        return patientReportedUse;
    }

    public void setPatientReportedUse(String patientReportedUse) {
        this.patientReportedUse = patientReportedUse;
    }

    @jakarta.xml.bind.annotation.XmlTransient
    public String getReportedMedication() {
        return reportedMedication;
    }

    public void setReportedMedication(String reportedMedication) {
        this.reportedMedication = reportedMedication;
    }

    @jakarta.xml.bind.annotation.XmlTransient
    public String getReviewedVersion() {
        return reviewedVersion;
    }

    public void setReviewedVersion(String reviewedVersion) {
        this.reviewedVersion = reviewedVersion;
    }

    @jakarta.xml.bind.annotation.XmlTransient
    public boolean isReviewChanged() {
        return reviewChanged;
    }

    public void setReviewChanged(boolean reviewChanged) {
        this.reviewChanged = reviewChanged;
    }

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
    public String getHow() {
        if (how == null) {
            usePrescriptionDirections(null, getSpecial_instruction(),
                    (!getCustomName().isBlank() && getDosage().isBlank())
                            || org.apache.commons.lang3.Strings.CI.equals(getUnit(), getDrugForm()) ? getUnit() : null,
                    isPrnFlagged());
        }
        return how;
    }

    /**
     * Populates {@code how} from the recorded prescription directions, stripping only recognized Rx envelope lines
     * (medication heading, generated numeric {@code Qty: ... Repeats: ...} trailer, and duplicate pharmacy instruction)
     * so authored multi-line and taper steps stay intact.
     *
     * <p>When no authored direction line remains, falls back to structured dosing from {@code takeMin}, {@code takeMax},
     * {@code method}, {@code freqCode}, and {@code route} only when both {@code takeMin} and {@code doseUnit} are
     * non-blank; otherwise sets {@code how} to {@code ""} so the BPMH form displays its missing-information indicator
     * (and callers with a recorded {@code cdsDoseText} may then invoke {@link #useRecordedDoseText(String, String, boolean)}).
     * Always replaces {@code how}.</p>
     *
     * @param brandName String brand name used to recognize a generated heading line, or {@code null}
     * @param pharmacyInstruction String separate pharmacy instruction that may be duplicated in {@code special}, or {@code null}
     * @param doseUnit String verified administered dose unit to allow structured fallback, or {@code null} when callers
     *     intend to defer to {@link #useRecordedDoseText(String, String, boolean)} or when no unambiguous dose unit is recorded
     */
    public void usePrescriptionDirections(String brandName, String pharmacyInstruction, String doseUnit) {
        usePrescriptionDirections(brandName, pharmacyInstruction, doseUnit, isPrnFlagged());
    }

    /**
     * Populates {@code how} from recorded prescription directions or structured fallback dosing, appending {@code PRN}
     * to structured fallback output when {@code prn} is {@code true} while leaving authored directions unchanged.
     *
     * <p>Callers should pass {@code doseUnit = null} when no unambiguous dose unit is known or when deferring empty
     * authored directions to the CDS fallback {@link #useRecordedDoseText(String, String, boolean)}. Always replaces
     * {@code how}.</p>
     *
     * @param brandName String brand name used to recognize a generated heading line, or {@code null}
     * @param pharmacyInstruction String separate pharmacy instruction that may be duplicated in {@code special}, or {@code null}
     * @param doseUnit String verified administered dose unit for structured fallback, or {@code null}
     * @param prn boolean {@code true} if the source prescription is marked PRN (as needed)
     */
    public void usePrescriptionDirections(String brandName, String pharmacyInstruction, String doseUnit, boolean prn) {
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
        if (isDispensingTrailer(last)) {
            text = lastBreak < 0 ? "" : text.substring(0, lastBreak);
        }
        if (hasHeading && StringUtils.isNotBlank(pharmacyInstruction)) {
            String pharmacy = pharmacyInstruction.replace("\r\n", "\n").replace('\r', '\n');
            text = text.equals(pharmacy) ? "" : StringUtils.removeEnd(text, "\n" + pharmacy);
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
        setStructuredDirections(amount + " " + doseUnit, prn);
    }

    /**
     * Populates {@code how} from a CDS-recorded dose string when authored directions were blank.
     *
     * <p>Appends {@code doseUnit} only when {@code doseUnit} is non-blank and {@code dose} contains no letters
     * (meaning it does not already carry its own unit). Always replaces {@code how}.</p>
     *
     * @param dose String recorded CDS dose text (must be non-null)
     * @param doseUnit String verified administered dose unit to append when {@code dose} is unitless, or {@code null}
     */
    public void useRecordedDoseText(String dose, String doseUnit) {
        useRecordedDoseText(dose, doseUnit, isPrnFlagged());
    }

    /**
     * Populates {@code how} from a CDS-recorded dose string when authored directions were blank, optionally appending
     * {@code PRN} when the prescription is flagged as needed. Always replaces {@code how}.
     *
     * @param dose String recorded CDS dose text (must be non-null)
     * @param doseUnit String verified administered dose unit to append when {@code dose} is unitless, or {@code null}
     * @param prn boolean {@code true} if the source prescription is marked PRN (as needed)
     */
    public void useRecordedDoseText(String dose, String doseUnit, boolean prn) {
        String recorded = StringUtils.isNotBlank(doseUnit) && dose.codePoints().noneMatch(Character::isLetter)
                ? dose + " " + doseUnit : dose;
        setStructuredDirections(recorded, prn);
    }

    private boolean isPrnFlagged() {
        String value = StringUtils.trimToEmpty(getPrn());
        return Boolean.parseBoolean(value) || org.apache.commons.lang3.Strings.CI.equals("PRN", value);
    }

    private void setStructuredDirections(String amount, boolean prn) {
        boolean appendPrn = prn && !org.apache.commons.lang3.Strings.CI.equals("PRN", StringUtils.trimToEmpty(getFreqCode()));
        how = String.join(" ", java.util.stream.Stream.of(getMethod(), amount, getFreqCode(), getRoute(),
                        appendPrn ? "PRN" : null)
                .filter(StringUtils::isNotBlank).toList());
    }

    private static boolean isDispensingTrailer(String line) {
        if (!line.startsWith("Qty:")) return false;
        int repeatsIndex = line.lastIndexOf(" Repeats:");
        if (repeatsIndex < 4) return false;
        String qtyPart = line.substring(4, repeatsIndex).strip();
        String repeatsPart = line.substring(repeatsIndex + " Repeats:".length()).strip();
        return startsWithPlainDecimal(qtyPart) && isPlainDecimal(repeatsPart);
    }

    private static boolean startsWithPlainDecimal(String value) {
        int end = plainDecimalEnd(value);
        return end > 0 && (end == value.length() || Character.isWhitespace(value.charAt(end)));
    }

    private static boolean isPlainDecimal(String value) {
        return !value.isEmpty() && plainDecimalEnd(value) == value.length();
    }

    private static int plainDecimalEnd(String value) {
        int end = 0;
        while (end < value.length() && value.charAt(end) >= '0' && value.charAt(end) <= '9') {
            end++;
        }
        if (end < value.length() && value.charAt(end) == '.'
                && end + 1 < value.length() && value.charAt(end + 1) >= '0' && value.charAt(end + 1) <= '9') {
            end++;
            while (end < value.length() && value.charAt(end) >= '0' && value.charAt(end) <= '9') {
                end++;
            }
        }
        return end;
    }

    // FindSecBugs REDOS: possessive digit runs, a single optional space, and a fixed unit vocabulary bound the heading match to linear time. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "REDOS", justification = "Possessive digit runs, a single optional space and a fixed unit vocabulary bound the match to linear time.")
    private boolean isMedicationHeading(String first, String name) {
        if (StringUtils.isBlank(name)) return false;
        String heading = first.strip();
        if (heading.equals(name)) return true;
        if (!heading.startsWith(name + " ")) return false;
        String suffix = heading.substring(name.length() + 1);
        return suffix.equals(getDosage()) || suffix.matches("[0-9]++(?:\\.[0-9]++)?+ ?(?:mg|mcg|g|mL|%)");
    }

    public void setHow(String how) {
        this.how = how;
    }

    /*
     * Drug Generic name, Strength, Dose/Delivery
     */
    public String getWhat() {
        if (StringUtils.isNotBlank(reportedMedication)) {
            return reportedMedication;
        }

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
