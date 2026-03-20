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

package io.github.carlos_emr.carlos.managers;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;

/**
 * Service interface for high-level measurement operations in OpenO EMR.
 * This manager provides business logic layer operations for clinical measurements,
 * abstracting complex database operations and providing security-aware access to measurement data.
 * 
 * <p>The MeasurementManager serves as the primary business service for:</p>
 * <ul>
 *   <li><strong>Data retrieval</strong>: Security-aware measurement queries with proper authorization</li>
 *   <li><strong>Data creation</strong>: Adding new measurements with validation and audit trails</li>
 *   <li><strong>Integration support</strong>: Methods for measurement mapping and external system integration</li>
 *   <li><strong>Flowsheet management</strong>: Dynamic flowsheet generation and HTML template handling</li>
 *   <li><strong>Configuration management</strong>: Measurement-related property and setting management</li>
 * </ul>
 * 
 * <p>All methods require {@link LoggedInInfo} for security context and audit logging,
 * ensuring that measurement access is properly authenticated and authorized according to
 * healthcare privacy requirements.</p>
 * 
 * <p>Common usage patterns:</p>
 * <pre>{@code
 * // Get measurements with security context
 * List<Measurement> measurements = measurementManager.getMeasurementByType(
 *     loggedInInfo, demographicId, Arrays.asList("BP", "WT"));
 * 
 * // Add new measurement with audit trail
 * Measurement newMeasurement = new Measurement();
 * // ... set measurement properties
 * Measurement saved = measurementManager.addMeasurement(loggedInInfo, newMeasurement);
 * 
 * // Get recent measurements for patient
 * List<Measurement> recent = measurementManager.getMeasurementByDemographicIdAfter(
 *     loggedInInfo, demographicId, thirtyDaysAgo);
 * }</pre>
 * 
 * @since 2006
 * @see Measurement
 * @see MeasurementMap  
 * @see io.github.carlos_emr.carlos.commn.dao.MeasurementDao
 * @see MeasurementManagerImpl
 */
public interface MeasurementManager {

