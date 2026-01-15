//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.PMmodule.caisi_integrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ca.openosp.openo.caisi_integrator.ws.CachedAdmission;
import ca.openosp.openo.caisi_integrator.ws.CachedAppointment;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicAllergy;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicDocument;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicDocumentContents;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicDrug;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicForm;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicIssue;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicLabResult;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicNote;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicPrevention;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdIntegerCompositePk;
import ca.openosp.openo.commn.dao.DemographicExtDao;
import ca.openosp.openo.commn.dao.RemoteIntegratedDataCopyDao;
import ca.openosp.openo.commn.model.RemoteIntegratedDataCopy;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;

/**
 * Local caching and fallback manager for integrator data access.
 * <p>
 * This manager provides local storage and retrieval of data retrieved from remote facilities
 * through the integrator system. It implements a caching layer that:
 * <ul>
 *   <li>Stores copies of remote demographic data locally for improved performance</li>
 *   <li>Enables access to integrated data even when the integrator service is unavailable</li>
 *   <li>Reduces network traffic by caching frequently accessed remote data</li>
 *   <li>Supports various data types: notes, issues, preventions, drugs, admissions, etc.</li>
 * </ul>
 * </p>
 * <p>
 * The manager operates in two modes:
 * <ol>
 *   <li><strong>Save mode:</strong> Retrieves data from integrator web services and stores
 *       serialized copies in the local database</li>
 *   <li><strong>Retrieve mode:</strong> Loads previously cached data from local storage
 *       without requiring integrator connectivity</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Configuration:</strong> Local caching is controlled by the
 * {@code INTEGRATOR_LOCAL_STORE} property (default: "yes"). When disabled, get methods
 * return null, forcing direct integrator access.
 * </p>
 * <p>
 * <strong>Data Types Supported:</strong>
 * <ul>
 *   <li>Demographic notes and group notes</li>
 *   <li>Clinical issues</li>
 *   <li>Preventions (immunizations, screenings)</li>
 *   <li>Medications (drugs)</li>
 *   <li>Admissions to programs</li>
 *   <li>Appointments</li>
 *   <li>Allergies</li>
 *   <li>Documents and document contents</li>
 *   <li>Lab results</li>
 *   <li>Forms (e.g., formLabReq07)</li>
 * </ul>
 * </p>
 * 
 * @see CaisiIntegratorManager
 * @see RemoteIntegratedDataCopyDao
 */
public class IntegratorFallBackManager {
    /** DAO for storing and retrieving serialized remote data copies */
    static RemoteIntegratedDataCopyDao remoteIntegratedDataCopyDao = SpringUtils.getBean(RemoteIntegratedDataCopyDao.class);
    
