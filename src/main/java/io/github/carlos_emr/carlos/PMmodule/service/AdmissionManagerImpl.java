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

package io.github.carlos_emr.carlos.PMmodule.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ClientReferralDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramClientStatusDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramQueueDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.exception.AdmissionException;
import io.github.carlos_emr.carlos.PMmodule.exception.AlreadyAdmittedException;
import io.github.carlos_emr.carlos.PMmodule.exception.ProgramFullException;
import io.github.carlos_emr.carlos.PMmodule.exception.ServiceRestrictionException;
import io.github.carlos_emr.carlos.PMmodule.model.AdmissionSearchBean;
import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;
import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.JointAdmission;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.log.LogAction;

/**
 * Transactional implementation of {@link AdmissionManager} for managing client admissions
 * within the CARLOS EMR Program Management module.
 *
 * <p>Handles the complete admission lifecycle including program admission, discharge,
 * community program transfers, and joint admissions for family dependents. Enforces
 * program capacity limits, service restrictions, and temporary bed program constraints.</p>
 *
 * <p>This manager coordinates with multiple DAOs and services including
 * {@link ProgramQueueDao} for queue management, {@link ClientReferralDAO} for referral tracking,
 * and {@link ClientRestrictionManager} for service restriction enforcement.</p>
 *
 * @see AdmissionManager
 * @see Admission
 * @since 2005
 */
@Transactional
public class AdmissionManagerImpl implements AdmissionManager {

    private AdmissionDao dao;
    private ProgramDao programDao;
    private ProgramQueueDao programQueueDao;
    private ClientReferralDAO clientReferralDAO;
    private ProgramClientStatusDAO programClientStatusDAO;
    private ClientRestrictionManager clientRestrictionManager;

    /** {@inheritDoc} */
    public List<Admission> getAdmissions_archiveView(String programId, Integer demographicNo) {
        return dao.getAdmissions_archiveView(Integer.valueOf(programId), demographicNo);
    }

    /** {@inheritDoc} */
    public Admission getAdmission(String programId, Integer demographicNo) {
        return dao.getAdmission(Integer.valueOf(programId), demographicNo);
    }

    /** {@inheritDoc} */
    public Admission getCurrentAdmission(String programId, Integer demographicNo) {
        return dao.getCurrentAdmission(Integer.valueOf(programId), demographicNo);
    }

    /** {@inheritDoc} */
    public List<Admission> getAdmissionsByFacility(Integer demographicNo, Integer facilityId) {
        return dao.getAdmissionsByFacility(demographicNo, facilityId);
    }

    /** {@inheritDoc} */
    public List<Admission> getCurrentAdmissionsByFacility(Integer demographicNo, Integer facilityId) {
        return dao.getCurrentAdmissionsByFacility(demographicNo, facilityId);
    }

    /** {@inheritDoc} */
    public List<Admission> getAdmissions() {
        return dao.getAdmissions();
    }

    /** {@inheritDoc} */
    public List<Admission> getAdmissions(Integer demographicNo) {
        return dao.getAdmissions(demographicNo);
    }

    /** {@inheritDoc} */
    public List<Admission> getCurrentAdmissions(Integer demographicNo) {
        return dao.getCurrentAdmissions(demographicNo);
    }


    /** {@inheritDoc} */
    public List<Admission> getCurrentServiceProgramAdmission(Integer demographicNo) {
        return dao.getCurrentServiceProgramAdmission(programDao, demographicNo);
    }

    /** {@inheritDoc} */
    public Admission getCurrentExternalProgramAdmission(Integer demographicNo) {
        return dao.getCurrentExternalProgramAdmission(programDao, demographicNo);
    }

    /** {@inheritDoc} */
    public Admission getCurrentCommunityProgramAdmission(Integer demographicNo) {
        return dao.getCurrentCommunityProgramAdmission(programDao, demographicNo);
    }

    /** {@inheritDoc} */
    public List<Admission> getCurrentAdmissionsByProgramId(String programId) {
        return dao.getCurrentAdmissionsByProgramId(Integer.valueOf(programId));
    }

    /** {@inheritDoc} */
    public Admission getAdmission(Long id) {
        return dao.getAdmission(id.intValue());
    }

    /** {@inheritDoc} */
    public Admission getAdmission(Integer id) {
        return dao.getAdmission(id);
    }

    /** {@inheritDoc} */
    public void saveAdmission(Admission admission) {
        dao.saveAdmission(admission);
    }