    /**
     * Retrieves measurements created after a specific date with security authorization.
     * Supports pagination for large datasets and incremental synchronization scenarios.
     * 
     * @param loggedInInfo LoggedInInfo security context for authorization and audit
     * @param updatedAfterThisDateExclusive Date cutoff date (exclusive) for measurement creation
     * @param itemsToReturn int maximum number of measurements to return
     * @return List<Measurement> measurements created after the specified date
     */
    public List<Measurement> getCreatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive,
                                                 int itemsToReturn);

    /**
     * Retrieves a single measurement by ID with proper security authorization.
     * 
     * @param loggedInInfo LoggedInInfo security context for authorization and audit
     * @param id Integer the measurement ID to retrieve
     * @return Measurement the requested measurement, or null if not found or not authorized
     */
    public Measurement getMeasurement(LoggedInInfo loggedInInfo, Integer id);

    /**
     * Retrieves measurements of specific types for a patient with security authorization.
     * Essential method for clinical workflows requiring specific measurement categories.
     * 
     * @param loggedInInfo LoggedInInfo security context for authorization and audit
     * @param id Integer the patient's demographic ID
     * @param types List<String> measurement type codes to retrieve (e.g., ["BP", "WT", "HT"])
     * @return List<Measurement> measurements of the specified types for the patient
     */
    public List<Measurement> getMeasurementByType(LoggedInInfo loggedInInfo, Integer id, List<String> types);

    /**
     * Retrieves measurements for a patient updated after a specific date.
     * Useful for tracking measurement changes and synchronization workflows.
     * 
     * @param loggedInInfo LoggedInInfo security context for authorization and audit
     * @param demographicId Integer the patient's demographic ID
     * @param updateAfter Date cutoff date for measurement updates
     * @return List<Measurement> measurements for the patient updated after the date
     */
    public List<Measurement> getMeasurementByDemographicIdAfter(LoggedInInfo loggedInInfo, Integer demographicId,
                                                                Date updateAfter);

    /**
     * Retrieves all measurement mapping configurations for laboratory integration.
     * Returns mappings between internal measurement types and external codes (LOINC, etc.).
     * 
     * @return List<MeasurementMap> all configured measurement mappings
     */
    public List<MeasurementMap> getMeasurementMaps();

    /**
     * Adds a new measurement to the system with security validation and audit logging.
     * Performs validation, authorization checks, and maintains audit trails.
     * 
     * @param loggedInInfo LoggedInInfo security context for authorization and audit
     * @param measurement Measurement the measurement data to add
     * @return Measurement the persisted measurement with generated ID and audit information
     * @throws SecurityException if user lacks permission to add measurements for this patient
     */
    public Measurement addMeasurement(LoggedInInfo loggedInInfo, Measurement measurement);

    public List<Measurement> getMeasurementsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo,
                                                                             Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive,
                                                                             int itemsToReturn);

    /**
     * Retrieves HTML template content for measurement flowsheet display groups.
     * Used for dynamic flowsheet rendering based on measurement group configurations.
     * 
     * @param groupName String the measurement group name to get HTML template for
     * @return String HTML template content, or null if group not found
     */
    public String getDShtml(String groupName);

    /**
     * Checks if a measurement-related property exists in the system configuration.
     * 
     * @param prop String the property name to check
     * @return boolean true if the property exists, false otherwise
     */
    public boolean isProperty(String prop);

    /**
     * Finds the internal ID for a measurement group by name.
     * Used for measurement group management and flowsheet organization.
     * 
     * @param groupName String the measurement group name
     * @return String the group ID, or null if not found
     */
    public String findGroupId(String groupName);

    /**
     * Adds or updates HTML template for a measurement group.
     * Used for customizing flowsheet displays and measurement presentations.
     * 
     * @param groupName String the measurement group name
     * @param dsHTML String the HTML template content for the group
     */
    public void addMeasurementGroupDS(String groupName, String dsHTML);

    /**
     * Removes HTML template for a measurement group.
     * Cleans up custom flowsheet configurations.
     * 
     * @param propKey String the property key identifying the group template to remove
     */
    public void removeMeasurementGroupDS(String propKey);

    /**
     * Retrieves the latest measurements for a patient observed after a specific date.
     * Returns the most recent measurement for each type, filtered by observation date.
     * 
     * @param loggedInInfo LoggedInInfo security context for authorization and audit
     * @param demographicId Integer the patient's demographic ID
     * @param observedDate Date cutoff date for clinical observation (not system entry)
     * @return List<Measurement> latest measurements observed after the specified date
     */
    public List<Measurement> getLatestMeasurementsByDemographicIdObservedAfter(LoggedInInfo loggedInInfo, Integer demographicId, Date observedDate);

    /**
     * Retrieves all available flowsheet HTML template files from the system.
     * Scans both configured directories and default resource locations for templates.
     * 
     * @return List<String> list of available HTML template filenames
     */
    public static List<String> getFlowsheetDsHTML() {
        List<String> dsHtml = new ArrayList<String>();
        String path_set_by_property = CarlosProperties.getInstance().getProperty("MEASUREMENT_DS_HTML_DIRECTORY");

        if (path_set_by_property != null) {
            File[] files1 = new File(path_set_by_property).listFiles();

            for (File file1 : files1) {
                if (file1.isFile()) {
                    dsHtml.add(file1.getName());
                }
            }
        }

        URL path_of_resource = MeasurementFlowSheet.class.getClassLoader()
                .getResource("/oscar/oscarEncounter/oscarMeasurements/flowsheets/html/");
        File[] files2 = new File(path_of_resource.getPath()).listFiles();

        for (File file2 : files2) {
            if (file2.isFile()) {
                dsHtml.add(file2.getName());
            }
        }

        return dsHtml;
    }

    /**
     * Retrieves the value of a measurement-related system property.
     * Provides access to configuration values stored in the properties system.
     * 
     * @param prop String the property name to retrieve
     * @return String the property value, or null if property not found
     */
    public static String getPropertyValue(String prop) {
        PropertyDao propertyDao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
        Property p = propertyDao.checkByName(prop);
        String value = p.getValue();

        return value;
    }
}
