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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean;

import java.util.Collection;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class EctTypeDisplayNameBeanHandler {

    Vector<EctTypeDisplayNameBean> typeDisplayNameVector = new Vector<EctTypeDisplayNameBean>();

    public EctTypeDisplayNameBeanHandler() {
        init();
    }

    public EctTypeDisplayNameBeanHandler(String groupName, boolean excludeGroupName) {
        initGroupTypes(groupName, excludeGroupName);
    }

    public boolean init() {
        MeasurementTypeDao dao = SpringUtils.getBean(MeasurementTypeDao.class);
        for (Object name : dao.findUniqueTypeDisplayNames()) {
            EctTypeDisplayNameBean typeDisplayName = new EctTypeDisplayNameBean(String.valueOf(name));
            typeDisplayNameVector.add(typeDisplayName);
        }
        return true;
    }

    public boolean initGroupTypes(String groupName, boolean excludeGroupName) {
        MeasurementTypeDao tDao = SpringUtils.getBean(MeasurementTypeDao.class);
        MeasurementGroupDao gDao = SpringUtils.getBean(MeasurementGroupDao.class);

        if (excludeGroupName) {
            for (Object tdnMt : tDao.findUniqueTypeDisplayNames()) {
                boolean foundInGroup = false;
                String typeDisplayNameFromMeasurementType = String.valueOf(tdnMt);

                for (Object tdnMg : gDao.findUniqueTypeDisplayNamesByGroupName(groupName)) {
                    String typeDisplayNameFromMeasurmentGroup = String.valueOf(tdnMg);

                    if (typeDisplayNameFromMeasurementType.equals(typeDisplayNameFromMeasurmentGroup)) {
                        foundInGroup = true;
                        break;
                    } else {
                        foundInGroup = false;
                    }
                }

                if (!foundInGroup) {
                    EctTypeDisplayNameBean typeDisplayName = new EctTypeDisplayNameBean(typeDisplayNameFromMeasurementType);
                    typeDisplayNameVector.add(typeDisplayName);
                }
            }
        } else {
            for (Object tdnMg : gDao.findUniqueTypeDisplayNamesByGroupName(groupName)) {
                EctTypeDisplayNameBean typeDisplayName = new EctTypeDisplayNameBean(String.valueOf(tdnMg));
                typeDisplayNameVector.add(typeDisplayName);
            }
        }

        return true;
    }

    public Collection<EctTypeDisplayNameBean> getTypeDisplayNameVector() {
        return typeDisplayNameVector;
    }
}
