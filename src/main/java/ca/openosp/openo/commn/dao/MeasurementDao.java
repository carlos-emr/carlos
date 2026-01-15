//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.commn.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.commn.model.MeasurementType;
import ca.openosp.openo.commn.dao.MeasurementDaoImpl.SearchCriteria;

/**
 * Data Access Object interface for {@link Measurement} entities providing comprehensive
 * database operations for clinical measurement data in OpenO EMR.
 * 
 * <p>This DAO provides extensive query capabilities for retrieving measurements by various criteria
 * including patient demographics, measurement types, date ranges, appointments, and providers.
 * It serves as the primary data access layer for the measurement system.</p>
 * 
 * <p>Key functionality groups:</p>
 * <ul>
 *   <li><strong>Patient-based queries</strong>: Find measurements by demographic ID, type, and date ranges</li>
 *   <li><strong>Appointment-based queries</strong>: Retrieve measurements associated with specific appointments</li>
 *   <li><strong>Provider-based queries</strong>: Find measurements by provider and update tracking</li>
 *   <li><strong>Laboratory integration</strong>: Support for LOINC codes and lab-specific identifiers</li>
 *   <li><strong>Temporal queries</strong>: Date-based filtering and chronological ordering</li>
 *   <li><strong>Type-specific queries</strong>: Operations focused on specific measurement types</li>
 * </ul>
 * 
 * <p>Common usage patterns:</p>
 * <pre>{@code
 * // Get all measurements for a patient
 * List<Measurement> measurements = measurementDao.findByDemographicNo(demographicId);
 * 
 * // Get specific measurement type for patient
 * List<Measurement> bloodPressure = measurementDao.findByDemographicNoAndType(demographicId, "BP");
 * 
 * // Get latest measurement of specific type
 * Measurement latest = measurementDao.findLatestByDemographicNoAndType(demographicId, "WT");
 * 
 * // Get measurements within date range
 * List<Measurement> recent = measurementDao.findByDemographicIdObservedDate(demographicId, startDate, endDate);
 * }</pre>
 * 
 * @since 2006
 * @see Measurement
 * @see MeasurementType
 * @see MeasurementDaoImpl
 * @see ca.openosp.openo.managers.MeasurementManager
 */
public interface MeasurementDao extends AbstractDao<Measurement> {

