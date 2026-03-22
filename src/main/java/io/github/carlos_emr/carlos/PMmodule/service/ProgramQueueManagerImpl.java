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
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramQueueDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyTemplateDao;
import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;
import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
import io.github.carlos_emr.carlos.PMmodule.model.VacancyTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional implementation of {@link ProgramQueueManager} for managing program queues
 * within the CARLOS EMR Program Management module.
 *
 * <p>Enriches active queue entries with vacancy and template name information from referrals.
 * Handles queue rejection by updating both the queue entry and the associated referral status.</p>
 *
 * @see ProgramQueueManager
 * @see ProgramQueue
 * @since 2005
 */
@Transactional
public class ProgramQueueManagerImpl implements ProgramQueueManager {
    private ProgramQueueDao dao;
    private ClientReferralDAO referralDAO;
    private VacancyTemplateDao vacancyTemplateDao;
    private VacancyDao vacancyDao;

    /** {@inheritDoc} */
    public void setVacancyDao(VacancyDao vacancyDao) {
        this.vacancyDao = vacancyDao;
    }

    /** {@inheritDoc} */
    public void setVacancyTemplateDao(VacancyTemplateDao vacancyTemplateDao) {
        this.vacancyTemplateDao = vacancyTemplateDao;
    }

    /** {@inheritDoc} */
    public void setProgramQueueDao(ProgramQueueDao dao) {
        this.dao = dao;
    }

    public void setClientReferralDAO(ClientReferralDAO dao) {
        this.referralDAO = dao;
    }

    public ProgramQueue getProgramQueue(String queueId) {
        ProgramQueue pq = dao.getProgramQueue(Long.valueOf(queueId));
        return pq;
    }

    public List<ProgramQueue> getProgramQueuesByProgramId(Long programId) {
        return dao.getProgramQueuesByProgramId(programId);
    }

    public void saveProgramQueue(ProgramQueue programQueue) {
        dao.saveProgramQueue(programQueue);
    }

    public List<ProgramQueue> getActiveProgramQueuesByProgramId(Long programId) {
        List<ProgramQueue> queue = dao.getActiveProgramQueuesByProgramId(programId);
        List<ProgramQueue> q = new ArrayList<ProgramQueue>();
        //Get vacancy name and vacancy template name.
        for (ProgramQueue pq : queue) {
            if (pq.getReferralId() != null) {
                ClientReferral ref = referralDAO.getClientReferral(pq.getReferralId());
                if (ref.getSelectVacancy() != null) {
                    pq.setVacancyName(ref.getSelectVacancy() == null ? "" : ref.getSelectVacancy());
                    Vacancy vacancy = vacancyDao.getVacancyById(ref.getVacancyId() == null ? 0 : ref.getVacancyId());
                    if (vacancy != null) {
                        VacancyTemplate vt = vacancyTemplateDao.getVacancyTemplate(vacancy.getTemplateId() == null ? 0 : vacancy.getTemplateId());
                        pq.setVacancyTemplateName(vt != null ? vt.getName() : "");
                    }

                }
            }
            q.add(pq);
        }
        return q;
    }

    public ProgramQueue getActiveProgramQueue(String programId, String demographicNo) {
        return dao.getActiveProgramQueue(Long.valueOf(programId), Long.valueOf(demographicNo));
    }

    public void rejectQueue(String programId, String clientId, String notes, String rejectionReason) {
        ProgramQueue queue = getActiveProgramQueue(programId, clientId);
        if (queue == null) {
            return;
        }
        ClientReferral referral = this.referralDAO.getClientReferral(queue.getReferralId());
        if (referral != null) {
            referral.setStatus(ClientReferral.STATUS_REJECTED);
            referral.setCompletionDate(new Date());
            referral.setCompletionNotes(notes);
            referral.setRadioRejectionReason(rejectionReason);
            this.referralDAO.saveClientReferral(referral);
        }
        queue.setStatus(ProgramQueue.STATUS_REJECTED);

        this.saveProgramQueue(queue);
    }
}
