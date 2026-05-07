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

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.prescript.dto.DrugListItemDTO;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.util.Date;
import java.util.List;
import java.util.Calendar;

/**
 * Service interface for managing Prescriptions and Drugs in the EMR.
 * Handles operations related to creating prescriptions, retrieving active/long-term medications,
 * and linking medications to patient demographics while enforcing access controls via LoggedInInfo.
 */
public interface PrescriptionManager {

    /**
     * Retrieves a specific prescription by its ID.
     * @param loggedInInfo Security context of the logged-in user.
     * @param prescriptionId The unique identifier of the prescription.
     * @return The requested Prescription entity.
     */
    public Prescription getPrescription(LoggedInInfo loggedInInfo, Integer prescriptionId);

    /**
     * Retrieves all prescriptions that were updated after a specific date.
     * Useful for synchronization and auditing purposes.
     * @param loggedInInfo Security context.
     * @param updatedAfterThisDateExclusive The threshold date.
     * @param itemsToReturn Maximum number of items to return.
     * @return List of updated prescriptions.
     */
    public List<Prescription> getPrescriptionUpdatedAfterDate(LoggedInInfo loggedInInfo,
                                                              Date updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Retrieves prescriptions for a specific patient demographic that were updated after a specific date.
     * @param loggedInInfo Security context.
     * @param demographicId The patient's demographic ID.
     * @param updatedAfterThisDateExclusive The threshold date.
     * @return List of updated prescriptions for the demographic.
     */
    public List<Prescription> getPrescriptionByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo,
                                                                             Integer demographicId, Date updatedAfterThisDateExclusive);

    /**
     * Retrieves the individual drug line items associated with a specific prescription number.
     * @param loggedInInfo Security context.
     * @param scriptNo The prescription number.
     * @param archived Flag indicating whether to include archived/historical drugs.
     * @return List of Drug entities.
     */
    public List<Drug> getDrugsByScriptNo(LoggedInInfo loggedInInfo, Integer scriptNo, Boolean archived);

    /**
     * Retrieves a unique list of drugs prescribed to a patient.
     * @param loggedInInfo Security context.
     * @param demographicNo The patient's demographic ID.
     * @return List of unique Drug entities.
     */
    public List<Drug> getUniqueDrugsByPatient(LoggedInInfo loggedInInfo, Integer demographicNo);

    /**
     * Retrieves prescriptions matching a combination of program, provider, demographic, and date criteria.
     * Used in complex reporting and program-specific workflows.
     * @param loggedInInfo Security context.
     * @param programId Optional program filter.
     * @param providerNo Optional provider filter.
     * @param demographicId Optional patient filter.
     * @param updatedAfterThisDateExclusive The threshold date.
     * @param itemsToReturn Maximum number of items to return.
     * @return List of matching Prescriptions.
     */
    public List<Prescription> getPrescriptionsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo,
                                                                               Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive,
                                                                               int itemsToReturn);

    /**
     * Creates a new prescription record containing one or more drugs for a patient.
     * @param info Security context.
     * @param drugs The list of drugs to include in the prescription.
     * @param demographicNo The patient's demographic ID.
     * @return The newly created Prescription entity.
     */
    public Prescription createNewPrescription(LoggedInInfo info, List<Drug> drugs, Integer demographicNo);

    /**
     * Retrieves all medications (active and optionally archived) for a specific patient.
     * @param loggedInInfo Security context.
     * @param demographicNo The patient's demographic ID.
     * @param archived True to include past/archived medications.
     * @return List of Drug entities.
     */
    public List<Drug> getMedicationsByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo, Boolean archived);

    /**
     * Retrieves only the active medications for a patient (String demographic ID version).
     * @param loggedInInfo Security context.
     * @param demographicNo The patient's demographic ID as a String.
     * @return List of active Drug entities.
     */
    public List<Drug> getActiveMedications(LoggedInInfo loggedInInfo, String demographicNo);

    /**
     * Retrieves only the active medications for a patient (Integer demographic ID version).
     * @param loggedInInfo Security context.
     * @param demographicNo The patient's demographic ID.
     * @return List of active Drug entities.
     */
    public List<Drug> getActiveMedications(LoggedInInfo loggedInInfo, Integer demographicNo);

    /**
     * Finds a specific drug record by its ID.
     * @param loggedInInfo Security context.
     * @param drugId The unique identifier of the drug.
     * @return The requested Drug entity.
     */
    public Drug findDrugById(LoggedInInfo loggedInInfo, Integer drugId);

    /**
     * Retrieves medications marked as long-term or chronic for a specific patient.
     * @param loggedInInfo Security context.
     * @param demographicId The patient's demographic ID.
     * @return List of long-term Drug entities.
     */
    public List<Drug> getLongTermDrugs(LoggedInInfo loggedInInfo, Integer demographicId);

    /**
     * Retrieves all prescriptions for a specific patient demographic.
     * @param loggedInInfo Security context.
     * @param demographicId The patient's demographic ID.
     * @return List of Prescription entities.
     */
    public List<Prescription> getPrescriptions(LoggedInInfo loggedInInfo, Integer demographicId);

    /**
     * Initiates the printing workflow for a specific prescription.
     * @param loggedInInfo Security context.
     * @param scriptNo The prescription number.
     * @return True if the print job was successfully queued.
     */
    public boolean print(LoggedInInfo loggedInInfo, int scriptNo);

    /**
     * Attaches a digital signature to a specific prescription, finalizing it.
     * @param loggedInInfo Security context.
     * @param scriptNo The prescription number.
     * @param digitalSignatureId The ID of the applied digital signature.
     * @return True if the signature was successfully applied.
     */
    boolean setPrescriptionSignature(LoggedInInfo loggedInInfo, int scriptNo, Integer digitalSignatureId);

    /**
     * Returns lightweight drug DTOs for a demographic. Enforces read privilege check.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context
     * @param demographicNo Integer the patient demographic number
     * @return List of DrugListItemDTO for the patient's prescriptions
     * @throws AccessDeniedException if the caller lacks {@code _demographic} read privilege for this patient
     * @since 2026-04-11
     */
    List<DrugListItemDTO> getDrugDTOs(LoggedInInfo loggedInInfo, Integer demographicNo);
}
