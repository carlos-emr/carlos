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
package io.github.carlos_emr.carlos.services;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.FacilityMessageDao;
import io.github.carlos_emr.carlos.commn.model.FacilityMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional implementation of the {@link OrganizationMessageManager} interface
 * that delegates to {@link FacilityMessageDao} for persistence operations.
 *
 * <p>Manages the lifecycle of facility messages including creation, updates,
 * and retrieval with various filtering strategies by facility and program.</p>
 *
 * @see OrganizationMessageManager
 * @see io.github.carlos_emr.carlos.commn.dao.FacilityMessageDao
 * @since 2026-03-17
 */
@Transactional
public class OrganizationMessageManagerImpl implements OrganizationMessageManager {

    @Autowired
    private FacilityMessageDao facilityMessageDao;

    /** {@inheritDoc} */
    public FacilityMessage getMessage(String messageId) {
        return facilityMessageDao.find(Integer.valueOf(messageId));
    }

    /** {@inheritDoc} */
    public void saveFacilityMessage(FacilityMessage msg) {
        // New messages have null or zero ID; persist them as new entities
        if (msg.getId() == null || msg.getId().intValue() == 0) {
            msg.setId(null);
            facilityMessageDao.persist(msg);
        } else {
            // Existing messages are merged to update their state
            facilityMessageDao.merge(msg);
        }
    }

    /** {@inheritDoc} */
    public List<FacilityMessage> getMessages() {
        return facilityMessageDao.getMessages();
    }

    /** {@inheritDoc} */
    public List<FacilityMessage> getMessagesByFacilityId(Integer facilityId) {
        if (facilityId == null || facilityId.intValue() == 0) {
            return null;
        }
        return facilityMessageDao.getMessagesByFacilityId(facilityId);
    }

    /** {@inheritDoc} */
    public List<FacilityMessage> getMessagesByFacilityIdOrNull(Integer facilityId) {
        if (facilityId == null || facilityId.intValue() == 0) {
            return null;
        }
        return facilityMessageDao.getMessagesByFacilityIdOrNull(facilityId);
    }

    /** {@inheritDoc} */
    public List<FacilityMessage> getMessagesByFacilityIdAndProgramId(Integer facilityId, Integer programId) {
        if (facilityId == null || facilityId.intValue() == 0) {
            return null;
        }
        return facilityMessageDao.getMessagesByFacilityIdAndProgramId(facilityId, programId);
    }

    /** {@inheritDoc} */
    public List<FacilityMessage> getMessagesByFacilityIdOrNullAndProgramIdOrNull(Integer facilityId, Integer programId) {
        if (facilityId == null || facilityId.intValue() == 0) {
            return null;
        }
        return facilityMessageDao.getMessagesByFacilityIdOrNullAndProgramIdOrNull(facilityId, programId);
    }
}
