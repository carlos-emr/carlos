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
package io.github.carlos_emr.carlos.PMmodule.service;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.exception.AdmissionException;
import io.github.carlos_emr.carlos.PMmodule.exception.AlreadyAdmittedException;
import io.github.carlos_emr.carlos.PMmodule.exception.ProgramFullException;
import io.github.carlos_emr.carlos.PMmodule.exception.ServiceRestrictionException;
import io.github.carlos_emr.carlos.PMmodule.model.AdmissionSearchBean;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.JointAdmission;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public interface AdmissionManager {

    List<Admission> getAdmissions_archiveView(String programId, Integer demographicNo);

    Admission getAdmission(String programId, Integer demographicNo);

    Admission getCurrentAdmission(String programId, Integer demographicNo);

    List<Admission> getAdmissionsByFacility(Integer demographicNo, Integer facilityId);

    List<Admission> getCurrentAdmissionsByFacility(Integer demographicNo, Integer facilityId);

    List<Admission> getAdmissions();

    List<Admission> getAdmissions(Integer demographicNo);

    List<Admission> getCurrentAdmissions(Integer demographicNo);


    List<Admission> getCurrentServiceProgramAdmission(Integer demographicNo);

    Admission getCurrentExternalProgramAdmission(Integer demographicNo);

    Admission getCurrentCommunityProgramAdmission(Integer demographicNo);

    List<Admission> getCurrentAdmissionsByProgramId(String programId);

    Admission getAdmission(Long id);

    Admission getAdmission(Integer id);

    void saveAdmission(Admission admission);

    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, List<Integer> dependents) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, boolean overrideRestriction) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, Date admissionDate) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, List<Integer> dependents, Date admissionDate) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, Date admissionDate, boolean overrideRestriction, List<Integer> dependents) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    void processInitialAdmission(Integer demographicNo, String providerNo, Program program, String admissionNotes, Date admissionDate) throws ProgramFullException, AlreadyAdmittedException, ServiceRestrictionException;

    Admission getTemporaryAdmission(Integer demographicNo);

    List<Admission> getCurrentTemporaryProgramAdmission(Integer demographicNo);

    boolean isDependentInDifferentProgramFromHead(Integer demographicNo, List<JointAdmission> dependentList);

    List search(AdmissionSearchBean searchBean);

    void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason) throws AdmissionException;

    void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason, Date dischargeDate) throws AdmissionException;

    void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason, Date dischargeDate, List<Integer> dependents, boolean fromTransfer, boolean automaticDischarge) throws AdmissionException;

    void processDischargeToCommunity(Integer communityProgramId, Integer demographicNo, String providerNo, String notes, String radioDischargeReason, Date dischargeDate) throws AdmissionException;

    void processDischargeToCommunity(Integer communityProgramId, Integer demographicNo, String providerNo, String notes, String radioDischargeReason, List<Integer> dependents, Date dischargeDate) throws AdmissionException;

    boolean isActiveInCurrentFacility(LoggedInInfo loggedInInfo, int demographicId);

    List getActiveAnonymousAdmissions();

    boolean wasInProgram(Integer programId, Integer clientId);

    List<Admission> findAdmissionsByProgramAndDate(LoggedInInfo loggedInInfo, Integer programNo, Date day, int startIndex, int numToReturn);

    Integer findAdmissionsByProgramAndDateAsCount(LoggedInInfo loggedInInfo, Integer programNo, Date day);
}
