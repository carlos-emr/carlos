/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 * Modifications made by CARLOS Contributors in 2026: preserve imported clinical uncertainty.
 */

package io.github.carlos_emr.carlos.webserv.rest.conversion;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationCompatibilityException;
import io.github.carlos_emr.carlos.managers.DrugLookUp;
import io.github.carlos_emr.carlos.utility.DoseText;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts between domain Drug object and transfer Drug objects.
 * <p>
 * This class represents the transformation between a the SQL schema
 * and the data model that is presented to a client.
 */
@Component
public class DrugConverterImpl extends AbstractConverter<Drug, DrugTo1> implements DrugConverter {
    private static Logger logger = MiscUtils.getLogger();

    @Autowired
    protected DrugLookUp drugLookUpManager;

    /**
     * The unit after the first strength in stored strength text: any blanks, then one run of text
     * that starts with a letter or a percent sign. It ends at the blank before the next
     * ingredient, so a unit of concentration ("mg/mL", "mg/5ml") stays whole.
     */
    private static final Pattern UNIT_AFTER_NUMBER = Pattern.compile("\\s*+([\\p{L}%]\\S*+)");

    /** A separator between ingredients left at the end of the first one's unit: "mg/" or "mg,". */
    private static final Pattern TRAILING_SEPARATOR = Pattern.compile("[/,;]++$");

    /**
     * Converts from a transfer object to a Drug domain object.
     *
     * @param loggedInInfo information regarding the current logged in user.
     * @param t            the transfer object to copy the data from
     * @return a Drug domain object representing this data.
     * @throws ConversionException if conversion did not complete properly.
     */
    @Override
    public Drug getAsDomainObject(LoggedInInfo loggedInInfo, DrugTo1 t) throws ConversionException {

        Drug d = new Drug();

        // Copy fields from DrugTo1 object
        // over to the new Drug object.
        // This is not quite a one-to-one copy, some transformation
        // is done on types.

        try {

            d.setId(t.getDrugId());
            d.setRowVersion(t.getRowVersion());
            d.setBrandName(t.getBrandName());
            d.setGenericName(t.getGenericName());
            d.setCustomName(t.getCustomName());
            d.setDemographicId(t.getDemographicNo());
            if (t.getProviderNo() == null) {
                d.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            } else {
                d.setProviderNo(t.getProviderNo()); // Cast to string.
            }
            d.setAtc(t.getAtc());
            d.setRegionalIdentifier(t.getRegionalIdentifier());
            d.setDosage(strengthText(t.getStrength(), t.getStrengthUnit(), ""));
            d.setTakeMax(t.getTakeMax());
            d.setTakeMin(t.getTakeMin());
            d.setRxDate(t.getRxDate());
            d.setEndDate(t.getEndDate());
            d.setWrittenDate(t.getWrittenDate());
            d.setFreqCode(t.getFrequency());
            d.setDuration(convertToStringOrNull(t.getDuration())); // Cast to string.
            d.setDurUnit(t.getDurationUnit());
            d.setRepeat(t.getRepeats());
            d.setSpecial(t.getInstructions());
            d.setArchived(t.isArchived());
            d.setArchivedReason(t.getArchivedReason());
            d.setArchivedDate(t.getArchivedDate());
            d.setRoute(t.getRoute());
            d.setDrugForm(t.getForm());
            d.setMethod(t.getMethod());
            d.setPrn(t.isPrn());
            d.setLongTerm(t.getLongTerm());
            d.setNoSubs(t.getNoSubstitutions());
            d.setPosition(1);
            d.setOutsideProviderName(t.getExternalProvider());
            d.setSpecialInstruction(t.getAdditionalInstructions());

            d.setDispenseInterval(t.getDispenseIntervalNumber());
            d.setRefillDuration(zeroIfNull(t.getRefillDuration()));
            d.setRefillQuantity(zeroIfNull(t.getRefillQuantity()));

            if (t.getQuantity() != null) {
                d.setQuantity(t.getQuantity().toString());
            }

            populateDrugStrength(d, t);

        } catch (RuntimeException re) {
            logger.error("Runtime EX", re);
            throw new ConversionException();

        }

        return d;

    }

    private String convertToStringOrNull(Integer integer) {
        try {
            String ret = integer.toString();
            return ret;
        } catch (Exception e) {
            logger.debug("value was not parseable " + integer);
        }
        return null;
    }

    private Integer convertToIntegerOrNull(String integer) {
        try {
            Integer ret = Integer.parseInt(integer);
            return ret;
        } catch (Exception e) {
            logger.debug("value was not parseable " + integer);
        }
        return null;
    }

