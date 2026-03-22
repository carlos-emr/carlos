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
import java.util.HashMap;

import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link MeasurementsExtDao} for clinical measurement data access.
 *
 * @since 2001
 */

public class MeasurementsExtDaoImpl extends AbstractDaoImpl<MeasurementsExt> implements MeasurementsExtDao {

    /** Constructs this DAO for the {@link MeasurementsExt} entity class. */

    public MeasurementsExtDaoImpl() {
        super(MeasurementsExt.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<MeasurementsExt> getMeasurementsExtByMeasurementId(Integer measurementId) {
        String queryStr = "select m FROM MeasurementsExt m WHERE m.measurementId = ?1";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, measurementId);

        @SuppressWarnings("unchecked")
        List<MeasurementsExt> rs = q.getResultList();

        return rs;
    }

    /** {@inheritDoc} */

    @Override
    public HashMap<String, MeasurementsExt> getMeasurementsExtMapByMeasurementId(Integer measurementId) {
        HashMap<String, MeasurementsExt> measurementsExtHashMap = new HashMap<String, MeasurementsExt>();
        List<MeasurementsExt> rs = getMeasurementsExtByMeasurementId(measurementId);

        for (MeasurementsExt measurementsExt : rs) {
            measurementsExtHashMap.put(measurementsExt.getKeyVal(), measurementsExt);
        }

        return measurementsExtHashMap;
    }

    /** {@inheritDoc} */

    @Override
    public List<MeasurementsExt> getMeasurementsExtListByMeasurementIdList(List<Integer> measurementIdList) {
        String queryStr = "select m FROM MeasurementsExt m WHERE m.measurementId IN (?1) order by m.measurementId";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, measurementIdList);
        return q.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public MeasurementsExt getMeasurementsExtByMeasurementIdAndKeyVal(Integer measurementId, String keyVal) {
        String queryStr = "select m FROM MeasurementsExt m WHERE m.measurementId = ?1 AND m.keyVal = ?2";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, measurementId);
        q.setParameter(2, keyVal);

        @SuppressWarnings("unchecked")
        List<MeasurementsExt> rs = q.getResultList();

        if (rs.isEmpty()) {
            return null;
        }
        return rs.get(0);
    }

    /** {@inheritDoc} */

    @Override
    public Integer getMeasurementIdByKeyValue(String key, String value) {
        String queryStr = "select m FROM MeasurementsExt m WHERE m.keyVal=?1 AND m.val=?2";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, key);
        q.setParameter(2, value);

        @SuppressWarnings("unchecked")
        List<MeasurementsExt> rs = q.getResultList();

        if (rs.size() > 0) return rs.get(0).getMeasurementId();
        return null;
    }

    /** {@inheritDoc} */

    @Override
    public List<MeasurementsExt> findByKeyValue(String key, String value) {
        String queryStr = "select m FROM MeasurementsExt m WHERE m.keyVal=?1 AND m.val=?2";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, key);
        q.setParameter(2, value);

        @SuppressWarnings("unchecked")
        List<MeasurementsExt> rs = q.getResultList();

        return rs;
    }

    /** {@inheritDoc} */

    @Override
    public Integer getMeasurementIdByLabNoAndTestName(String labNo, String testName) {
        //TODO: consider replacing this with a simpler query.   This approach was taken because a self join such as the following was resulting in hibernate errors and in the interests in time
        //a procedural approach was taken
        /*
         * SELECT DISTINCT a.measurementId
         * FROM MeasurementsExt a
         * JOIN MeasurementsExt b ON a.measurementId = b.measurementId
         * WHERE a.keyval = 'lab_no' and a.val = ?1
         * AND b.keyval = 'name' and b.val = ?2;
         */

        String queryStr = "SELECT distinct m.measurementId FROM MeasurementsExt m " + "WHERE m.keyVal = 'lab_no' and m.val = ?1";

        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, labNo);

        @SuppressWarnings("unchecked")
        List<Integer> rs = q.getResultList();

        if (!rs.isEmpty()) {
            String mIds = "";
            for (int i = 0; i < rs.size(); i++) {
                mIds += rs.get(i);
                if (i <= rs.size() - 2) {
                    mIds += ",";
                }
            }
            queryStr = "SELECT distinct m.measurementId FROM MeasurementsExt m " + "WHERE m.measurementId in (" + mIds + ") and m.keyVal = 'name' and m.val = ?1";

            q = entityManager.createQuery(queryStr);
            q.setParameter(1, testName);

            @SuppressWarnings("unchecked")
            List<Integer> rs2 = q.getResultList();

            if (!rs2.isEmpty()) {
                return rs2.get(0);
            }
            return null;
        }
        return null;
    }

    /** {@inheritDoc} */

    @Override
    public List<Integer> findUnmappedMeasuremntIds(List<String> excludeList) {
        String queryStr = "SELECT MAX(m.measurementId) FROM MeasurementsExt m WHERE m.keyVal LIKE 'identifier' AND m.val NOT IN (?1)";
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, excludeList);
        return q.getResultList();
    }
}
