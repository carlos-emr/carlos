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

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.DrugProduct;
import io.github.carlos_emr.carlos.prescription.dispensary.LotBean;

/**
 * DAO interface for drug and prescription operations.
 *
 * @since 2001
 */

public interface DrugProductDao extends AbstractDao<DrugProduct> {

    /**
     * Find Available.
     * @return List<DrugProduct>
     */
    public List<DrugProduct> findAvailable();

    /**
     * Find Available By Code.
     *
     * @param code String the code
     * @return List<DrugProduct>
     */
    public List<DrugProduct> findAvailableByCode(String code);

    /**
     * Find All Available Unique.
     * @return List<Object[]>
     */
    public List<Object[]> findAllAvailableUnique();

    /**
     * Find All Unique.
     * @return List<Object[]>
     */
    public List<Object[]> findAllUnique();

    /**
     * Find Unique Drug Product Names.
     * @return List<String>
     */
    public List<String> findUniqueDrugProductNames();

    /**
     * Get Available Count.
     *
     * @param lotNumber String the lotNumber
     * @param expiryDate Date the expiryDate
     * @param amount int the amount
     * @return int
     */
    public int getAvailableCount(String lotNumber, Date expiryDate, int amount);

    /**
     * Get Available Drug Products.
     *
     * @param lotNumber String the lotNumber
     * @param expiryDate Date the expiryDate
     * @param amount int the amount
     * @return List<DrugProduct>
     */
    public List<DrugProduct> getAvailableDrugProducts(String lotNumber, Date expiryDate, int amount);

    /**
     * Find Distinct Lots Available By Code.
     *
     * @param code String the code
     * @return List<LotBean>
     */
    public List<LotBean> findDistinctLotsAvailableByCode(String code);

    /**
     * Find By Code And Lot Number.
     *
     * @param code String the code
     * @param lotNumber String the lotNumber
     * @return DrugProduct
     */
    public DrugProduct findByCodeAndLotNumber(String code, String lotNumber);

    /**
     * Find By Dispensing Id.
     *
     * @param id Integer the id
     * @return List<DrugProduct>
     */
    public List<DrugProduct> findByDispensingId(Integer id);

    /**
     * Find By Name.
     *
     * @param offset int the offset
     * @param limit int the limit
     * @param name String the name
     * @return List<DrugProduct>
     */
    public List<DrugProduct> findByName(int offset, int limit, String name);

    /**
     * Find All.
     *
     * @param offset int the offset
     * @param limit int the limit
     * @return List<DrugProduct>
     */
    public List<DrugProduct> findAll(int offset, int limit);

    public List<DrugProduct> findByNameAndLot(int offset, int limit, String name, String lotNumber, Integer location,
                                              boolean availableOnly);

    /**
     * Find By Name And Lot Count.
     *
     * @param name String the name
     * @param lotNumber String the lotNumber
     * @param location Integer the location
     * @param availableOnly boolean the availableOnly
     * @return Integer
     */
    public Integer findByNameAndLotCount(String name, String lotNumber, Integer location, boolean availableOnly);

    /**
     * Find Unique Drug Product Lots By Name.
     *
     * @param productName String the productName
     * @return List<String>
     */
    public List<String> findUniqueDrugProductLotsByName(String productName);
}
