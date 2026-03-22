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
import io.github.carlos_emr.carlos.PMmodule.exception.AlreadyAdmittedException;
import io.github.carlos_emr.carlos.PMmodule.exception.AlreadyQueuedException;
import io.github.carlos_emr.carlos.PMmodule.exception.ServiceRestrictionException;
import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.JointAdmissionDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.JointAdmission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional implementation of {@link ClientManager} for managing clients within the
 * CARLOS EMR Program Management module.
 *
 * <p>Handles client demographics, referral processing, joint admission (family) management,
 * demographic extensions, and health card validation. Coordinates with
 * {@link ProgramQueueManager} for queue operations, {@link AdmissionManager} for admission
 * checks, and {@link ClientRestrictionManager} for service restriction enforcement.</p>
 *
 * @see ClientManager
 * @see Demographic
 * @since 2005
 */
@Transactional
public class ClientManagerImpl implements ClientManager {

    private DemographicDao dao;
    private DemographicExtDao demographicExtDao;
    private ClientReferralDAO referralDAO;
    private JointAdmissionDao jointAdmissionDao;
    private ProgramQueueManager queueManager;
    private AdmissionManager admissionManager;
    private ClientRestrictionManager clientRestrictionManager;

    private boolean outsideOfDomainEnabled;

    /** {@inheritDoc} */
    public boolean isOutsideOfDomainEnabled() {
        return outsideOfDomainEnabled;
    }

    /** {@inheritDoc} */
    public Demographic getClientByDemographicNo(String demographicNo) {
        if (demographicNo == null || demographicNo.length() == 0) {
            return null;
        }
        return dao.getClientByDemographicNo(Integer.valueOf(demographicNo));
    }

    /** {@inheritDoc} */
    public List<Demographic> getClients() {
        return dao.getClients();
    }

    /** {@inheritDoc} */
    public List<Demographic> search(ClientSearchFormBean criteria, boolean returnOptinsOnly, boolean excludeMerged) {
        return dao.search(criteria, returnOptinsOnly, excludeMerged);
    }

    /** {@inheritDoc} */
    public List<Demographic> search(ClientSearchFormBean criteria) {
        return dao.search(criteria);
    }

    /** {@inheritDoc} */
    public List<ClientReferral> getReferrals() {
        return referralDAO.getReferrals();
    }

    /** {@inheritDoc} */
    public List<ClientReferral> getReferrals(String clientId) {
        return referralDAO.getReferrals(Long.valueOf(clientId));
    }

    /** {@inheritDoc} */
    public List<ClientReferral> getReferralsByFacility(Integer clientId, Integer facilityId) {
        return referralDAO.getReferralsByFacility(clientId.longValue(), facilityId);
    }

    /** {@inheritDoc} */
    public List<ClientReferral> getActiveReferrals(String clientId, String sourceFacilityId) {
        List<ClientReferral> results = referralDAO.getActiveReferrals(Long.valueOf(clientId), Integer.parseInt(sourceFacilityId));

        return results;
    }

