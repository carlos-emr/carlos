//CHECKSTYLE:OFF
package ca.openosp.openo.services;

import ca.openosp.openo.commn.model.Demographic;

import java.util.List;

/**
 * Service interface for managing patient demographic information in the OpenO EMR system.
 *
 * <p>This interface provides core operations for accessing and retrieving patient demographic
 * data, including individual patient records, demographic collections, and program enrollment
 * information. Patient demographics form the foundation of the healthcare record system and
 * include essential information such as patient identification, Health Insurance Number (HIN),
 * contact details, and rostering status.</p>
 *
 * <p>The DemographicManager serves as a service layer abstraction over the data access layer,
 * providing business logic and healthcare-specific operations for managing patient information
 * in compliance with Canadian healthcare regulations (PIPEDA/HIPAA).</p>
 *
 * @see ca.openosp.openo.commn.model.Demographic
 * @see ca.openosp.openo.commn.dao.DemographicDao
 * @since 2026-01-24
 */
public interface DemographicManager {
    /**
     * Retrieves a single patient demographic record by demographic number.
     *
     * <p>This method fetches the complete demographic record for a patient identified
     * by their unique demographic number. The demographic number is the primary identifier
     * used throughout the OpenO EMR system for patient records.</p>
     *
     * @param demographic_no String the unique demographic identifier for the patient
     * @return Demographic the patient demographic record, or null if not found
     */
    Demographic getDemographic(String demographic_no);

    /**
     * Retrieves all demographic records in the system.
     *
     * <p>This method returns a collection of all patient demographic records. Due to
     * potential performance implications with large patient populations, use with caution
     * and consider implementing pagination or filtering for production use.</p>
     *
     * @return List list of all Demographic records in the system
     */
    List getDemographics();

    /**
     * Retrieves all program identifiers associated with a specific patient.
     *
     * <p>This method returns a list of program IDs that the patient is enrolled in,
     * supporting OpenO's program management functionality for case management, community
     * programs, and specialized healthcare initiatives.</p>
     *
     * @param demoNo String the demographic number of the patient
     * @return List list of Integer program IDs associated with the patient
     */
    List getProgramIdByDemoNo(String demoNo);

    /**
     * Retrieves demographic program enrollment records for a specific patient.
     *
     * <p>This method returns detailed program enrollment information for a patient,
     * including program assignments, enrollment dates, and program-specific data used
     * in the PMmodule (Program Management module) for tracking patient participation
     * in healthcare programs.</p>
     *
     * @param demoNo Integer the demographic number of the patient
     * @return List list of demographic program enrollment records
     */
    List getDemoProgram(Integer demoNo);
}
