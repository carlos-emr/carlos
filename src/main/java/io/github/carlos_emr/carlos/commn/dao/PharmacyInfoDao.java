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

public interface PharmacyInfoDao extends AbstractDao<PharmacyInfo> {


    public void addPharmacy(String name, String address, String city, String province, String postalCode, String phone1, String phone2, String fax, String email, String serviceLocationIdentifier, String notes);

    public void updatePharmacy(Integer ID, String name, String address, String city, String province, String postalCode, String phone1, String phone2, String fax, String email, String serviceLocationIdentifier, String notes);

    public void deletePharmacy(Integer ID);

    public List<PharmacyInfo> getPharmacies(List<Integer> idList);

    public PharmacyInfo getPharmacy(Integer ID);

    public PharmacyInfo getPharmacyByRecordID(Integer recordID);

    public List<PharmacyInfo> getAllPharmacies();

    public List<PharmacyInfo> searchPharmacyByNameAddressCity(String name, String city);

    /**
     * Searches ACTIVE pharmacies that can actually receive a fax, for the fax recipient picker.
     *
     * <p>The fax-number filter is applied in SQL rather than by the caller for the same reason it
     * is on {@code ServiceSpecialistsDao.searchSpecialistsWithService}: {@code maxResults} caps the
     * rows the database returns, so discarding fax-less rows afterwards let a run of them consume
     * the whole limit and hide faxable pharmacies that sorted after them.
     *
     * @param keyword    matched against name or address
     * @param city       matched against city; pass {@code ""} to accept any city
     * @param maxResults row cap, applied after the fax filter; ignored when not positive
     * @return active pharmacies with a non-blank fax number, ordered by name then address
     */
    public List<PharmacyInfo> searchFaxablePharmacies(String keyword, String city, int maxResults);

    public List<String> searchPharmacyByCity(String city);

    // public PharmacyInfo find(Integer id);
    // public void persist(PharmacyInfo pharmacyInfo);
    // public void merge(PharmacyInfo pharmacyInfo);
    // public List<PharmacyInfo> findAll();
    //public PharmacyInfo saveEntity(PharmacyInfo pharmacyInfo);
}