    /** {@inheritDoc} */
    public ClientReferral getClientReferral(String id) {
        return referralDAO.getClientReferral(Long.valueOf(id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Saves the referral and automatically adds it to the program queue.</p>
     */
    public void saveClientReferral(ClientReferral referral) {

        referralDAO.saveClientReferral(referral);
        addClientReferralToProgramQueue(referral);
    }


    /** {@inheritDoc} */
    public void addClientReferralToProgramQueue(ClientReferral referral) {
        if (referral.getStatus().equalsIgnoreCase(ClientReferral.STATUS_ACTIVE)) {
            ProgramQueue queue = new ProgramQueue();
            queue.setClientId(referral.getClientId());
            queue.setNotes(referral.getNotes());
            queue.setProgramId(referral.getProgramId());
            queue.setProviderNo(Long.parseLong(referral.getProviderNo()));
            queue.setReferralDate(referral.getReferralDate());
            queue.setStatus(ProgramQueue.STATUS_ACTIVE);
            queue.setReferralId(referral.getId());
            queue.setTemporaryAdmission(referral.isTemporaryAdmission());
            queue.setPresentProblems(referral.getPresentProblems());
            queue.setVacancyName(referral.getSelectVacancy());

            queueManager.saveProgramQueue(queue);
        }
    }

    /** {@inheritDoc} */
    public List<ClientReferral> searchReferrals(ClientReferral referral) {
        return referralDAO.search(referral);
    }

    /** {@inheritDoc} */
    public void saveJointAdmission(JointAdmission admission) {
        if (admission == null) {
            throw new IllegalArgumentException();
        }

        jointAdmissionDao.persist(admission);
    }

    /** {@inheritDoc} */
    public List<JointAdmission> getDependents(Integer clientId) {
        return jointAdmissionDao.getSpouseAndDependents(clientId);
    }

    /** {@inheritDoc} */
    public List<Integer> getDependentsList(Integer clientId) {
        List<Integer> list = new ArrayList<Integer>();
        List<JointAdmission> jadms = jointAdmissionDao.getSpouseAndDependents(clientId);
        for (JointAdmission jadm : jadms) {
            list.add(jadm.getClientId());
        }
        return list;
    }

    /** {@inheritDoc} */
    public JointAdmission getJointAdmission(Integer clientId) {
        return jointAdmissionDao.getJointAdmission(clientId);
    }

    /** {@inheritDoc} */
    public boolean isClientDependentOfFamily(Integer clientId) {

        JointAdmission clientsJadm = null;
        if (clientId != null) {
            clientsJadm = getJointAdmission(Integer.valueOf(clientId.toString()));
        }
        if (clientsJadm != null && clientsJadm.getHeadClientId() != null) {
            return true;
        }
        return false;
    }


    /** {@inheritDoc} */
    public boolean isClientFamilyHead(Integer clientId) {

        List<JointAdmission> dependentList = getDependents(Integer.valueOf(clientId.toString()));
        if (dependentList != null && dependentList.size() > 0) {
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    public void removeJointAdmission(Integer clientId, String providerNo) {
        jointAdmissionDao.removeJointAdmission(clientId, providerNo);
    }

    /** {@inheritDoc} */
    public void removeJointAdmission(JointAdmission admission) {
        jointAdmissionDao.removeJointAdmission(admission);
    }

    /** {@inheritDoc} */
    public void processReferral(ClientReferral referral) throws AlreadyAdmittedException, AlreadyQueuedException, ServiceRestrictionException {
        processReferral(referral, false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates service restrictions, checks for existing admissions and queue entries,
     * then saves the referral and queues the client plus any dependents.</p>
     */
    public void processReferral(ClientReferral referral, boolean override) throws AlreadyAdmittedException, AlreadyQueuedException, ServiceRestrictionException {
        if (!override) {
            // check if there's a service restriction in place on this individual for this program
            ProgramClientRestriction restrInPlace = clientRestrictionManager.checkClientRestriction(referral.getProgramId().intValue(), referral.getClientId().intValue(), new Date());
            if (restrInPlace != null) {
                throw new ServiceRestrictionException("service restriction in place", restrInPlace);
            }
        }

        Admission currentAdmission = admissionManager.getCurrentAdmission(String.valueOf(referral.getProgramId()), referral.getClientId().intValue());
        if (currentAdmission != null) {
            referral.setStatus(ClientReferral.STATUS_REJECTED);
            referral.setCompletionNotes("Client currently admitted");
            referral.setCompletionDate(new Date());

            //saveClientReferral(referral); //???what's the point to save it if it's already admitted
            throw new AlreadyAdmittedException();
        }

        ProgramQueue queue = queueManager.getActiveProgramQueue(String.valueOf(referral.getProgramId()), String.valueOf(referral.getClientId()));
        if (queue != null) {
            referral.setStatus(ClientReferral.STATUS_REJECTED);
            referral.setCompletionNotes("Client already in queue");
            referral.setCompletionDate(new Date());

            //saveClientReferral(referral); //???what's the point to save it if's already in queue
            throw new AlreadyQueuedException();
        }

        saveClientReferral(referral);
        List<JointAdmission> dependents = getDependents(Long.valueOf(referral.getClientId()).intValue());
        for (JointAdmission jadm : dependents) {
            referral.setClientId(Long.valueOf(jadm.getClientId()));
            saveClientReferral(referral);
        }

    }

    /** {@inheritDoc} */
    public void saveClient(Demographic client) {
        dao.saveClient(client);
    }

    /** {@inheritDoc} */
    public DemographicExt getDemographicExt(String id) {
        return demographicExtDao.getDemographicExt(Integer.valueOf(id));
    }

    /** {@inheritDoc} */
    public List<DemographicExt> getDemographicExtByDemographicNo(int demographicNo) {
        return demographicExtDao.getDemographicExtByDemographicNo(demographicNo);
    }

    /** {@inheritDoc} */
    public DemographicExt getDemographicExt(int demographicNo, String key) {
        return demographicExtDao.getDemographicExt(demographicNo, key);
    }

    /** {@inheritDoc} */
    public void updateDemographicExt(DemographicExt de) {
        demographicExtDao.updateDemographicExt(de);
    }

    /** {@inheritDoc} */
    public void saveDemographicExt(int demographicNo, String key, String value) {
        demographicExtDao.saveDemographicExt(demographicNo, key, value);
    }

    /** {@inheritDoc} */
    public void removeDemographicExt(String id) {
        demographicExtDao.removeDemographicExt(Integer.valueOf(id));
    }

    /** {@inheritDoc} */
    public void removeDemographicExt(int demographicNo, String key) {
        demographicExtDao.removeDemographicExt(demographicNo, key);
    }

    /**
     * Sets the joint admission data access object.
     *
     * @param jointAdmissionDao JointAdmissionDao the joint admission DAO to inject
     */
    public void setJointAdmissionDao(JointAdmissionDao jointAdmissionDao) {
        this.jointAdmissionDao = jointAdmissionDao;
    }

    /**
     * Sets the demographic data access object.
     *
     * @param dao DemographicDao the demographic DAO to inject
     */
    @Autowired
    public void setDemographicDao(DemographicDao dao) {
        this.dao = dao;
    }

    /**
     * Sets the demographic extension data access object.
     *
     * @param dao DemographicExtDao the demographic extension DAO to inject
     */
    @Autowired
    public void setDemographicExtDao(DemographicExtDao dao) {
        this.demographicExtDao = dao;
    }

    /**
     * Sets the client referral data access object.
     *
     * @param dao ClientReferralDAO the client referral DAO to inject
     */
    @Autowired
    public void setClientReferralDAO(ClientReferralDAO dao) {
        this.referralDAO = dao;
    }

    /**
     * Sets the program queue manager.
     *
     * @param mgr ProgramQueueManager the queue manager to inject
     */
    @Autowired
    public void setProgramQueueManager(ProgramQueueManager mgr) {
        this.queueManager = mgr;
    }

    /**
     * Sets the admission manager.
     *
     * @param mgr AdmissionManager the admission manager to inject
     */
    @Autowired
    public void setAdmissionManager(AdmissionManager mgr) {
        this.admissionManager = mgr;
    }

    /**
     * Sets the client restriction manager.
     *
     * @param clientRestrictionManager ClientRestrictionManager the restriction manager to inject
     */
    @Autowired
    public void setClientRestrictionManager(ClientRestrictionManager clientRestrictionManager) {
        this.clientRestrictionManager = clientRestrictionManager;
    }

    @Autowired
    public void setOutsideOfDomainEnabled(boolean outsideOfDomainEnabled) {
        this.outsideOfDomainEnabled = outsideOfDomainEnabled;
    }


    public boolean checkHealthCardExists(String hin, String hcType) {
        List<Demographic> results = this.dao.searchByHealthCard(hin, hcType);
        return (results.size() > 0) ? true : false;
    }
}