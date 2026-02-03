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

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.util.Date;
import java.util.List;
import java.util.Calendar;

public interface PrescriptionManager {

    public Prescription getPrescription(LoggedInInfo loggedInInfo, Integer prescriptionId);

    public List<Prescription> getPrescriptionUpdatedAfterDate(LoggedInInfo loggedInInfo,
                                                              Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Prescription> getPrescriptionByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo,
                                                                             Integer demographicId, Date updatedAfterThisDateExclusive);

    public List<Drug> getDrugsByScriptNo(LoggedInInfo loggedInInfo, Integer scriptNo, Boolean archived);

    public List<Drug> getUniqueDrugsByPatient(LoggedInInfo loggedInInfo, Integer demographicNo);

    public List<Prescription> getPrescriptionsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo,
                                                                               Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive,
                                                                               int itemsToReturn);

    public Prescription createNewPrescription(LoggedInInfo info, List<Drug> drugs, Integer demographicNo);

    public List<Drug> getMedicationsByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo, Boolean archived);

    public List<Drug> getActiveMedications(LoggedInInfo loggedInInfo, String demographicNo);

    public List<Drug> getActiveMedications(LoggedInInfo loggedInInfo, Integer demographicNo);

    public Drug findDrugById(LoggedInInfo loggedInInfo, Integer drugId);

    public List<Drug> getLongTermDrugs(LoggedInInfo loggedInInfo, Integer demographicId);

    public List<Prescription> getPrescriptions(LoggedInInfo loggedInInfo, Integer demographicId);

    public boolean print(LoggedInInfo loggedInInfo, int scriptNo);

    boolean setPrescriptionSignature(LoggedInInfo loggedInInfo, int scriptNo, Integer digitalSignatureId);
}
