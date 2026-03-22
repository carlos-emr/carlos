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


    public void addPharmacy(String name, String address, String city, String province, String postalCode, String phone1, String phone2, String fax, String email, String serviceLocationIdentifier, String notes);

    public void updatePharmacy(Integer ID, String name, String address, String city, String province, String postalCode, String phone1, String phone2, String fax, String email, String serviceLocationIdentifier, String notes);

    public void deletePharmacy(Integer ID);

    public List<PharmacyInfo> getPharmacies(List<Integer> idList);

    public PharmacyInfo getPharmacy(Integer ID);

    public PharmacyInfo getPharmacyByRecordID(Integer recordID);

    public List<PharmacyInfo> getAllPharmacies();

    public List<PharmacyInfo> searchPharmacyByNameAddressCity(String name, String city);

    public List<String> searchPharmacyByCity(String city);

    // public PharmacyInfo find(Integer id);
    // public void persist(PharmacyInfo pharmacyInfo);
    // public void merge(PharmacyInfo pharmacyInfo);
    // public List<PharmacyInfo> findAll();
    //public PharmacyInfo saveEntity(PharmacyInfo pharmacyInfo);
}