    private boolean falseIfNull(Boolean b) {
        if (b == null) {
            return false;
        }
        return b.booleanValue();
    }

    private int zeroIfNull(Integer i) {
        if (i == null) {
            return 0;
        }
        return i.intValue();
    }

    /**
     * Converts from the Drug domain model object to a serializable Drug transfer
     * object.
     *
     * @param loggedInInfo information for the logged in user (unused).
     * @param d            the Drug domain object to convert from.
     * @return a serializable transfer object that represents the Drug object
     * @throws ConversionException if the conversion fails.
     */
    @Override
    public DrugTo1 getAsTransferObject(LoggedInInfo loggedInInfo, Drug d) throws ConversionException {

        // This Date/float-based protocol cannot carry partial dates, source dose text,
        // unknown authority/substitution or independent imported lifecycle states.
        CdsMedicationCompatibilityException.requireLegacyCompatible(d);
        DrugTo1 t = new DrugTo1();

        // Copy over the fields from the Drug to the new transfer object.
        // This is not a one-to-one mapping, some transformation is
        // done on types.

        t.setDrugId(d.getId());
        t.setBrandName(d.getBrandName());
        t.setGenericName(d.getGenericName());
        t.setCustomName(d.getCustomName());
        t.setAtc(d.getAtc());
        t.setRegionalIdentifier(d.getRegionalIdentifier());
        t.setDemographicNo(d.getDemographicId());
        t.setProviderNo(d.getProviderNo());
        t.setTakeMin(d.getTakeMin());
        t.setTakeMax(d.getTakeMax());
        t.setRxDate(d.getRxDate());
        t.setEndDate(d.getEndDate());
        t.setWrittenDate(d.getWrittenDate());
        t.setFrequency(d.getFreqCode());
        t.setDuration(convertToIntegerOrNull(d.getDuration())); // Parse the duration string to an int.
        t.setDurationUnit(d.getDurUnit());
        t.setRoute(d.getRoute());
        t.setForm(d.getDrugForm());
        t.setPrn(d.isPrn());
        t.setMethod(d.getMethod());
        t.setRepeats(d.getRepeat());
        t.setInstructions(d.getSpecial());
        t.setPrn(d.isPrn());
        t.setNoSubstitutions(d.isNoSubs());
        t.setLongTerm(d.isLongTerm());
        t.setArchived(d.isArchived());
        t.setArchivedDate(d.getArchivedDate());
        t.setArchivedReason(d.getArchivedReason());
        t.setExternalProvider(d.getOutsideProviderName());
        t.setAdditionalInstructions(d.getSpecialInstruction());

        // A quantity the Integer field cannot carry ("2.5", of 2.5 mL) is left out: parsing it
        // threw and failed the read of the patient's whole drug list (#6185). The instruction text
        // still states it, and an update that states no quantity keeps the stored one
        // (RxManagerImpl.updateDrug).
        t.setQuantity(Drug.wholeQuantity(d.getQuantity()));

        this.populateStrengthFromDosage(d, t);

        return t;
    }

    /**
     * Reads {@code strength} and {@code strengthUnit} back from the stored strength text: "5 mg",
     * "250mg", "2.5 mg/mL", a number with no unit, or the first ingredient of "5 mg / 20 mg".
     *
     * <p>Only the first number in the text is read, whole ({@link DoseText#numberEnd}): "2.5" is
     * 2.5, and "1.0E7 unit", which this service stored for ten million, is ten million. When that
     * number goes on after a comma, a slash or a hyphen it is not one plain decimal: grouped
     * digits ("1,000 mg"), a decimal comma ("2,5 mg"), a fraction ("1/2 mg"), a combination
     * written together ("5/20 mg") or a range ("5-10 mg"). Its first digits are not the strength
     * (1 is not 1,000) and the single strength field cannot carry the rest, so no strength is
     * reported. The search does not go on to a later number, which belongs to another ingredient.
     *
     * <p>The old pattern needed two digits, which went unnoticed while every strength was stored
     * with a trailing zero: "5 mg" had no strength, and "5 mg / 20 mg" reported the 20 (#6185).
     *
     * @return true when a strength was read
     */
    protected Boolean populateStrengthFromDosage(Drug d, DrugTo1 t) {
        if (t.getStrength() != null || (t.getStrengthUnit() != null && !t.getStrengthUnit().isEmpty())) {
            return false;
        }
        String dosage = d.getDosage();
        // A drug saved without a strength holds no text; that is not an error to log.
        if (dosage == null) {
            return false;
        }
        int start = firstNumberStart(dosage);
        if (start < 0) {
            return false;
        }
        int end = DoseText.numberEnd(dosage, start);
        if (continuesPastSeparator(dosage, end)) {
            return false;
        }
        float strength = Float.parseFloat(dosage.substring(start, end));
        if (!Float.isFinite(strength)) {
            return false;
        }
        t.setStrength(strength);
        Matcher unit = UNIT_AFTER_NUMBER.matcher(dosage).region(end, dosage.length());
        if (unit.lookingAt()) {
            // "5 mg / 20 mg" and "5 mg, 20 mg": the separator after the first unit is not part of it.
            // The unit starts with a letter or "%", so something always remains.
            t.setStrengthUnit(TRAILING_SEPARATOR.matcher(unit.group(1)).replaceFirst(""));
        }
        return true;
    }

