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


package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

import java.math.BigDecimal;

public class RptCheckGuideline {

    /** Numeric binding prevents VARCHAR readings from being compared lexicographically. */
    static BigDecimal numericValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }



    public RptCheckGuideline() {
    }

    /*****************************************************************************************
     * Check the measurementType is a numeric value
     *
     * @return 0 when it is false, 1 otherwise
     ******************************************************************************************/
    public int getValidation(String measurementType) {
        MeasurementTypeDao dao = SpringUtils.getBean(MeasurementTypeDao.class);
        ValidationsDao vDao = SpringUtils.getBean(ValidationsDao.class);
        for (MeasurementType mt : dao.findByType(measurementType)) {
            String validation = mt.getValidation();

            Validations v = vDao.find(ConversionUtils.fromIntString(validation));
            // isNumeric is NULL on non-numeric rules (Yes/No/NA, Provided/Revised/Reviewed); unboxing
            // it threw and aborted the whole CDM report.
            if (v != null && Boolean.TRUE.equals(v.isNumeric())) {
                return 1;
            } else {
                return 0;
            }
        }
        return 0;
    }

    /*****************************************************************************************
     * Check if the data stored met guideline
     *
     * @return boolean
     ******************************************************************************************/
    public boolean isNumericValueMetGuideline(double dataEntry, String guideline, String aboveBelow) {

        MiscUtils.getLogger().debug("this is a numeric value");
        boolean passAllTests = false;

        if (aboveBelow.compareTo(">") == 0) {
            if (dataEntry >= Double.parseDouble(guideline)) {
                passAllTests = true;
                MiscUtils.getLogger().debug("Pass double test");
            } else {
                passAllTests = false;
            }
        } else if (aboveBelow.compareTo("<") == 0) {
            if (dataEntry <= Double.parseDouble(guideline)) {
                passAllTests = true;
                MiscUtils.getLogger().debug("Pass double test");
            } else {
                passAllTests = false;
            }
        }
        return passAllTests;
    }

    /*****************************************************************************************
     * Check if the the blood pressure stored met guideline
     *
     * @return boolean
     ******************************************************************************************/
    public boolean isBloodPressureMetGuideline(String dataEntry, String guideline, String aboveBelow) {

        MiscUtils.getLogger().debug("this is blood pressure");

        int slashIndex = guideline.indexOf("/");
        int dataSlashIndex = dataEntry.indexOf("/");
        boolean passAllTests = false;

        if (slashIndex >= 0 && dataSlashIndex >= 0) {
            String systolic = guideline.substring(0, slashIndex);
            String diastolic = guideline.substring(slashIndex + 1);
            String systolicData = dataEntry.substring(0, dataSlashIndex);
            String diastolicData = dataEntry.substring(dataSlashIndex + 1);

            int iGuidelineSystolic = Integer.parseInt(systolic);
            int iGuidelineDiastolic = Integer.parseInt(diastolic);
            int iDataSystolic = Integer.parseInt(systolicData);
            int iDataDiastolic = Integer.parseInt(diastolicData);

            MiscUtils.getLogger().debug("guideline Systolic: " + iGuidelineSystolic + " dataSystolic: " + iDataSystolic);
            MiscUtils.getLogger().debug("guideline Diastolic: " + iGuidelineDiastolic + " dataDiastolic: " + iDataDiastolic);
            if (aboveBelow.compareTo("<") == 0) {
                if (iDataSystolic <= iGuidelineSystolic && iDataDiastolic <= iGuidelineDiastolic) {
                    passAllTests = true;
                    MiscUtils.getLogger().debug("pass this BP test");
                } else {
                    passAllTests = false;
                    MiscUtils.getLogger().debug("fail this BP test");
                }
            } else if (aboveBelow.compareTo(">") == 0) {
                if (iDataSystolic >= iGuidelineSystolic && iDataDiastolic >= iGuidelineDiastolic) {
                    passAllTests = true;
                    MiscUtils.getLogger().debug("pass this BP test");
                } else {
                    passAllTests = false;
                    MiscUtils.getLogger().debug("fail this BP test");
                }
            }
        }
        return passAllTests;
    }

    /*****************************************************************************************
     * Check if a non-numeric (yes/no or categorical) reading met guideline.
     *
     * <p>A yes/no guideline keeps its original matching. Any other guideline is a categorical
     * value, such as the Asthma Action Plan's {@code Provided}, {@code Revised} or
     * {@code Reviewed} (issue #3893). A reading meets it when it holds the same value, ignoring
     * surrounding whitespace. Without this branch every categorical reading counted as not
     * meeting the guideline.</p>
     *
     * @return boolean
     ******************************************************************************************/
    public boolean isYesNoMetGuideline(String dataEntry, String guideline) {
        boolean passAllTests;

        MiscUtils.getLogger().debug("this is yes/no question");
        if (isYes(guideline)) {
            passAllTests = isYes(dataEntry);
            MiscUtils.getLogger().debug(passAllTests ? "Pass yesno test" : "fail yesno test");
        } else if (isNo(guideline)) {
            passAllTests = isNo(dataEntry);
            MiscUtils.getLogger().debug(passAllTests ? "Pass yesno test" : "fail yesno test");
        } else if (isNotApplicable(guideline)) {
            // The seeded Yes/No/NA rule accepts both "NA" and "NotApplicable" for the same answer,
            // so a not-applicable guideline matches either spelling, as the yes and no branches do
            // for theirs. Legacy AACP readings recorded under that rule may hold either form.
            passAllTests = isNotApplicable(dataEntry);
            MiscUtils.getLogger().debug(passAllTests ? "Pass NA test" : "fail NA test");
        } else {
            // Categorical guideline: the validation rule that accepted it is case-sensitive, so
            // an exact match is the same comparison the reading itself was validated with.
            // A blank guideline names no category, so it must not "match" blank readings.
            passAllTests = !guideline.isBlank() && dataEntry != null && guideline.trim().equals(dataEntry.trim());
        }
        return passAllTests;
    }

    /** The exact yes spellings the seeded Yes/No rules accept ({@code YES|yes|Yes|Y}). */
    private static boolean isYes(String value) {
        return value != null && ("YES".equals(value) || "yes".equals(value) || "Y".equals(value) || "Yes".equals(value));
    }

    /** The exact no spellings the seeded Yes/No rules accept ({@code NO|no|No|N}). */
    private static boolean isNo(String value) {
        return value != null && ("NO".equals(value) || "No".equals(value) || "N".equals(value) || "no".equals(value));
    }

    /**
     * Whether a guideline or reading is one of the not-applicable spellings the seeded
     * {@code Yes/No/NA} validation rule accepts ({@code NA} or {@code NotApplicable}).
     */
    private static boolean isNotApplicable(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.equals("NA") || trimmed.equals("NotApplicable");
    }
}
