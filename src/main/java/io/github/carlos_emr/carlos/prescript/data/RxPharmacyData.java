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
 * <p>
 * composePharmacyPhone ported from Open-O (openo-beta/Open-O PR #2494,
 * Liam Stanziani, 2026); modifications by CARLOS Contributors, 2026.
 */


/*
 * RxPharmacyData.java
 *
 * Created on September 29, 2004, 3:41 PM
 */

package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.model.DemographicPharmacy;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * @author Jay Gallagher
 */
public class RxPharmacyData {

    /** A run of CR/LF inside a stored phone value; collapsed so the fax note stays on one line. */
    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]+");

    private PharmacyInfoDao pharmacyInfoDao = (PharmacyInfoDao) SpringUtils.getBean(PharmacyInfoDao.class);
    private DemographicPharmacyDao demographicPharmacyDao = (DemographicPharmacyDao) SpringUtils.getBean(DemographicPharmacyDao.class);

    /**
     * Creates a new instance of RxPharmacyData
     */
    public RxPharmacyData() {
    }


    /**
     * Used to add a new pharmacy
     *
     * @param name
     * @param address
     * @param city
     * @param province
     * @param postalCode
     * @param phone1
     * @param phone2
     * @param fax
     * @param email
     * @param notes
     */
    synchronized public void addPharmacy(String name, String address, String city, String province, String postalCode, String phone1, String phone2, String fax, String email, String serviceLocationIdentifier, String notes) {
        pharmacyInfoDao.addPharmacy(name, address, city, province, postalCode, phone1, phone2, fax, email, serviceLocationIdentifier, notes);
    }


    /**
     * Used to update an new pharmacy.  Creates a new record for this pharmacy with the same pharmacyID
     *
     * @param ID         pharmacy ID
     * @param name
     * @param address
     * @param city
     * @param province
     * @param postalCode
     * @param phone1
     * @param phone2
     * @param fax
     * @param email
     * @param notes
     */
    public void updatePharmacy(String ID, String name, String address, String city, String province, String postalCode, String phone1, String phone2, String fax, String email, String serviceLocationIdentifier, String notes) {
        pharmacyInfoDao.updatePharmacy(Integer.parseInt(ID), name, address, city, province, postalCode, phone1, phone2, fax, email, serviceLocationIdentifier, notes);
    }

    /**
     * set the status of the pharmacy to 0, this will not be found in the getAllPharmacy queries
     *
     * @param ID
     */
    public void deletePharmacy(String ID) {

        List<DemographicPharmacy> demographicPharmacies = demographicPharmacyDao.findAllByPharmacyId(Integer.parseInt(ID));

        for (DemographicPharmacy demographicPharmacy : demographicPharmacies) {
            demographicPharmacyDao.unlinkPharmacy(Integer.parseInt(ID), demographicPharmacy.getDemographicNo());
        }

        pharmacyInfoDao.deletePharmacy(Integer.parseInt(ID));
    }

    /**
     * Returns the latest data about a pharmacy.
     *
     * <p>Every caller hands this a request-sourced string (the Rx preview and
     * view pages, the pharmacy-info AJAX action, the customized-PDF servlet)
     * and each already handles a null result, so a value that is not a plain
     * numeric id answers null here rather than letting
     * {@code Integer.parseInt} throw and 500 the whole page.</p>
     *
     * @param ID pharmacy id
     * @return the pharmacy, or null when the id is not numeric or is unknown
     */
    public PharmacyInfo getPharmacy(String ID) {
        // [0-9] spelled out rather than \d for explicitness: without
        // UNICODE_CHARACTER_CLASS the two are equivalent (ASCII-only), and
        // the guard must stay narrower than Integer.parseInt, which DOES
        // accept Unicode digits.
        if (ID == null || !ID.matches("[0-9]{1,9}")) {
            return null;
        }
        PharmacyInfo pharmacyInfo = pharmacyInfoDao.getPharmacy(Integer.parseInt(ID));
        return pharmacyInfo;
    }

    /**
     * Returns the data about a pharmacy record.  This would be used to see prior addresses or phone numbers of a pharmacy.
     *
     * @param recordID pharmacy Record ID
     * @return Pharmacy data class
     */
    public PharmacyInfo getPharmacyByRecordID(String recordID) {
        return pharmacyInfoDao.getPharmacyByRecordID(Integer.parseInt(recordID));
    }


    /**
     * Used to get a list of all the active pharmacies with their latest data
     *
     * @return ArrayList of Pharmacy classes
     */
    public List<PharmacyInfo> getAllPharmacies() {
        return pharmacyInfoDao.getAllPharmacies();
    }

    /**
     * Used to link a patient with a pharmacy.
     *
     * @param pharmacyId    Id of the pharmacy
     * @param demographicNo Patient demographic number
     */
    public PharmacyInfo addPharmacyToDemographic(String pharmacyId, String demographicNo, String preferredOrder) {
        demographicPharmacyDao.addPharmacyToDemographic(Integer.parseInt(pharmacyId), Integer.parseInt(demographicNo), Integer.parseInt(preferredOrder));

        PharmacyInfo pharmacyInfo = pharmacyInfoDao.find(Integer.parseInt(pharmacyId));
        pharmacyInfo.setPreferredOrder(Integer.parseInt(preferredOrder));

        return pharmacyInfo;

    }

    /**
     * Used to get the most recent pharmacy associated with this patient.  Returns a Pharmacy object with the latest data about that pharmacy.
     *
     * @param demographicNo patients demographic number
     * @return Pharmacy data object
     */
    public List<PharmacyInfo> getPharmacyFromDemographic(String demographicNo) {

		if (demographicNo == null || demographicNo.isEmpty() || !demographicNo.matches("\\d+")) {
			return null;
		}


        List<DemographicPharmacy> dpList = demographicPharmacyDao.findByDemographicId(Integer.parseInt(demographicNo));
        if (dpList.isEmpty()) {
            return null;
        }

        List<Integer> pharmacyIds = new ArrayList<Integer>();
        for (DemographicPharmacy demoPharmacy : dpList) {
            pharmacyIds.add(demoPharmacy.getPharmacyId());
            MiscUtils.getLogger().debug("ADDING ID " + demoPharmacy.getPharmacyId());
        }

        List<PharmacyInfo> pharmacyInfos = pharmacyInfoDao.getPharmacies(pharmacyIds);

        for (DemographicPharmacy demographicPharmacy : dpList) {
            for (PharmacyInfo pharmacyInfo : pharmacyInfos) {
                if (demographicPharmacy.getPharmacyId() == pharmacyInfo.getId()) {
                    pharmacyInfo.setPreferredOrder(demographicPharmacy.getPreferredOrder());
                    pharmacyInfo.setDemoAddDate(demographicPharmacy.getAddDate());
                    break;
                }
            }
        }

        Collections.sort(pharmacyInfos);
        return pharmacyInfos;
    }

    public List<String> searchPharmacyCity(String searchTerm) {

        return pharmacyInfoDao.searchPharmacyByCity(searchTerm);

    }

    public List<PharmacyInfo> searchPharmacy(String searchTerm) {

        String[] terms;
        String name = "", city = "";

        if (searchTerm.indexOf(",") > -1) {
            terms = searchTerm.split(",", -1);

            switch (terms.length) {
                case 2:
                    city = terms[1];
                    // fall through
                case 1:
                    name = terms[0];
                    break;
                default:
                    break;
            }
        } else {
            name = searchTerm;
        }

        return pharmacyInfoDao.searchPharmacyByNameAddressCity(name, city);

    }

    public void unlinkPharmacy(String pharmacyId, String demographicNo) {

        demographicPharmacyDao.unlinkPharmacy(Integer.parseInt(pharmacyId), Integer.parseInt(demographicNo));

    }

    public Long getTotalDemographicsPreferedToPharmacyByPharmacyId(String pharmacyId) {
        return demographicPharmacyDao.getTotalDemographicsPreferedToPharmacyByPharmacyId(Integer.parseInt(pharmacyId));
    }

    /**
     * Composes a pharmacy's telephone numbers into the single value shown wherever a pharmacy is
     * named to a provider: the "Rx faxed to" encounter note written by {@code rx/ViewScript2.jsp}
     * and the pharmacy block printed on the prescription PDF.
     *
     * <p>{@code phone1} and {@code phone2} are joined with a single space, skipping whichever is
     * null or blank, so the result never contains a stray separator or the literal {@code "null"}.
     * Each number is trimmed and any embedded CR/LF run is collapsed to one space so the chart
     * note stays on one line. Otherwise the numbers are returned verbatim: stored pharmacy phone
     * fields are unvalidated, admin-entered free text in mixed formats, so this method does not
     * normalise them and callers must still encode the result for their output context.</p>
     *
     * <p>Ported from Open-O PR #2494 (openo-beta/Open-O, LiamStanziani).</p>
     *
     * @param pharmacy the pharmacy, may be null
     * @return the joined phone numbers, or {@code ""} when the pharmacy is null or has none on file;
     *         never null
     * @since 2026-09-26
     */
    public static String composePharmacyPhone(PharmacyInfo pharmacy) {
        if (pharmacy == null) {
            return "";
        }
        StringJoiner phones = new StringJoiner(" ");
        for (String phone : new String[]{pharmacy.getPhone1(), pharmacy.getPhone2()}) {
            if (phone != null) {
                String cleaned = LINE_BREAKS.matcher(phone).replaceAll(" ").trim();
                if (!cleaned.isEmpty()) {
                    phones.add(cleaned);
                }
            }
        }
        return phones.toString();
    }
}