    /*public void processAdmissionToExternal(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes) throws ProgramFullException, AdmissionException, ServiceRestrictionException {
        processAdmission(demographicNo, providerNo, program, dischargeNotes, admissionNotes, false, null, false);
    }
    */
    /** {@inheritDoc} */
    public void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes) throws ProgramFullException, AdmissionException, ServiceRestrictionException {
        processAdmission(demographicNo, providerNo, program, dischargeNotes, admissionNotes, false, null, false, null);
    }

    /** {@inheritDoc} */
    public void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission) throws ProgramFullException, AdmissionException, ServiceRestrictionException {
        processAdmission(demographicNo, providerNo, program, dischargeNotes, admissionNotes, tempAdmission, null, false, null);
    }

    /** {@inheritDoc} */
    public void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, List<Integer> dependents) throws ProgramFullException, AdmissionException, ServiceRestrictionException {
        processAdmission(demographicNo, providerNo, program, dischargeNotes, admissionNotes, tempAdmission, null, false, dependents);
    }

    /** {@inheritDoc} */
    public void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, boolean overrideRestriction) throws ProgramFullException, AdmissionException, ServiceRestrictionException {
        processAdmission(demographicNo, providerNo, program, dischargeNotes, admissionNotes, tempAdmission, null, overrideRestriction, null);
    }

    /** {@inheritDoc} */
    public void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, Date admissionDate) throws ProgramFullException, AdmissionException, ServiceRestrictionException {
        processAdmission(demographicNo, providerNo, program, dischargeNotes, admissionNotes, false, admissionDate, false, null);
    }

    /** {@inheritDoc} */
    public void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, List<Integer> dependents, Date admissionDate) throws ProgramFullException, AdmissionException, ServiceRestrictionException {
        processAdmission(demographicNo, providerNo, program, dischargeNotes, admissionNotes, tempAdmission, admissionDate, false, dependents);
    }


    /**
     * {@inheritDoc}
     *
     * <p>Core admission processing method that all other processAdmission overloads delegate to.
     * Validates program capacity, checks service restrictions, enforces single temporary bed
     * constraint, creates the admission record, updates the program queue and referral status,
     * and recursively admits any dependents.</p>
     */
    public void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, Date admissionDate, boolean overrideRestriction, List<Integer> dependents) throws ProgramFullException, AdmissionException, ServiceRestrictionException {
        // see if there's room first
        if (program.getNumOfMembers().intValue() >= program.getMaxAllowed().intValue()) {
            throw new ProgramFullException();
        }

        // check if there's a service restriction in place on this individual for this program
        if (!overrideRestriction) {
            ProgramClientRestriction restrInPlace = clientRestrictionManager.checkClientRestriction(program.getId(), demographicNo, new Date());
            if (restrInPlace != null) {
                throw new ServiceRestrictionException("service restriction in place", restrInPlace);
            }
        }


        // Can only be in a single temporary bed program.
        if (tempAdmission && getTemporaryAdmission(demographicNo) != null) {
            throw new AdmissionException("Already in a temporary program.");
        }

        // Create/Save admission object
        Admission newAdmission = new Admission();
        if (admissionDate != null) {
            newAdmission.setAdmissionDate(admissionDate);
        } else {
            newAdmission.setAdmissionDate(new Date());
        }

        newAdmission.setAdmissionNotes(admissionNotes);
        newAdmission.setAdmissionStatus(Admission.STATUS_CURRENT);
        newAdmission.setClientId(demographicNo);
        newAdmission.setProgramId(program.getId());
        newAdmission.setProviderNo(providerNo);
        newAdmission.setTeamId(null);
        newAdmission.setTemporaryAdmission(tempAdmission);
        newAdmission.setAdmissionFromTransfer(false);

        //keep the client status if he was in the same program with it.
        Integer clientStatusId = dao.getLastClientStatusFromAdmissionByProgramIdAndClientId(Integer.valueOf(program.getId()), demographicNo);

        //check if the client status is valid/existed in program_clientStatus
        if (programClientStatusDAO.getProgramClientStatus(clientStatusId.toString()) == null || clientStatusId == 0)
            clientStatusId = null;

        newAdmission.setClientStatusId(clientStatusId);

        saveAdmission(newAdmission);

        // Clear them from the queue, Update their referral
        ProgramQueue pq = programQueueDao.getActiveProgramQueue(program.getId().longValue(), (long) demographicNo);
        if (pq != null) {
            pq.setStatus(ProgramQueue.STATUS_ADMITTED);
            programQueueDao.saveProgramQueue(pq);

            // is there a referral
            if (pq.getReferralId() != null && pq.getReferralId().longValue() > 0) {
                ClientReferral referral = clientReferralDAO.getClientReferral(pq.getReferralId());
                referral.setStatus(ClientReferral.STATUS_CURRENT);
                referral.setCompletionDate(new Date());
                referral.setCompletionNotes(admissionNotes);
                clientReferralDAO.saveClientReferral(referral);
                if (referral.getVacancyId() != null) {
                    //change vacancy's status
                    VacancyDao vacancyDao = SpringUtils.getBean(VacancyDao.class);
                    Vacancy v = vacancyDao.find(referral.getVacancyId());
                    if (v != null) {
                        v.setStatus("filled");
                        vacancyDao.saveEntity(v);
                    }
                }
            }
        }



        //For the clients dependents
        if (dependents != null) {
            for (Integer l : dependents) {
                processAdmission(l, providerNo, program, dischargeNotes, admissionNotes, tempAdmission, newAdmission.getAdmissionDate(), true, null);
            }
        }

        //Once the patient is admitted to this program, the vacancy
    }

    /** {@inheritDoc} */
    public void processInitialAdmission(Integer demographicNo, String providerNo, Program program, String admissionNotes, Date admissionDate) throws ProgramFullException, AlreadyAdmittedException, ServiceRestrictionException {
        // see if there's room first
        if (program.getNumOfMembers().intValue() >= program.getMaxAllowed().intValue()) {
            throw new ProgramFullException();
        }

        // check if there's a service restriction in place on this individual for this program
        ProgramClientRestriction restrInPlace = clientRestrictionManager.checkClientRestriction(program.getId(), demographicNo, new Date());
        if (restrInPlace != null) {
            throw new ServiceRestrictionException("service restriction in place", restrInPlace);
        }

        Admission admission = getCurrentAdmission(String.valueOf(program.getId()), demographicNo);
        if (admission != null) {
            throw new AlreadyAdmittedException();
        }

        Admission newAdmission = new Admission();
        if (admissionDate == null) {
            newAdmission.setAdmissionDate(new Date());
        } else {
            newAdmission.setAdmissionDate(admissionDate);
        }
        newAdmission.setAdmissionNotes(admissionNotes);
        newAdmission.setAdmissionStatus(Admission.STATUS_CURRENT);
        newAdmission.setClientId(demographicNo);
        newAdmission.setProgramId(program.getId());
        newAdmission.setProviderNo(providerNo);
        newAdmission.setTeamId(null);
        saveAdmission(newAdmission);
    }

    /** {@inheritDoc} */
    public Admission getTemporaryAdmission(Integer demographicNo) {
        return dao.getTemporaryAdmission(demographicNo);
    }

    /** {@inheritDoc} */
    public List<Admission> getCurrentTemporaryProgramAdmission(Integer demographicNo) {
        Admission admission = dao.getTemporaryAdmission(demographicNo);
        if (admission != null) {
            List<Admission> results = new ArrayList<Admission>();
            results.add(admission);
            return results;
        }
        return null;
    }

    /** {@inheritDoc} */
    public boolean isDependentInDifferentProgramFromHead(Integer demographicNo, List<JointAdmission> dependentList) {
        return false;
    }

    /** {@inheritDoc} */
    public List search(AdmissionSearchBean searchBean) {
        return dao.search(searchBean);
    }

    /** {@inheritDoc} */
    public void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason) throws AdmissionException {
        processDischarge(programId, demographicNo, dischargeNotes, radioDischargeReason, null, null, false, false);
    }

    /** {@inheritDoc} */
    public void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason, Date dischargeDate) throws AdmissionException {
        processDischarge(programId, demographicNo, dischargeNotes, radioDischargeReason, dischargeDate, null, false, false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Core discharge method that updates the admission status to discharged,
     * records discharge details, and recursively discharges any dependents.</p>
     */
    public void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason, Date dischargeDate, List<Integer> dependents, boolean fromTransfer, boolean automaticDischarge) throws AdmissionException {

        Admission fullAdmission = getCurrentAdmission(String.valueOf(programId), demographicNo);

        if (fullAdmission == null) {
            throw new AdmissionException("Admission Record not found");
        }

        if (dischargeDate == null)
            fullAdmission.setDischargeDate(new Date());
        else
            fullAdmission.setDischargeDate(dischargeDate);

        fullAdmission.setDischargeNotes(dischargeNotes);
        fullAdmission.setAdmissionStatus(Admission.STATUS_DISCHARGED);
        fullAdmission.setRadioDischargeReason(radioDischargeReason);
        fullAdmission.setDischargeFromTransfer(fromTransfer);
        fullAdmission.setAutomaticDischarge(automaticDischarge);

        saveAdmission(fullAdmission);


        if (dependents != null) {
            for (Integer l : dependents) {
                processDischarge(programId, l, dischargeNotes, radioDischargeReason, dischargeDate, null, fromTransfer, automaticDischarge);
            }
        }
    }

    /** {@inheritDoc} */
    public void processDischargeToCommunity(Integer communityProgramId, Integer demographicNo, String providerNo, String notes, String radioDischargeReason, Date dischargeDate) throws AdmissionException {
        processDischargeToCommunity(communityProgramId, demographicNo, providerNo, notes, radioDischargeReason, null, dischargeDate);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Discharges the client from their current community program (if any), creates a new
     * admission in the target community program, and recursively processes dependents.</p>
     */
    public void processDischargeToCommunity(Integer communityProgramId, Integer demographicNo, String providerNo, String notes, String radioDischargeReason, List<Integer> dependents, Date dischargeDate) throws AdmissionException {
        Admission currentCommunityAdmission = getCurrentCommunityProgramAdmission(demographicNo);

        if (currentCommunityAdmission != null) {
            processDischarge(currentCommunityAdmission.getProgramId(), demographicNo, notes, radioDischargeReason);
        }

        // Create and save admission object
        Admission admission = new Admission();
        admission.setAdmissionDate(new Date());
        admission.setAdmissionNotes(notes);
        admission.setAdmissionStatus(Admission.STATUS_CURRENT);
        admission.setClientId(demographicNo);
        admission.setProgramId(communityProgramId);
        admission.setProviderNo(providerNo);
        admission.setTeamId(null);
        admission.setTemporaryAdmission(false);
        admission.setRadioDischargeReason(radioDischargeReason);
        admission.setClientStatusId(null);
        saveAdmission(admission);

        if (dependents != null) {
            for (Integer l : dependents) {
                processDischargeToCommunity(communityProgramId, l, providerNo, notes, radioDischargeReason, null);
            }
        }
    }

    /**
     * Sets the admission data access object.
     *
     * @param dao AdmissionDao the admission DAO to inject
     */
    @Autowired
    public void setAdmissionDao(AdmissionDao dao) {
        this.dao = dao;
    }

    /**
     * Sets the program data access object.
     *
     * @param programDao ProgramDao the program DAO to inject
     */
    @Autowired
    public void setProgramDao(ProgramDao programDao) {
        this.programDao = programDao;
    }

    /**
     * Sets the program queue data access object.
     *
     * @param dao ProgramQueueDao the program queue DAO to inject
     */
    @Autowired
    public void setProgramQueueDao(ProgramQueueDao dao) {
        this.programQueueDao = dao;
    }

    /**
     * Sets the client referral data access object.
     *
     * @param dao ClientReferralDAO the client referral DAO to inject
     */
    @Autowired
    public void setClientReferralDAO(ClientReferralDAO dao) {
        this.clientReferralDAO = dao;
    }


    /**
     * Sets the program client status data access object.
     *
     * @param programClientStatusDAO ProgramClientStatusDAO the client status DAO to inject
     */
    @Autowired
    public void setProgramClientStatusDAO(ProgramClientStatusDAO programClientStatusDAO) {
        this.programClientStatusDAO = programClientStatusDAO;
    }

    /**
     * Sets the client restriction manager for service restriction enforcement.
     *
     * @param clientRestrictionManager ClientRestrictionManager the restriction manager to inject
     */
    @Autowired
    public void setClientRestrictionManager(ClientRestrictionManager clientRestrictionManager) {
        this.clientRestrictionManager = clientRestrictionManager;
    }


    /** {@inheritDoc} */
    public boolean isActiveInCurrentFacility(LoggedInInfo loggedInInfo, int demographicId) {
        List<Admission> results = getCurrentAdmissionsByFacility(demographicId, loggedInInfo.getCurrentFacility().getId());
        if (results != null && results.size() > 0) return (true);

        return (false);
    }

    /** {@inheritDoc} */
    public List getActiveAnonymousAdmissions() {
        return dao.getActiveAnonymousAdmissions();
    }

    /** {@inheritDoc} */
    public boolean wasInProgram(Integer programId, Integer clientId) {
        if (dao.getAdmission(programId, clientId) != null)
            return true;
        else
            return false;

    }


    /** {@inheritDoc} */
    public List<Admission> findAdmissionsByProgramAndDate(LoggedInInfo loggedInInfo, Integer programNo, Date day, int startIndex, int numToReturn) {
        List<Admission> results = dao.findAdmissionsByProgramAndDate(programNo, day, startIndex, numToReturn);

        for (Admission result : results) {
            LogAction.addLogSynchronous(loggedInInfo, "AdmissionManager.findAdmissionsByProgramAndDate", "admission id=" + result.getId());
        }
        return results;
    }

    /** {@inheritDoc} */
    public Integer findAdmissionsByProgramAndDateAsCount(LoggedInInfo loggedInInfo, Integer programNo, Date day) {
        Integer count = dao.findAdmissionsByProgramAndDateAsCount(programNo, day);

        return count;
    }
}