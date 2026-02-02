//CHECKSTYLE:OFF
package ca.openosp.openo.services;

import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.model.Demographic;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of the DemographicManager service interface providing patient demographic management
 * functionality within the OpenO EMR system.
 *
 * <p>This service layer implementation delegates to {@link DemographicDao} for data access operations
 * and provides transactional support for patient demographic data management. It handles patient record
 * retrieval and program enrollment queries for healthcare providers across the EMR system.</p>
 *
 * <p>All methods in this class are transactional, ensuring data consistency when accessing patient
 * health information (PHI) and maintaining HIPAA/PIPEDA compliance requirements.</p>
 *
 * @since 2026-01-23
 * @see DemographicManager
 * @see DemographicDao
 * @see ca.openosp.openo.commn.model.Demographic
 */
@Transactional
public class DemographicManagerImpl implements DemographicManager {

    private DemographicDao demographicDao = null;

    /**
     * Sets the data access object for demographic operations.
     *
     * <p>This setter method is used by Spring's dependency injection framework to inject
     * the {@link DemographicDao} implementation at runtime.</p>
     *
     * @param demographicDao {@link DemographicDao} the demographic data access object to use for database operations
     */
    public void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    /**
     * Retrieves a patient demographic record by their demographic number.
     *
     * <p>The demographic number is the unique identifier for a patient within the OpenO EMR system.
     * This method retrieves the complete patient record including personal information, contact details,
     * health insurance number (HIN), and rostering status.</p>
     *
     * @param demographic_no {@link String} the unique demographic identifier for the patient
     * @return {@link Demographic} the patient demographic record, or null if no patient exists with the given number
     */
    @Override
    public Demographic getDemographic(String demographic_no) {
        return demographicDao.getDemographic(demographic_no);
    }

    /**
     * Retrieves all patient demographic records from the system.
     *
     * <p>This method returns a complete list of all patient records in the EMR database.
     * Use with caution as this can return a large dataset in production environments with
     * extensive patient populations.</p>
     *
     * @return {@link List} of {@link Demographic} objects representing all patients in the system
     */
    @Override
    public List getDemographics() {
        return demographicDao.getDemographics();
    }

    /**
     * Retrieves program IDs associated with a specific patient demographic.
     *
     * <p>This method returns all program identifiers that a patient is enrolled in within the
     * OpenO EMR system. Programs may include case management programs, community health initiatives,
     * or specialized care programs defined in the PMmodule.</p>
     *
     * @param demoNo {@link String} the demographic number of the patient as a string
     * @return {@link List} of program IDs (as Integer objects) associated with the patient
     */
    @Override
    public List getProgramIdByDemoNo(String demoNo) {
        return demographicDao.getProgramIdByDemoNo(Integer.parseInt(demoNo));
    }

    /**
     * Retrieves program enrollment records for a specific patient demographic.
     *
     * <p>This method returns detailed program enrollment information for a patient, including
     * enrollment dates, program status, and associated metadata. The records include both active
     * and historical program enrollments.</p>
     *
     * @param demoNo {@link Integer} the demographic number of the patient
     * @return {@link List} of program enrollment objects associated with the patient
     */
    @Override
    public List getDemoProgram(Integer demoNo) {
        return demographicDao.getDemoProgram(demoNo);
    }
}