    /** The index at which the first number in the text starts, or -1 when it holds none. */
    private static int firstNumberStart(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (DoseText.numberEnd(text, index) > index) {
                return index;
            }
        }
        return -1;
    }

    /** Whether the number that ends at {@code end} goes on: a comma, slash or hyphen, then a digit. */
    private static boolean continuesPastSeparator(String text, int end) {
        return end + 1 < text.length()
                && ",/-".indexOf(text.charAt(end)) >= 0
                && Character.isDigit(text.charAt(end + 1));
    }

    /**
     * Attempts to populate the strength and strength unit fields of the transfer
     * object
     * based on the info in the drug object. Will not overwrite the strength fields
     * if they
     * are already set.
     *
     * @param t a transfer object to populated.
     * @param d a drug object to use as a reference.
     * @return true if populated successfully, false otherwise.
     */
    protected Boolean populateTo1Strength(DrugTo1 t, Drug d) {

        if (t.getStrength() != null || (t.getStrengthUnit() != null && !t.getStrengthUnit().isEmpty())) {
            // check that the strength is not already set.
            return false;
        }

        if (d.getBrandName() == null || d.getBrandName().isEmpty())
            return false;

        try {

            List<DrugSearchTo1> matchedDrugs = this.drugLookUpManager.search(d.getBrandName());
            DrugSearchTo1 details = null;

            if (matchedDrugs.size() >= 1) {

                // get the details for the drug in question
                details = this.drugLookUpManager.details(matchedDrugs.get(0).getId().toString());

                if (details != null) {
                    t.setStrength(details.getComponents().get(0).getStrength().floatValue());
                    t.setStrengthUnit(details.getComponents().get(0).getUnit());
                    return true;
                } else {
                    return false;
                }

            } else {
                // if we did not find a matching drug ignore, not strength populated.
                return false;
            }
        } catch (Exception e) {
            return false;
        }

    }

    /**
     * The strength text stored with a drug ("500 mg"): the number as {@link DoseText} writes it,
     * then the unit.
     *
     * <p>Concatenating the {@code Float} stored "500.0 mg" and "1.0E7 unit", and stored the word
     * "null" for a part the client did not send (#6185). A missing part is left out.
     *
     * @param strength the strength, or {@code null} when it is not known
     * @param unit the strength unit, or {@code null} when it is not known
     * @param separator the text between the two when both are known
     * @return the strength text; empty when neither part is known
     */
    private static String strengthText(Float strength, String unit, String separator) {
        String amount = strength == null ? "" : DoseText.of(strength.floatValue());
        String unitText = unit == null ? "" : unit;
        if (amount.isEmpty() || unitText.isEmpty()) {
            return amount + unitText;
        }
        return amount + separator + unitText;
    }

    /**
     * Attempts to populate the dosage field of the drug object given a transfer
     * object.
     *
     * @param d the drug to populate
     * @param t the transfer object to use as a reference.
     * @return true if successful, false otherwise.
     */
    protected boolean populateDrugStrength(Drug d, DrugTo1 t) {

        if (t.getStrengthUnit() != null && t.getStrength() != null) {

            d.setDosage(strengthText(t.getStrength(), t.getStrengthUnit(), " "));
            d.setUnit(t.getStrengthUnit());
            return true;

        } else {

            // if there is no strength we have to look it up in drug ref
            // we use the
            if (this.populateTo1Strength(t, d)) {

                d.setDosage(strengthText(t.getStrength(), t.getStrengthUnit(), " "));
                d.setUnit(t.getStrengthUnit());
                return true;

            } else {

                return false;

            }

        }
    }

}
