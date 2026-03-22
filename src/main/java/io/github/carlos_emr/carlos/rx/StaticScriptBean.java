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


package io.github.carlos_emr.carlos.rx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;

/**
 * Provides static utility methods for retrieving and displaying prescription drug data
 * for a given patient demographic.
 *
 * <p>This bean serves as a read-only interface for looking up drugs prescribed to a patient,
 * matching by regional identifier, custom name, brand name, or ATC code. Results are
 * returned as {@link DrugDisplayData} objects sorted by prescription start date in
 * descending order (most recent first).</p>
 *
 * <p>The inner {@link DrugDisplayData} class is a lightweight value object that aggregates
 * prescription details including provider name, dates, generic/brand/custom names,
 * archive status, pickup information, and dispensing details for display purposes.</p>
 *
 * @since 2001-01-01
 * @see io.github.carlos_emr.carlos.commn.dao.DrugDao
 * @see io.github.carlos_emr.carlos.commn.model.Drug
 */
public class StaticScriptBean {
    private static final Logger logger = MiscUtils.getLogger();
    private static DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);

    /**
     * Value object containing prescription drug information formatted for display in the UI.
     *
     * <p>Includes provider name, prescription dates, drug identifiers (generic, brand, custom),
     * archive and authorization status, pickup details, and refill information. Supports
     * sorting by start date via the {@link #DATE_COMPARATOR}.</p>
     */
    public static class DrugDisplayData {
        public static final Comparator<DrugDisplayData> DATE_COMPARATOR = new Comparator<DrugDisplayData>() {
            public int compare(DrugDisplayData o1, DrugDisplayData o2) {
                if (o1.dateStartDate.after(o2.dateStartDate)) return (-1);
                else if (o1.dateStartDate.before(o2.dateStartDate)) return (1);
                else {
                    if (o1.isLocal) return (-1);
                    else return (1);
                }
            }
        };

        public Integer localDrugId = null;
        public String providerName = null;
        public String startDate = null;
        protected Date dateStartDate = null;
        public String endDate = null;
        public String writtenDate = null;
        public String prescriptionDetails = null;
        public String genericName = null;
        public String customName = null;
        public String brandName = null;
        public boolean isArchived = false;
        public boolean isLocal = true;
        public boolean nonAuthoritative = false;
        public String pickupDate = null;
        public String pickupTime = null;
        public String eTreatmentType = null;
        public String rxStatus = null;
        public String dispenseInterval;
        public Integer refillQuantity;
        public Integer refillDuration;
    }

    /**
     * Retrieves a sorted list of drug display data for a patient, matching by drug identifiers.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param demographicId int the patient demographic ID
     * @param regionalIdentifier String the drug regional identifier (DIN) to match, or null
     * @param customName String the custom drug name to match, or null
     * @param brandName String the brand name to match, or null
     * @return ArrayList of DrugDisplayData sorted by start date descending
     */
    public static ArrayList<DrugDisplayData> getDrugList(LoggedInInfo loggedInInfo, int demographicId, String regionalIdentifier, String customName, String brandName) {
        return getDrugList(loggedInInfo, demographicId, regionalIdentifier, customName, brandName, null);
    }

    /**
     * Retrieves a sorted list of drug display data for a patient, matching by drug identifiers
     * including ATC code.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param demographicId int the patient demographic ID
     * @param regionalIdentifier String the drug regional identifier (DIN) to match, or null
     * @param customName String the custom drug name to match, or null
     * @param brandName String the brand name to match, or null
     * @param atc String the ATC (Anatomical Therapeutic Chemical) code to match, or null
     * @return ArrayList of DrugDisplayData sorted by start date descending
     */
    public static ArrayList<DrugDisplayData> getDrugList(LoggedInInfo loggedInInfo, int demographicId, String regionalIdentifier, String customName, String brandName, String atc) {
        regionalIdentifier = StringUtils.trimToNull(regionalIdentifier);
        customName = StringUtils.trimToNull(customName);
        atc = StringUtils.trimToNull(atc);

        ArrayList<DrugDisplayData> results = new ArrayList<DrugDisplayData>();

        // add local drugs
        List<Drug> drugs = drugDao.findByDemographicIdSimilarDrugOrderByDate(demographicId, regionalIdentifier, customName, brandName, atc);
        for (Drug drug : drugs) {
            results.add(getDrugDisplayData(drug));
        }

        Collections.sort(results, DrugDisplayData.DATE_COMPARATOR);
        return (results);
    }

    private static DrugDisplayData getDrugDisplayData(Drug drug) {
        DrugDisplayData drugDisplayData = new DrugDisplayData();

        drugDisplayData.localDrugId = drug.getId();

        RxProviderData.Provider prov = new RxProviderData().getProvider(drug.getProviderNo());
        drugDisplayData.providerName = prov.getFirstName() + ' ' + prov.getSurname();

        drugDisplayData.startDate = RxUtil.DateToString(drug.getRxDate());
        drugDisplayData.dateStartDate = drug.getRxDate();

        drugDisplayData.endDate = RxUtil.DateToString(drug.getEndDate());
        drugDisplayData.writtenDate = RxUtil.DateToString(drug.getWrittenDate());

        drugDisplayData.prescriptionDetails = RxPrescriptionData.getFullOutLine(drug.getSpecial()).replaceAll(";", " ");

        drugDisplayData.nonAuthoritative = drug.isNonAuthoritative();

        drugDisplayData.genericName = drug.getGenericName();

        drugDisplayData.customName = drug.getCustomName();

        drugDisplayData.brandName = drug.getBrandName();

        drugDisplayData.isArchived = drug.isArchived();

        drugDisplayData.pickupDate = RxUtil.DateToString(drug.getPickUpDateTime(), "yyyy-MM-dd");

        drugDisplayData.pickupTime = RxUtil.DateToString(drug.getPickUpDateTime(), "hh:mm aa");

        drugDisplayData.eTreatmentType = drug.getETreatmentType();

        drugDisplayData.rxStatus = drug.getRxStatus();

        drugDisplayData.dispenseInterval = drug.getDispenseInterval();

        drugDisplayData.refillDuration = drug.getRefillDuration();

        drugDisplayData.refillQuantity = drug.getRefillQuantity();

        return (drugDisplayData);
    }
}
