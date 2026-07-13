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
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.CarlosProperties;

@Repository
@SuppressWarnings("unchecked")
public class MeasurementGroupDaoImpl extends AbstractDaoImpl<MeasurementGroup> implements MeasurementGroupDao {

    public MeasurementGroupDaoImpl() {
        super(MeasurementGroup.class);
    }

    @Override
    public List<MeasurementGroup> findAll() {
        Query query = createQuery("x", null);
        return query.getResultList();
    }

    @Override
    public List<MeasurementGroup> findByNameAndTypeDisplayName(String name, String typeDisplayName) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.name=?1 AND x.typeDisplayName=?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, name);
        query.setParameter(2, typeDisplayName);


        List<MeasurementGroup> results = query.getResultList();

        return (results);
    }

    @Override
    public List<MeasurementGroup> findByTypeDisplayName(String typeDisplayName) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.typeDisplayName=?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, typeDisplayName);


        List<MeasurementGroup> results = query.getResultList();

        return (results);
    }

    @Override
    public List<MeasurementGroup> findByName(String name) {
        boolean orderById = "true".equals(CarlosProperties.getInstance().getProperty("oscarMeasurements.orderGroupById", "false"));
        String orderBy = "";
        if (orderById) {
            orderBy = " ORDER BY x.id ASC";
        }
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.name=?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, name);

        List<MeasurementGroup> results = query.getResultList();

        return (results);
    }

    @Override
    public List<Object> findUniqueTypeDisplayNamesByGroupName(String groupName) {
        String sql = "SELECT DISTINCT mg.typeDisplayName FROM MeasurementGroup mg WHERE mg.name = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, groupName);
        return query.getResultList();
    }
}