    /**
     * Finds measurements for a specific patient that were updated after a given date.
     * Useful for synchronization and incremental data retrieval.
     * 
     * @param demographicId Integer the patient's demographic ID
     * @param updatedAfterThisDate Date the cutoff date for updates
     * @return List<Measurement> measurements updated after the specified date
     */
    public List<Measurement> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date updatedAfterThisDate);

    /**
     * Finds measurements created after a specific date, with a limit on results.
     * Date parameter is exclusive (measurements created exactly on this date are not included).
     * 
     * @param updatedAfterThisDateExclusive Date the exclusive cutoff date
     * @param itemsToReturn int maximum number of measurements to return
     * @return List<Measurement> up to the specified number of measurements
     */
    public List<Measurement> findByCreateDate(Date updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Finds demographic IDs of patients who have measurements updated after a given date.
     * Useful for determining which patients have measurement changes.
     * 
     * @param updatedAfterThisDate Date the cutoff date for updates
     * @return List<Integer> demographic IDs with measurement updates
     */
    public List<Integer> findDemographicIdsUpdatedAfterDate(Date updatedAfterThisDate);

    /**
     * Finds measurements that match the properties of the given measurement template.
     * Uses non-null properties of the template for matching criteria.
     * 
     * @param measurement Measurement template with criteria to match
     * @return List<Measurement> measurements matching the template properties
     */
    public List<Measurement> findMatching(Measurement measurement);

    /**
     * Finds all measurements for a specific patient.
     * One of the most commonly used methods for retrieving patient measurement history.
     * 
     * @param demographicNo Integer the patient's demographic ID
     * @return List<Measurement> all measurements for the patient, typically ordered by date
     */
    public List<Measurement> findByDemographicNo(Integer demographicNo);

    /**
     * Finds all measurements of a specific type for a specific patient.
     * Commonly used for retrieving measurement trends (e.g., blood pressure over time).
     * 
     * @param demographicNo Integer the patient's demographic ID
     * @param type String the measurement type code (e.g., "BP", "WT", "HT")
     * @return List<Measurement> all measurements of the specified type for the patient
     */
    public List<Measurement> findByDemographicNoAndType(Integer demographicNo, String type);

    /**
     * Finds the most recent measurement of a specific type for a patient.
     * Essential for displaying current values in patient charts and clinical decision support.
     * 
     * @param demographicNo int the patient's demographic ID
     * @param type String the measurement type code
     * @return Measurement the most recent measurement of the specified type, or null if none found
     */
    public Measurement findLatestByDemographicNoAndType(int demographicNo, String type);

    /**
     * Finds all measurements recorded during a specific appointment.
     * Used for encounter-based measurement retrieval and reporting.
     * 
     * @param appointmentNo Integer the appointment ID
     * @return List<Measurement> measurements recorded during the appointment
     */
    public List<Measurement> findByAppointmentNo(Integer appointmentNo);

    public List<Measurement> findByAppointmentNo2(Integer appointmentNo);

    public List<Measurement> findByAppointmentNoAndType(Integer appointmentNo, String type);

    public Measurement findLatestByAppointmentNoAndType(int appointmentNo, String type);

    /**
     * Finds measurements for a patient within a specific observation date range.
     * Uses the dateObserved field (clinical date) rather than system entry date.
     * 
     * @param demographicId Integer the patient's demographic ID
     * @param startDate Date the start of the observation date range (inclusive)
     * @param endDate Date the end of the observation date range (inclusive)
     * @return List<Measurement> measurements observed within the date range
     */
    public List<Measurement> findByDemographicIdObservedDate(Integer demographicId, Date startDate, Date endDate);

    public List<Measurement> findByDemographicId(Integer demographicId);

    public List<Measurement> find(SearchCriteria criteria);

    public List<Measurement> findByIdTypeAndInstruction(Integer demographicId, String type, String instructions);

    /**
     * Gets the latest measurement for each specified type for a patient.
     * Returns a map with measurement type as key and latest measurement as value.
     * 
     * @param demographicNo Integer the patient's demographic ID
     * @param types String[] array of measurement type codes to retrieve
     * @return map of type codes to their latest measurements
     */
    public HashMap<String, Measurement> getMeasurements(Integer demographicNo, String[] types);

    public Set<Integer> getAppointmentNosByDemographicNoAndType(int demographicNo, String type, Date startDate,
                                                                Date endDate);

    public HashMap<String, Measurement> getMeasurementsPriorToDate(Integer demographicNo, Date d);

    public List<Date> getDatesForMeasurements(Integer demographicNo, String[] types);

    /**
     * Finds measurements for a patient by LOINC code for laboratory integration.
     * Returns Object arrays containing measurement data and related information.
     * 
     * @param demoNo Integer the patient's demographic ID
     * @param loincCode String the LOINC code to search for
     * @return measurement data arrays matching the LOINC code
     */
    public List<Object[]> findMeasurementsByDemographicIdAndLocationCode(Integer demoNo, String loincCode);

    public List<Object[]> findMeasurementsWithIdentifiersByDemographicIdAndLocationCode(Integer demoNo,
                                                                                        String loincCode);

    public List<Object> findLabNumbers(Integer demoNo, String identCode);

    public List<Object> findLabNumbersOrderByObserved(Integer demoNo, String identCode);

    /**
     * Finds the last measurement entered into the system for a patient and type.
     * Based on system entry date (createDate) rather than clinical observation date.
     * 
     * @param demo Integer the patient's demographic ID
     * @param type String the measurement type code
     * @return Measurement the most recently entered measurement, or null if none found
     */
    public Measurement findLastEntered(Integer demo, String type);

    /**
     * Finds all measurement types that have been used for a specific patient.
     * Useful for determining what types of measurements have been recorded.
     * 
     * @param demoNo Integer the patient's demographic ID
     * @return List<MeasurementType> measurement types used for this patient
     */
    public List<MeasurementType> findMeasurementsTypes(Integer demoNo);

    public List<Object[]> findMeasurementsAndProviders(Integer measurementId);

    public List<Object[]> findMeasurementsAndProvidersByType(String type, Integer demographicNo);

    public Object[] findMeasurementsAndProvidersByDemoAndType(Integer demographicNo, String type);

    public List<Measurement> findByValue(String key, String value);

    public List<Object> findObservationDatesByDemographicNoTypeAndMeasuringInstruction(Integer demo, String type,
                                                                                       String mInstrc);

    public List<Date> findByDemographicNoTypeAndMeasuringInstruction(Integer demo, String type, String mInstrc);

    public Measurement findByDemographicNoTypeAndDate(Integer demo, String type, java.util.Date date);

    public List<Measurement> findByDemoNoTypeDateAndMeasuringInstruction(Integer demoNo, Date from, Date to,
                                                                         String type, String instruction);

    public List<Object[]> findLastEntered(Date from, Date to, String measurementType, String mInstrc);

    public List<Measurement> findByDemographicNoTypeAndDate(Integer demographicNo, Date createDate,
                                                            String measurementType, String mInstrc);

    public List<Object[]> findByDemoNoDateTypeMeasuringInstrAndDataField(Integer demographicNo, Date dateEntered,
                                                                         String measurementType, String mInstrc, String upper, String lower);

    public List<Object[]> findLastEntered(Date from, Date to, String measurementType);

    public List<Measurement> findByDemoNoDateAndType(Integer demoNo, Date createDate, String type);

    public List<Object[]> findByDemoNoDateTypeAndDataField(Integer demographicNo, Date dateEntered, String type,
                                                           String upper, String lower);

    public List<Object[]> findTypesAndMeasuringInstructionByDemographicId(Integer demoNo);

    public List<Object[]> findByCreateDate(Date from, Date to);

    /**
     * Finds all measurements of a specific type for a patient.
     * Alias method providing alternative signature for demographic-type queries.
     * 
     * @param demographicId Integer the patient's demographic ID
     * @param type String the measurement type code
     * @return List<Measurement> all measurements of the specified type
     */
    public List<Measurement> findByType(Integer demographicId, String type);

    /**
     * Finds measurements of a specific type for a patient after a given date.
     * Combines patient, type, and temporal filtering.
     * 
     * @param demographicId Integer the patient's demographic ID
     * @param type String the measurement type code
     * @param after Date the cutoff date (measurements after this date)
     * @return List<Measurement> measurements of the specified type after the date
     */
    public List<Measurement> findByType(Integer demographicId, String type, Date after);

    /**
     * Finds measurements of multiple types for a patient.
     * Efficient way to retrieve several measurement types in a single query.
     * 
     * @param demographicId Integer the patient's demographic ID
     * @param types List<String> list of measurement type codes to retrieve
     * @return List<Measurement> measurements matching any of the specified types
     */
    public List<Measurement> findByType(Integer demographicId, List<String> types);

    public List<Measurement> findByType(Integer demographicId, List<String> types, Date after);

    public List<Measurement> findByType(String type);

    public List<Measurement> findByType(List<String> types);

    public List<Integer> findDemographicIdsByType(List<String> types);

    public List<Measurement> findByTypeBefore(Integer demographicId, String type, Date date);

    public List<Measurement> findByProviderDemographicLastUpdateDate(String providerNo, Integer demographicId,
                                                                     Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Measurement> findByDemographicLastUpdateAfterDate(Integer demographicId,
                                                                  Date updatedAfterThisDateExclusive);

    public List<Measurement> findLatestByDemographicObservedAfterDate(Integer demographicId,
                                                                      Date observedAfterDateExclusive);

    public List<Integer> findNewMeasurementsSinceDemoKey(String keyName);

    /**
     * Finds measurements of a specific type within a date range for a patient.
     * Combines patient, type, and date range filtering for focused queries.
     * 
     * @param demoNo Integer the patient's demographic ID
     * @param type String the measurement type code
     * @param start Date the start of the date range
     * @param end Date the end of the date range
     * @return List<Measurement> measurements matching all criteria
     */
    public List<Measurement> findMeasurementByTypeAndDate(Integer demoNo, String type, Date start, Date end);
}
