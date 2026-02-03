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
package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.DrugProduct;
import io.github.carlos_emr.carlos.commn.model.DrugProductTemplate;
import io.github.carlos_emr.carlos.commn.model.ProductLocation;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public interface DrugProductManager {

    public void saveDrugProduct(LoggedInInfo loggedInInfo, DrugProduct drugProduct);

    public void updateDrugProduct(LoggedInInfo loggedInInfo, DrugProduct drugProduct);

    public DrugProduct getDrugProduct(LoggedInInfo loggedInInfo, Integer id);

    public List<DrugProduct> getAllDrugProducts(LoggedInInfo loggedInInfo, Integer offset, Integer limit);

    public List<DrugProduct> getAllDrugProductsByName(LoggedInInfo loggedInInfo, Integer offset, Integer limit,
                                                      String productName);

    public List<DrugProduct> getAllDrugProductsByNameAndLot(LoggedInInfo loggedInInfo, Integer offset, Integer limit,
                                                            String productName, String lotNumber, Integer location, boolean availableOnly);

    public Integer getAllDrugProductsByNameAndLotCount(LoggedInInfo loggedInInfo, String productName, String lotNumber,
                                                       Integer location, boolean availableOnly);

    public List<DrugProduct> getAllDrugProductsGroupedByCode(LoggedInInfo loggedInInfo, Integer offset, Integer limit);

    public List<String> findUniqueDrugProductNames(LoggedInInfo loggedInInfo);

    public List<String> findUniqueDrugProductLotsByName(LoggedInInfo loggedInInfo, String productName);

    public void deleteDrugProduct(LoggedInInfo loggedInInfo, Integer drugProductId);

    public List<ProductLocation> getProductLocations();

    public List<DrugProductTemplate> getDrugProductTemplates();
}
