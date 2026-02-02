//CHECKSTYLE:OFF
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ClientReferralDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramQueueDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyTemplateDao;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;

public interface ProgramQueueManager {
    void setVacancyDao(VacancyDao vacancyDao);

    void setVacancyTemplateDao(VacancyTemplateDao vacancyTemplateDao);

    void setProgramQueueDao(ProgramQueueDao dao);

    void setClientReferralDAO(ClientReferralDAO dao);

    ProgramQueue getProgramQueue(String queueId);

    List<ProgramQueue> getProgramQueuesByProgramId(Long programId);

    void saveProgramQueue(ProgramQueue programQueue);

    List<ProgramQueue> getActiveProgramQueuesByProgramId(Long programId);

    ProgramQueue getActiveProgramQueue(String programId, String demographicNo);

    void rejectQueue(String programId, String clientId, String notes, String rejectionReason);
}
