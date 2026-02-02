//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.DrugProduct;
import io.github.carlos_emr.carlos.prescription.dispensary.LotBean;

public interface DrugProductDao extends AbstractDao<DrugProduct> {

    public List<DrugProduct> findAvailable();

    public List<DrugProduct> findAvailableByCode(String code);

    public List<Object[]> findAllAvailableUnique();

    public List<Object[]> findAllUnique();

    public List<String> findUniqueDrugProductNames();

    public int getAvailableCount(String lotNumber, Date expiryDate, int amount);

    public List<DrugProduct> getAvailableDrugProducts(String lotNumber, Date expiryDate, int amount);

    public List<LotBean> findDistinctLotsAvailableByCode(String code);

    public DrugProduct findByCodeAndLotNumber(String code, String lotNumber);

    public List<DrugProduct> findByDispensingId(Integer id);

    public List<DrugProduct> findByName(int offset, int limit, String name);

    public List<DrugProduct> findAll(int offset, int limit);

    public List<DrugProduct> findByNameAndLot(int offset, int limit, String name, String lotNumber, Integer location,
                                              boolean availableOnly);

    public Integer findByNameAndLotCount(String name, String lotNumber, Integer location, boolean availableOnly);

    public List<String> findUniqueDrugProductLotsByName(String productName);
}