    /** DAO for accessing demographic extended attributes */
    static DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);

    /**
     * Retrieves linked notes for a demographic from local cache.
     * <p>
     * This method checks the local cache for previously stored notes from linked remote
     * demographics. It does NOT contact the integrator service, making it suitable for
     * fallback scenarios when the integrator is unavailable.
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param demographicNo the demographic number to retrieve notes for
     * @return list of cached demographic notes, or null if local store is disabled or no data found
     */
    public static List<CachedDemographicNote> getLinkedNotes(LoggedInInfo loggedInInfo, Integer demographicNo) {
        // Check if local caching is enabled
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedDemographicNote> linkedNotes = null;
        // Look up the cached data by demographic and type
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicNote[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            return linkedNotes;
        }

        try {
            // Deserialize the cached array of notes
            CachedDemographicNote[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicNote[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedDemographicNote>();
            // Convert array to list
            for (CachedDemographicNote cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }

    /**
     * Saves linked notes for a demographic to local cache.
     * <p>
     * This method retrieves notes from the integrator web service and stores a serialized
     * copy in the local database for future fallback access. This is typically called during
     * periodic synchronization or when viewing integrated data.
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param demographicNo the demographic number to save notes for
     */
    public static void saveLinkNotes(LoggedInInfo loggedInInfo, Integer demographicNo) {
        try {
            try {
                // Extract the provider number for audit purposes
                String providerNo = null;
                if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                    providerNo = loggedInInfo.getLoggedInProviderNo();
                }
                // Retrieve notes from the integrator service
                DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
                List<CachedDemographicNote> linkedNotes = Collections.unmodifiableList(demographicWs.getLinkedCachedDemographicNotes(demographicNo));
                MiscUtils.getLogger().info("Saving remote copy for " + demographicNo + "  linkedNotes : " + linkedNotes.size());

                if (linkedNotes.size() == 0) {
                    return;
                }
                // Convert to array for serialization
                CachedDemographicNote[] array = linkedNotes.toArray(new CachedDemographicNote[linkedNotes.size()]);

                // Store the serialized array in the local database
                remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error saving remote notes for " + demographicNo, e);
            }
            //	}
        } catch (Exception ee) {
            MiscUtils.getLogger().error("Error getting remote notes for " + demographicNo, ee);
        }
    }

    /**
     * Saves remote forms for a demographic to local cache.
     * <p>
     * Currently supports form types defined in the tables array. Each form type is
     * retrieved from the integrator and cached separately with a type-specific key.
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param demographicNo the demographic number to save forms for
     */
    public static void saveRemoteForms(LoggedInInfo loggedInInfo, Integer demographicNo) {
        // Define which form types to cache - this list should be expanded as needed
        String[] tables = {"formLabReq07"};  //Need better way to do this
        List<CachedDemographicForm> remoteForms = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            // Process each form type separately
            for (String table : tables) {
                DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
                remoteForms = demographicWs.getLinkedCachedDemographicForms(demographicNo, table);
                MiscUtils.getLogger().debug("Saving remote forms for " + demographicNo + "  forms : " + remoteForms.size() + " table " + table);
                if (remoteForms.size() == 0) {
                    continue;
                }

                CachedDemographicForm[] array = remoteForms.toArray(new CachedDemographicForm[remoteForms.size()]);
                MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + table + " " + demographicNo);

                // Store with table name appended to type for separate caching per form type
                remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId(), table);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote forms for " + demographicNo, e);
        }
    }

    /**
     * Retrieves remote forms for a specific form type from local cache.
     * 
     * @param loggedInInfo the current user's session information
     * @param demographicNo the demographic number to retrieve forms for
     * @param table the form type identifier (e.g., "formLabReq07")
     * @return list of cached forms, or null if local store disabled or no data found
     */
    public static List<CachedDemographicForm> getRemoteForms(LoggedInInfo loggedInInfo, Integer demographicNo, String table) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedDemographicForm> linkedForms = null;
        // Lookup includes table name as part of the type key
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicForm[].class.getName() + "+" + table);

        if (remoteIntegratedDataCopy == null) {
            return linkedForms;
        }

        try {
            CachedDemographicForm[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicForm[].class, remoteIntegratedDataCopy);
            linkedForms = new ArrayList<CachedDemographicForm>();
            for (CachedDemographicForm cdn : array) {
                linkedForms.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedForms;
    }


    ////TESTED ^^^^/////
    
    /*
     * The following methods follow a consistent pattern for caching different data types:
     * 
     * For each data type (Issues, Preventions, Drugs, Admissions, Appointments, Allergies, Documents, LabResults):
     * 
     * 1. save<DataType>(loggedInInfo, demographicNo):
     *    - Retrieves data from integrator web service
     *    - Serializes and stores in local database via remoteIntegratedDataCopyDao
     *    - Used during synchronization to cache remote data locally
     * 
     * 2. get<DataType>(loggedInInfo, demographicNo):
     *    - Checks if local caching is enabled (INTEGRATOR_LOCAL_STORE property)
     *    - Retrieves cached data from local database
     *    - Deserializes and returns as list
     *    - Returns null if caching disabled or no data found
     * 
     * This pattern enables fallback access to integrator data when the service is unavailable.
     */
    
    /**
     * Saves demographic issues from integrator to local cache.
     * Follows standard save pattern: retrieve from web service, serialize, store locally.
     * 
     * @param loggedInInfo the current user's session information
     * @param demographicNo the demographic number to save issues for
     */
    public static void saveDemographicIssues(LoggedInInfo loggedInInfo, int demographicNo) {
        List<CachedDemographicIssue> remoteIssues = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteIssues = demographicWs.getLinkedCachedDemographicIssuesByDemographicId(demographicNo);
            MiscUtils.getLogger().debug("Saving remoteIssues for " + demographicNo + "  issues : " + remoteIssues.size());
            if (remoteIssues.size() == 0) {
                return;
            }

            CachedDemographicIssue[] array = remoteIssues.toArray(new CachedDemographicIssue[remoteIssues.size()]);
            MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + demographicNo);

            remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote forms for " + demographicNo, e);
        }
    }


    public static List<CachedDemographicIssue> getRemoteDemographicIssues(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedDemographicIssue> linkedNotes = null;
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicIssue[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            return linkedNotes;
        }

        try {
            CachedDemographicIssue[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicIssue[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedDemographicIssue>();
            for (CachedDemographicIssue cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }


    public static void saveDemographicPreventions(LoggedInInfo loggedInInfo, int demographicNo) {
        List<CachedDemographicPrevention> remoteItems = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteItems = demographicWs.getLinkedCachedDemographicPreventionsByDemographicId(demographicNo);
            MiscUtils.getLogger().debug("Saving remote Preventions for " + demographicNo + "  issues : " + remoteItems.size());
            if (remoteItems.size() == 0) {
                return;
            }

            CachedDemographicPrevention[] array = remoteItems.toArray(new CachedDemographicPrevention[remoteItems.size()]);
            MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + demographicNo);
            remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote forms for " + demographicNo, e);
        }
    }


    public static List<CachedDemographicPrevention> getRemotePreventions(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedDemographicPrevention> linkedNotes = null;
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicPrevention[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            return linkedNotes;
        }

        try {
            CachedDemographicPrevention[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicPrevention[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedDemographicPrevention>();
            for (CachedDemographicPrevention cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }


    public static void saveDemographicDrugs(LoggedInInfo loggedInInfo, int demographicNo) {
        List<CachedDemographicDrug> remoteItems = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteItems = demographicWs.getLinkedCachedDemographicDrugsByDemographicId(demographicNo);
            MiscUtils.getLogger().debug("Saving remote Drugs for " + demographicNo + "  issues : " + remoteItems.size());
            if (remoteItems.size() == 0) {
                return;
            }

            CachedDemographicDrug[] array = remoteItems.toArray(new CachedDemographicDrug[remoteItems.size()]);
            MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + demographicNo);
            remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote forms for " + demographicNo, e);
        }
    }

    public static List<CachedDemographicDrug> getRemoteDrugs(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedDemographicDrug> linkedNotes = null;
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicDrug[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            return linkedNotes;
        }

        try {
            CachedDemographicDrug[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicDrug[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedDemographicDrug>();
            for (CachedDemographicDrug cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }

    public static void saveAdmissions(LoggedInInfo loggedInInfo, int demographicNo) {
        List<CachedAdmission> remoteItems = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteItems = demographicWs.getLinkedCachedAdmissionsByDemographicId(demographicNo);
            MiscUtils.getLogger().debug("Saving remote Admissions for " + demographicNo + "  issues : " + remoteItems.size());
            if (remoteItems.size() == 0) {
                return;
            }

            CachedAdmission[] array = remoteItems.toArray(new CachedAdmission[remoteItems.size()]);
            MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + demographicNo);
            remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote forms for " + demographicNo, e);
        }
    }

    public static List<CachedAdmission> getRemoteAdmissions(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedAdmission> linkedNotes = null;
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedAdmission[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            return linkedNotes;
        }

        try {
            CachedAdmission[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedAdmission[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedAdmission>();
            for (CachedAdmission cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }


    public static void saveAppointments(LoggedInInfo loggedInInfo, int demographicNo) {
        List<CachedAppointment> remoteItems = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteItems = demographicWs.getLinkedCachedAppointments(demographicNo);
            MiscUtils.getLogger().debug("Saving remote CachedAppointment for " + demographicNo + "  issues : " + remoteItems.size());
            if (remoteItems.size() == 0) {
                return;
            }

            CachedAppointment[] array = remoteItems.toArray(new CachedAppointment[remoteItems.size()]);
            MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + demographicNo);
            remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote CachedAppointment for " + demographicNo, e);
        }
    }


    public static List<CachedAppointment> getRemoteAppointments(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedAppointment> linkedNotes = null;
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedAppointment[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            return linkedNotes;
        }

        try {
            CachedAppointment[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedAppointment[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedAppointment>();
            for (CachedAppointment cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }


    public static void saveAllergies(LoggedInInfo loggedInInfo, int demographicNo) {
        List<CachedDemographicAllergy> remoteItems = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteItems = demographicWs.getLinkedCachedDemographicAllergies(demographicNo);
            MiscUtils.getLogger().debug("Saving remote CachedDemographicAllergy for " + demographicNo + "  issues : " + remoteItems.size());
            if (remoteItems.size() == 0) {
                return;
            }

            CachedDemographicAllergy[] array = remoteItems.toArray(new CachedDemographicAllergy[remoteItems.size()]);
            MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + demographicNo);
            remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote CachedDemographicAllergy for " + demographicNo, e);
        }
    }


    public static List<CachedDemographicAllergy> getRemoteAllergies(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedDemographicAllergy> linkedNotes = null;
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicAllergy[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            return linkedNotes;
        }

        try {
            CachedDemographicAllergy[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicAllergy[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedDemographicAllergy>();
            for (CachedDemographicAllergy cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }

    /**
     * Saves demographic documents and their contents to local cache.
     * <p>
     * This method is more complex than other save methods because it stores both:
     * <ol>
     *   <li>Document metadata (headers) as an array</li>
     *   <li>Individual document contents, each keyed by facility and document ID</li>
     * </ol>
     * This two-level caching allows efficient listing of documents without loading
     * all content, while still providing fallback access to full document data.
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param demographicNo the demographic number to save documents for
     */
    public static void saveDocuments(LoggedInInfo loggedInInfo, int demographicNo) {
        List<CachedDemographicDocument> remoteItems = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteItems = demographicWs.getLinkedCachedDemographicDocuments(demographicNo);
            MiscUtils.getLogger().debug("Saving remote CachedDemographicAllergy for " + demographicNo + "  issues : " + remoteItems.size());
            if (remoteItems.size() == 0) {
                return;
            }

            CachedDemographicDocument[] array = remoteItems.toArray(new CachedDemographicDocument[remoteItems.size()]);
            MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + demographicNo);
            // Store document metadata (headers)
            remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());

            // For each document, also cache the full content separately
            for (CachedDemographicDocument document : array) {
                FacilityIdIntegerCompositePk remotePk = document.getFacilityIntegerPk();
                // Retrieve the actual document content from integrator
                CachedDemographicDocumentContents remoteDocumentContents = demographicWs.getCachedDemographicDocumentContents(remotePk);
                MiscUtils.getLogger().debug("what is remotePK " + getPK(remotePk));
                // Store content separately, keyed by the composite primary key
                remoteIntegratedDataCopyDao.save(demographicNo, remoteDocumentContents, providerNo, loggedInInfo.getCurrentFacility().getId(), getPK(remotePk));

            }


        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote CachedDemographicDocument for " + demographicNo, e);
        }
    }

    /**
     * Generates a string key from a facility/document composite primary key.
     * <p>
     * Format: "facilityId:documentId"
     * Used to uniquely identify document contents in the cache.
     * </p>
     * 
     * @param remotePk the composite primary key
     * @return formatted string key
     */
    private static String getPK(FacilityIdIntegerCompositePk remotePk) {
        return (String.valueOf(remotePk.getIntegratorFacilityId()) + ":" + remotePk.getCaisiItemId());
    }

    /**
     * Retrieves the demographic number associated with a cached document.
     * <p>
     * Useful for reverse lookups when you have a document key but need to know
     * which demographic it belongs to.
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param remotePk the document's composite primary key
     * @return the demographic number, or null if not found
     */
    public static Integer getDemographicNoFromRemoteDocument(LoggedInInfo loggedInInfo, FacilityIdIntegerCompositePk remotePk) {
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByType(loggedInInfo.getCurrentFacility().getId(), CachedDemographicDocumentContents.class.getName() + "+" + getPK(remotePk));
        return remoteIntegratedDataCopy.getDemographicNo();
    }


    /**
     * Retrieves document contents from local cache by composite key only.
     * <p>
     * This version looks up the document without requiring the demographic number,
     * making it useful when you only have the document identifier.
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param remotePk the document's composite primary key
     * @return the document contents, or null if not found
     */
    public static CachedDemographicDocumentContents getRemoteDocument(LoggedInInfo loggedInInfo, FacilityIdIntegerCompositePk remotePk) {
        CachedDemographicDocumentContents documentContents = null;

        // Lookup without demographic number - searches across all demographics
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByType(loggedInInfo.getCurrentFacility().getId(), CachedDemographicDocumentContents.class.getName() + "+" + getPK(remotePk));

        try {
            documentContents = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicDocumentContents.class, remoteIntegratedDataCopy);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for remotePK : " + remotePk + " from local store ", e);
        }

        return documentContents;
    }

    /**
     * Retrieves document contents from local cache by demographic and composite key.
     * <p>
     * This version is more efficient when you already know the demographic number,
     * as it narrows the search space.
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param demographicNo the demographic number the document belongs to
     * @param remotePk the document's composite primary key
     * @return the document contents, or null if not found
     */
    public static CachedDemographicDocumentContents getRemoteDocument(LoggedInInfo loggedInInfo, Integer demographicNo, FacilityIdIntegerCompositePk remotePk) {
        CachedDemographicDocumentContents documentContents = null;

        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicDocumentContents.class.getName() + "+" + getPK(remotePk));

        try {
            documentContents = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicDocumentContents.class, remoteIntegratedDataCopy);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }

        return documentContents;
    }


    public static List<CachedDemographicDocument> getRemoteDocuments(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedDemographicDocument> linkedNotes = null;
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicDocument[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            MiscUtils.getLogger().debug("remoteIntegratedDataCopy was null");
            return linkedNotes;
        }

        try {
            CachedDemographicDocument[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicDocument[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedDemographicDocument>();
            MiscUtils.getLogger().debug("linked Doc header size " + linkedNotes);
            for (CachedDemographicDocument cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Document headers for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }


    public static void saveLabResults(LoggedInInfo loggedInInfo, int demographicNo) {
        List<CachedDemographicLabResult> remoteItems = null;

        try {
            String providerNo = null;

            if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteItems = demographicWs.getLinkedCachedDemographicLabResults(demographicNo);
            MiscUtils.getLogger().debug("Saving remote CachedDemographicLabResult for " + demographicNo + "  issues : " + remoteItems.size());
            if (remoteItems.size() == 0) {
                return;
            }

            CachedDemographicLabResult[] array = remoteItems.toArray(new CachedDemographicLabResult[remoteItems.size()]);
            MiscUtils.getLogger().info("logged in " + loggedInInfo + " " + demographicNo);
            remoteIntegratedDataCopyDao.save(demographicNo, array, providerNo, loggedInInfo.getCurrentFacility().getId());

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving remote CachedDemographicLabResult for " + demographicNo, e);
        }
    }

    public static List<CachedDemographicLabResult> getLabResults(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!OscarProperties.getInstance().getBooleanProperty("INTEGRATOR_LOCAL_STORE", "yes")) return null;

        List<CachedDemographicLabResult> linkedNotes = null;
        RemoteIntegratedDataCopy remoteIntegratedDataCopy = remoteIntegratedDataCopyDao.findByDemoType(loggedInInfo.getCurrentFacility().getId(), demographicNo, CachedDemographicLabResult[].class.getName());

        if (remoteIntegratedDataCopy == null) {
            return linkedNotes;
        }

        try {
            CachedDemographicLabResult[] array = remoteIntegratedDataCopyDao.getObjectFrom(CachedDemographicLabResult[].class, remoteIntegratedDataCopy);
            linkedNotes = new ArrayList<CachedDemographicLabResult>();
            for (CachedDemographicLabResult cdn : array) {
                linkedNotes.add(cdn);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error Loading Notes for : " + demographicNo + " from local store ", e);
        }
        return linkedNotes;
    }

}
