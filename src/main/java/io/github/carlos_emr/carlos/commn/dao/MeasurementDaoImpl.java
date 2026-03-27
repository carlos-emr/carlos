/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import org.springframework.stereotype.Repository;

@Repository
public class MeasurementDaoImpl extends AbstractDaoImpl<Measurement> implements MeasurementDao {

    public MeasurementDaoImpl() {
        super(Measurement.class);
    }

    @Override
    public List<Measurement> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date updatedAfterThisDate) {

        // using create date since this object is not updateable
        String sqlCommand = "select x from Measurement x where x.demographicId=?1 and x.createDate>?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, updatedAfterThisDate);

        List<Measurement> results = query.getResultList();

        return (results);
    }

    /**
     * @return results ordered by createDate
     */
    @Override
    public List<Measurement> findByCreateDate(Date updatedAfterThisDateExclusive, int itemsToReturn) {
        String sqlCommand = "select x from " + modelClass.getSimpleName()
                + " x where x.createDate>?1 order by x.createDate";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, updatedAfterThisDateExclusive);
        setLimit(query, itemsToReturn);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();
        return (results);
    }

    // for integrator
    @Override
    public List<Integer> findDemographicIdsUpdatedAfterDate(Date updatedAfterThisDate) {

        String sqlCommand = "select x.demographicId from Measurement x where x.createDate>?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, updatedAfterThisDate);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return (results);
    }

    @Override
    public List<Measurement> findMatching(Measurement measurement) {

        String sqlCommand = "select x from Measurement x where x.demographicId=?1 and x.dataField=?2 and x.measuringInstruction=?3 and x.comments=?4 and x.dateObserved=?5 and x.type=?6";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, measurement.getDemographicId());
        query.setParameter(2, measurement.getDataField());
        query.setParameter(3, measurement.getMeasuringInstruction());
        query.setParameter(4, measurement.getComments());
        query.setParameter(5, measurement.getDateObserved());
        query.setParameter(6, measurement.getType());

        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByDemographicNo(Integer demographicNo) {
        String sqlCommand = "select x from Measurement x where x.demographicId = ?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicNo);

        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByDemographicNoAndType(Integer demographicNo, String type) {
        String sqlCommand = "select x from Measurement x where x.demographicId = ?1 and x.type=?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicNo);
        query.setParameter(2, type);

        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public Measurement findLatestByDemographicNoAndType(int demographicNo, String type) {
        List<Measurement> ms = findByDemographicNoAndType(demographicNo, type);
        if (ms.size() == 0)
            return null;
        Collections.sort(ms, Measurement.DateObservedComparator);
        return ms.get(ms.size() - 1);

    }

    @Override
    public List<Measurement> findByAppointmentNo(Integer appointmentNo) {
        String sqlCommand = "select x from Measurement x where x.appointmentNo = ?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, appointmentNo);

        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByAppointmentNo2(Integer appointmentNo) {
        String sqlCommand = "select x from Measurement x where x.appointmentNo = ?1 ORDER BY x.type, x.dateObserved ASC";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, appointmentNo);
        List<Measurement> results = query.getResultList();
        return results;
    }

    @Override
    public List<Measurement> findByAppointmentNoAndType(Integer appointmentNo, String type) {
        String sqlCommand = "select x from Measurement x where x.appointmentNo = ?1 and x.type = ?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, appointmentNo);
        query.setParameter(2, type);

        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public Measurement findLatestByAppointmentNoAndType(int appointmentNo, String type) {
        List<Measurement> ms = findByAppointmentNoAndType(appointmentNo, type);
        if (ms.size() == 0)
            return null;
        Collections.sort(ms, Measurement.DateObservedComparator);
        return ms.get(ms.size() - 1);

    }

    @Override
    public List<Measurement> findByDemographicIdObservedDate(Integer demographicId, Date startDate, Date endDate) {
        String sqlCommand = "select x from Measurement x where x.demographicId=?1 and x.type!='' and x.dateObserved >?2 and x.dateObserved <?3 order by x.dateObserved desc, x.createDate desc";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, startDate);
        query.setParameter(3, endDate);

        List<Measurement> results = query.getResultList();

        return (results);
    }

    @Override
    public List<Measurement> findByDemographicId(Integer demographicId) {
        String sqlCommand = "select x from Measurement x where x.demographicId=?1 and x.type!='' order by x.dateObserved desc";
        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);

        List<Measurement> results = query.getResultList();

        return (results);
    }

    /**
     * Finds be
     *
     * @param criteria
     * @return list of measurements
     */

    @Override
    public List<Measurement> find(SearchCriteria criteria) {
        Map<String, Object> params = new HashMap<String, Object>();
        StringBuilder buf = new StringBuilder();

        for (Object[] obj : new Object[][]{
                {"m.demographicId = :demographicNo", "demographicNo", criteria.getDemographicNo()},
                {"m.type= :type", "type", criteria.getType()},
                {"m.dataField = :dataField", "dataField", criteria.getDataField()},
                {"m.measuringInstruction = :measuringInstrc", "measuringInstrc", criteria.getMeasuringInstrc()},
                {"m.comments = :comments", "comments", criteria.getComments()},
                {"m.dateObserved = :dateObserved", "dateObserved", criteria.getDateObserved()}}) {

            String queryClause = (String) obj[0];
            String paramName = (String) obj[1];
            Object paramValue = obj[2];

            if (paramValue == null) {
                continue;
            }

            if (buf.length() != 0) {
                buf.append("AND ");
            }

            buf.append(queryClause).append(" ");
            params.put(paramName, paramValue);
        }

        // make sure empty sc still results in a well-formed query
        if (buf.length() > 0) {
            buf.insert(0, " WHERE ");
        }
        buf.insert(0, "select m FROM Measurement m");

        Query query = entityManager.createQuery(buf.toString());
        for (Entry<String, Object> param : params.entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }
        return query.getResultList();
    }

    /**
     * Criteria for measurement search.
     */

    public static class SearchCriteria {

        private Integer demographicNo;
        private String type;
        private String dataField;
        private String measuringInstrc;
        private String comments;
        private Date dateObserved;

        public SearchCriteria() {
        }

        public SearchCriteria(Integer demographicNo, String type, String dataField, String measuringInstrc,
                              String comments, Date dateObserved) {
            super();
            this.demographicNo = demographicNo;
            this.type = type;
            this.dataField = dataField;
            this.measuringInstrc = measuringInstrc;
            this.comments = comments;
            this.dateObserved = dateObserved;
        }

        public Integer getDemographicNo() {
            return demographicNo;
        }

        public void setDemographicNo(String demographicNo) {
            setDemographicNo(Integer.parseInt(demographicNo));
        }

        public void setDemographicNo(Integer demographicNo) {
            this.demographicNo = demographicNo;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDataField() {
            return dataField;
        }

        public void setDataField(String dataField) {
            this.dataField = dataField;
        }

        public String getMeasuringInstrc() {
            return measuringInstrc;
        }

        public void setMeasuringInstrc(String measuringInstrc) {
            this.measuringInstrc = measuringInstrc;
        }

        public String getComments() {
            return comments;
        }

        public void setComments(String comments) {
            this.comments = comments;
        }

        public Date getDateObserved() {
            return dateObserved;
        }

        public void setDateObserved(Date dateObserved) {
            this.dateObserved = dateObserved;
        }
    }

    /**
     * Looks up measurement information based on the demographic id, type and
     * instructions.
     *
     * @param demographicId ID of the demographic record
     * @param type          Type of the measurement
     * @param instructions  Measurement instructions
     * @return Returns the measurements found
     */

    @Override
    public List<Measurement> findByIdTypeAndInstruction(Integer demographicId, String type, String instructions) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName()
                + " m WHERE m.demographicId = ?1" + "AND m.type = ?2"
                + "AND m.measuringInstruction = ?3 ORDER BY m.createDate DESC");
        query.setParameter(1, demographicId);
        query.setParameter(2, type);
        query.setParameter(3, instructions);
        query.setMaxResults(1);
        return query.getResultList();
    }

    @Override
    public HashMap<String, Measurement> getMeasurements(Integer demographicNo, String[] types) {
        HashMap<String, Measurement> map = new HashMap<String, Measurement>();
        String queryStr = "select m from Measurement m WHERE m.demographicId = ?1 AND m.type IN (?2) ORDER BY m.type,m.dateObserved";
        Query query = entityManager.createQuery(queryStr);
        query.setParameter(1, demographicNo);
        List<String> lst = new ArrayList<String>();
        for (int x = 0; x < types.length; x++) {
            lst.add(types[x]);
        }
        query.setParameter(2, lst);

        List<Measurement> results = query.getResultList();

        for (Measurement m : results) {
            map.put(m.getType(), m);
        }
        return map;
    }

    @Override
    public Set<Integer> getAppointmentNosByDemographicNoAndType(int demographicNo, String type, Date startDate,
                                                                Date endDate) {
        Map<Integer, Boolean> results = new HashMap<Integer, Boolean>();

        String queryStr = "select m from  Measurement m WHERE m.demographicId = ?1 and m.type=?2 and m.dateObserved>=?3 and m.dateObserved<=?4 ORDER BY m.dateObserved DESC";
        Query query = entityManager.createQuery(queryStr);
        query.setParameter(1, demographicNo);
        query.setParameter(2, type);
        query.setParameter(3, startDate);
        query.setParameter(4, endDate);

        List<Measurement> rs = query.getResultList();
        for (Measurement m : rs) {
            results.put(m.getAppointmentNo(), true);
        }

        return results.keySet();
    }

    @Override
    public HashMap<String, Measurement> getMeasurementsPriorToDate(Integer demographicNo, Date d) {
        String queryStr = "select m From Measurement m WHERE m.demographicId = ?1 AND m.dateObserved <= ?2";
        Query query = entityManager.createQuery(queryStr);
        query.setParameter(1, demographicNo);
        query.setParameter(2, d);

        List<Measurement> rs = query.getResultList();

        HashMap<String, Measurement> map = new HashMap<String, Measurement>();

        for (Measurement m : rs) {
            map.put(m.getType(), m);
        }

        return map;
    }

    @Override
    public List<Date> getDatesForMeasurements(Integer demographicNo, String[] types) {
        List<String> lst = new ArrayList<String>();

        for (String type : types) {
            lst.add(type);
        }

        String queryStr = "SELECT DISTINCT m.dateObserved FROM Measurement m WHERE m.demographicId = ?1 AND m.type IN (?2) ORDER BY m.dateObserved DESC";

        Query query = entityManager.createQuery(queryStr);
        query.setParameter(1, demographicNo);
        query.setParameter(2, lst);

        List<Date> results = query.getResultList();

        return results;
    }

    /**
     * Finds abnormal measurements for the specified patient
     *
     * @param demoNo    Patient ID
     * @param loincCode LOINC Code
     * @return Returns a list of tuples containing record data, observation date,
     * lab no, abnormal value.
     */

    @Override
    public List<Object[]> findMeasurementsByDemographicIdAndLocationCode(Integer demoNo, String loincCode) {
        String sql = "SELECT m.dataField, m.dateObserved, e1.val, e3.val "
                + "FROM Measurement m, MeasurementsExt e1, MeasurementsExt e2, MeasurementsExt e3, MeasurementMap mm "
                + "WHERE m.id = e1.measurementId " + "AND e1.keyVal = 'lab_no' " + "AND m.id = e2.measurementId "
                + "AND e2.keyVal = 'identifier' " + "AND m.id = e3.measurementId " + "AND e3.keyVal = 'abnormal' "
                + "AND e2.val = mm.identCode " + "AND mm.loincCode = ?1" + "AND m.demographicId = ?2"
                + "ORDER BY m.dateObserved DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, loincCode);
        query.setParameter(2, demoNo);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(Integer demoNo,
                                                                                        String loincCode) {
        String sql = "SELECT m.dataField, m.dateObserved, e1.val, e3.val, e4.val "
                + "FROM Measurement m, MeasurementsExt e1, MeasurementsExt e2, MeasurementsExt e3, MeasurementsExt e4, MeasurementMap mm "
                + "WHERE m.id = e1.measurementId " + "AND e1.keyVal = 'lab_no' " + "AND m.id = e2.measurementId "
                + "AND e2.keyVal='identifier'" + "AND m.id = e4.measurementId " + "AND e4.keyVal='identifier' "
                + "AND m.id = e3.measurementId " + "AND e3.keyVal='abnormal' " + "AND e2.val = mm.identCode "
                + "AND mm.loincCode = ?1" + "AND m.demographicId = ?2" + "ORDER BY m.dateObserved DESC";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, loincCode);
        query.setParameter(2, demoNo);
        return query.getResultList();

    }

    @Override
    public List<Object> findLabNumbers(Integer demoNo, String identCode) {
        String sql = "SELECT DISTINCT e2.val FROM Measurement m, MeasurementsExt e1, MeasurementsExt e2 "
                + "WHERE m.id = e1.measurementId " + "AND e1.keyVal = 'identifier' " + "AND m.id = e2.measurementId "
                + "AND e2.keyVal = 'lab_no' " + "AND e1.val= ?1" + "AND m.demographicId = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, identCode);
        query.setParameter(2, demoNo);
        return query.getResultList();
    }

    @Override
    public List<Object> findLabNumbersOrderByObserved(Integer demoNo, String identCode) {
        String sql = "SELECT DISTINCT e2.val FROM Measurement m, MeasurementsExt e1, MeasurementsExt e2 "
                + "WHERE m.id = e1.measurementId " + "AND e1.keyVal = 'identifier' " + "AND m.id = e2.measurementId "
                + "AND e2.keyVal = 'lab_no' " + "AND e1.val= ?1"
                + "AND m.demographicId = ?2 ORDER BY m.dateObserved";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, identCode);
        query.setParameter(2, demoNo);
        return query.getResultList();
    }

    @Override
    public Measurement findLastEntered(Integer demo, String type) {
        Query query = createQuery("ms", "ms.demographicId = ?1 AND ms.type = ?2 ORDER BY ms.createDate DESC");
        query.setParameter(1, demo);
        query.setParameter(2, type);
        return getSingleResultOrNull(query);
    }

    @Override
    public List<MeasurementType> findMeasurementsTypes(Integer demoNo) {
        String sql = "select mt.* from measurementType AS mt LEFT JOIN measurementType AS mt2 on (mt.type = mt2.type AND mt.id < mt2.id) LEFT JOIN measurements as m ON m.type = mt.type WHERE mt2.id is null and m.demographicNo = ?1 group by mt.type order by mt.id DESC";
        Query query = entityManager.createNativeQuery(sql, MeasurementType.class);
        query.setParameter(1, demoNo);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findMeasurementsAndProviders(Integer measurementId) {
        String sql = "SELECT m, mt, p FROM Measurement m, MeasurementType mt, Provider p WHERE m.providerNo = p.ProviderNo AND m.id = ?1 AND m.type = mt.type";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, measurementId);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findMeasurementsAndProvidersByType(String type, Integer demographicNo) {
        String sql = "SELECT m, p, mt FROM Measurement m, Provider p, MeasurementType mt WHERE m.providerNo = p.ProviderNo AND m.type = mt.type AND m.type = ?1 AND m.demographicId = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, type);
        query.setParameter(2, demographicNo);
        return query.getResultList();
    }

    @Override
    public Object[] findMeasurementsAndProvidersByDemoAndType(Integer demographicNo, String type) {
        String sql = "SELECT m, p, mt FROM Measurement m, Provider p, MeasurementType mt WHERE m.demographicId = ?1 AND m.type = ?2 AND (m.providerNo = p.ProviderNo OR m.providerNo = '0') AND m.type = mt.type GROUP BY m.id ORDER BY m.dateObserved DESC, m.createDate DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, type);
        query.setMaxResults(1);

        List<Object[]> result = query.getResultList();
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    @Override
    public List<Measurement> findByValue(String key, String value) {
        Query q = entityManager.createQuery("SELECT m FROM Measurement m, MeasurementsExt e "
                + "WHERE m.id = e.measurementId " + "AND e.keyVal = ?1 " + "AND e.val = ?2");
        q.setParameter(1, key);
        q.setParameter(2, value);
        return q.getResultList();
    }

    @Override
    public List<Object> findObservationDatesByDemographicNoTypeAndMeasuringInstruction(Integer demo, String type,
                                                                                       String mInstrc) {
        String sql = "SELECT DISTINCT m.dateObserved FROM Measurement m " + "WHERE m.demographicId = ?1 "
                + "AND m.type = ?2 " + "AND m.measuringInstruction = ?3 " + "ORDER BY m.dateObserved";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demo);
        query.setParameter(2, type);
        query.setParameter(3, mInstrc);
        return query.getResultList();
    }

    @Override
    public List<Date> findByDemographicNoTypeAndMeasuringInstruction(Integer demo, String type, String mInstrc) {
        String sql = "SELECT m.dateObserved FROM Measurement m " + "WHERE m.demographicId = ?1"
                + "AND m.type = ?2" + "AND m.measuringInstruction = ?3" + "ORDER BY m.dateObserved";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demo);
        query.setParameter(2, type);
        query.setParameter(3, mInstrc);
        return query.getResultList();
    }

    @Override
    public Measurement findByDemographicNoTypeAndDate(Integer demo, String type, java.util.Date date) {
        String sql = "FROM Measurement m WHERE m.demographicId = ?1" + "AND m.type = ?2"
                + "AND m.dateObserved = ?3" + "ORDER BY m.createDate DESC";
        Query query = entityManager.createQuery(sql);
        query.setMaxResults(1);
        query.setParameter(1, demo);
        query.setParameter(2, type);
        query.setParameter(3, date);
        return getSingleResultOrNull(query);
    }

    @Override
    public List<Measurement> findByDemoNoTypeDateAndMeasuringInstruction(Integer demoNo, Date from, Date to,
                                                                         String type, String instruction) {
        Query query = createQuery("m", "m.dateObserved >= ?1 AND m.dateObserved <= ?2 AND m.type = ?3"
                + "AND m.measuringInstruction = ?4 AND m.demographicId = ?5");
        query.setParameter(1, from);
        query.setParameter(2, to);
        query.setParameter(3, type);
        query.setParameter(4, instruction);
        query.setParameter(5, demoNo);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findLastEntered(Date from, Date to, String measurementType, String mInstrc) {
        Query query = createQuery("SELECT m.demographicId, max(m.createDate)", "m",
                "m.dateObserved >= ?1 AND m.dateObserved <= ?2 AND m.type = ?3 AND m.measuringInstruction = ?4 group by m.demographicId");
        query.setParameter(1, from);
        query.setParameter(2, to);
        query.setParameter(3, measurementType);
        query.setParameter(4, mInstrc);
        return query.getResultList();
    }

    @Override
    public List<Measurement> findByDemographicNoTypeAndDate(Integer demographicNo, Date createDate,
                                                            String measurementType, String mInstrc) {
        String sql = "FROM Measurement m " + "WHERE m.createDate = ?1"
                + "AND m.demographicId = ?2" + "AND m.type = ?3"
                + "AND m.measuringInstruction = ?4";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, createDate);
        query.setParameter(2, demographicNo);
        query.setParameter(3, measurementType);
        query.setParameter(4, mInstrc);
        return query.getResultList();
    }

    @NativeSql("measurements")
    @Override
    public List<Object[]> findByDemoNoDateTypeMeasuringInstrAndDataField(Integer demographicNo, Date dateEntered,
                                                                         String measurementType, String mInstrc, String upper, String lower) {
        String sql = "SELECT dataField FROM measurements " + "WHERE dateEntered = ?1"
                + "AND demographicNo = ?2" + "AND type = ?3"
                + "AND measuringInstruction = ?4" + "AND dataField < ?5" + "AND dataField > ?6";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, dateEntered);
        query.setParameter(2, demographicNo);
        query.setParameter(3, measurementType);
        query.setParameter(4, mInstrc);
        query.setParameter(5, upper);
        query.setParameter(6, lower);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findLastEntered(Date from, Date to, String measurementType) {
        Query query = createQuery("SELECT m.demographicId, MAX(m.createDate)", "m",
                "m.dateObserved >= ?1 AND m.dateObserved <= ?2 AND m.type = ?3 GROUP BY m.demographicId");
        query.setParameter(1, from);
        query.setParameter(2, to);
        query.setParameter(3, measurementType);
        return query.getResultList();
    }

    @Override
    public List<Measurement> findByDemoNoDateAndType(Integer demoNo, Date createDate, String type) {
        Query query = createQuery("m", "m.createDate = ?1 AND m.demographicId = ?2 AND m.type = ?3");
        query.setParameter(1, createDate);
        query.setParameter(2, demoNo);
        query.setParameter(3, type);
        return query.getResultList();
    }

    @NativeSql("measurements")
    @Override
    public List<Object[]> findByDemoNoDateTypeAndDataField(Integer demographicNo, Date dateEntered, String type,
                                                           String upper, String lower) {
        String sql = "SELECT dataField FROM measurements WHERE dateEntered = ?1"
                + "AND demographicNo = ?2" + "AND type = ?3" + "AND dataField < ?4"
                + "AND dataField > ?5";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, dateEntered);
        query.setParameter(2, demographicNo);
        query.setParameter(3, type);
        query.setParameter(4, upper);
        query.setParameter(5, lower);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findTypesAndMeasuringInstructionByDemographicId(Integer demoNo) {
        Query query = createQuery("SELECT DISTINCT m.type, m.measuringInstruction", "m", "m.demographicId = ?1");
        query.setParameter(1, demoNo);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findByCreateDate(Date from, Date to) {
        Query query = createQuery("SELECT DISTINCT m.demographicId", "m",
                "m.createDate >= ?1 AND m.createDate <= ?2");
        query.setParameter(1, from);
        query.setParameter(2, to);
        return query.getResultList();
    }

    @Override
    public List<Measurement> findByType(Integer demographicId, String type) {
        String sqlCommand = "select x from Measurement x where x.demographicId = ?1 and x.type = ?2 order by x.dateObserved desc";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, type);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByType(Integer demographicId, String type, Date after) {
        String sqlCommand = "select x from Measurement x where x.demographicId = ?1 and x.type = ?2 and x.dateObserved >= ?3 order by x.dateObserved desc";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, type);
        query.setParameter(3, after);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByType(Integer demographicId, List<String> types) {
        String sqlCommand = "select x from Measurement x where x.demographicId = ?1 and x.type IN (?2) order by x.dateObserved desc";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, types);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByType(Integer demographicId, List<String> types, Date after) {
        String sqlCommand = "select x from Measurement x where x.demographicId = ?1 and x.type IN (?2) and x.dateObserved >= ?3 order by x.dateObserved desc";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, types);
        query.setParameter(3, after);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByType(String type) {
        String sqlCommand = "select x from Measurement x where x.type = ?1 order by x.demographicId, x.dateObserved desc";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, type);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByType(List<String> types) {
        String sqlCommand = "select x from Measurement x where x.type in (?1) order by x.demographicId, x.dateObserved desc";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, types);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Integer> findDemographicIdsByType(List<String> types) {
        String sqlCommand = "select distinct x.demographicId from Measurement x where x.type in (?1)";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, types);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByTypeBefore(Integer demographicId, String type, Date date) {
        String sqlCommand = "select x from Measurement x where x.demographicId = ?1 and x.type = ?2 and x.dateObserved <= ?3 order by x.dateObserved desc";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, type);
        query.setParameter(3, date);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();

        return results;
    }

    @Override
    public List<Measurement> findByProviderDemographicLastUpdateDate(String providerNo, Integer demographicId,
                                                                     Date updatedAfterThisDateExclusive, int itemsToReturn) {
        String sql = "select x from " + modelClass.getSimpleName()
                + " x where x.providerNo=?1 and x.demographicId=?2 and x.createDate>?3 order by x.createDate";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, providerNo);
        query.setParameter(2, demographicId);
        query.setParameter(3, updatedAfterThisDateExclusive);

        setLimit(query, itemsToReturn);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();
        return results;
    }

    @Override
    public List<Measurement> findByDemographicLastUpdateAfterDate(Integer demographicId,
                                                                  Date updatedAfterThisDateExclusive) {
        String sql = "select x from " + modelClass.getSimpleName()
                + " x where x.demographicId=?1 and x.createDate>?2 order by x.createDate";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicId);
        query.setParameter(2, updatedAfterThisDateExclusive);

        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();
        return results;
    }

    @NativeSql("measurements")
    @Override
    public List<Measurement> findLatestByDemographicObservedAfterDate(Integer demographicId,
                                                                      Date observedAfterDateExclusive) {
        String sql = "SELECT x.* FROM measurements x LEFT JOIN measurements y ON x.dateObserved < y.dateObserved AND x.type = y.type AND x.demographicNo = y.demographicNo WHERE y.id IS NULL AND x.demographicNo = ?1 AND x.dateObserved > ?2 GROUP BY x.type, x.dateObserved ORDER BY x.dateObserved DESC";
        Query query = entityManager.createNativeQuery(sql, Measurement.class);
        query.setParameter(1, demographicId);
        query.setParameter(2, observedAfterDateExclusive);
        @SuppressWarnings("unchecked")
        List<Measurement> results = query.getResultList();
        return results;
    }

    @NativeSql("measurements")
    @Override
    public List<Integer> findNewMeasurementsSinceDemoKey(String keyName) {

        String sql = "select distinct m.demographicNo from measurements m,demographic d,demographicExt e where m.demographicNo = d.demographic_no and d.demographic_no = e.demographic_no and e.key_val=?1 and m.type in ('HT','WT') and m.dateEntered > e.value";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, keyName);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Measurement> findMeasurementByTypeAndDate(Integer demoNo, String type, Date start, Date end) {
        String sqlCommand = "select x from Measurement x where  x.demographicId = ?1 and  x.type = ?2 and x.dateObserved >= ?3 and x.dateObserved <= ?4 order by x.dateObserved DESC";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demoNo);
        query.setParameter(2, type);
        query.setParameter(3, start);
        query.setParameter(4, end);
        return query.getResultList();
    }
}
