/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;

/**
 * DAO interface for pharmacy operations.
 *
 * @since 2001
 */

public interface PharmacyInfoDao extends AbstractDao<PharmacyInfo> {


    /**
     * Add Pharmacy.
     *
     * @param name String the name
     * @param address String the address
     * @param city String the city
     * @param province String the province
     * @param postalCode String the postalCode
     * @param phone1 String the phone1
     * @param phone2 String the phone2
     * @param fax String the fax
     * @param email String the email
     * @param serviceLocationIdentifier String the serviceLocationIdentifier
     * @param notes String the notes
     */
    public void addPharmacy(String name, String address, String city, String province, String postalCode, String phone1, String phone2, String fax, String email, String serviceLocationIdentifier, String notes);

    /**
     * Update Pharmacy.
     *
     * @param ID Integer the ID
     * @param name String the name
     * @param address String the address
     * @param city String the city
     * @param province String the province
     * @param postalCode String the postalCode
     * @param phone1 String the phone1
     * @param phone2 String the phone2
     * @param fax String the fax
     * @param email String the email
     * @param serviceLocationIdentifier String the serviceLocationIdentifier
     * @param notes String the notes
     */
    public void updatePharmacy(Integer ID, String name, String address, String city, String province, String postalCode, String phone1, String phone2, String fax, String email, String serviceLocationIdentifier, String notes);

    /**
     * Delete Pharmacy.
     *
     * @param ID Integer the ID
     */
    public void deletePharmacy(Integer ID);

    /**
     * Get Pharmacies.
     *
     * @param idList List<Integer> the idList
     * @return List<PharmacyInfo>
     */
    public List<PharmacyInfo> getPharmacies(List<Integer> idList);

    /**
     * Get Pharmacy.
     *
     * @param ID Integer the ID
     * @return PharmacyInfo
     */
    public PharmacyInfo getPharmacy(Integer ID);

    /**
     * Get Pharmacy By Record I D.
     *
     * @param recordID Integer the recordID
     * @return PharmacyInfo
     */
    public PharmacyInfo getPharmacyByRecordID(Integer recordID);

    /**
     * Get All Pharmacies.
     * @return List<PharmacyInfo>
     */
    public List<PharmacyInfo> getAllPharmacies();

    /**
     * Search Pharmacy By Name Address City.
     *
     * @param name String the name
     * @param city String the city
     * @return List<PharmacyInfo>
     */
    public List<PharmacyInfo> searchPharmacyByNameAddressCity(String name, String city);

    /**
     * Search Pharmacy By City.
     *
     * @param city String the city
     * @return List<String>
     */
    public List<String> searchPharmacyByCity(String city);

    // public PharmacyInfo find(Integer id);
    // public void persist(PharmacyInfo pharmacyInfo);
    // public void merge(PharmacyInfo pharmacyInfo);
    // public List<PharmacyInfo> findAll();
    //public PharmacyInfo saveEntity(PharmacyInfo pharmacyInfo);
}