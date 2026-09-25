/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Outcome;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.State;
import jakarta.persistence.LockModeType;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link PatientPortalInviteDeliveryDao}.
 *
 * @since 2026-09-22
 */
@Repository
public class PatientPortalInviteDeliveryDaoImpl extends AbstractDaoImpl<PatientPortalInviteDelivery>
        implements PatientPortalInviteDeliveryDao {

    public PatientPortalInviteDeliveryDaoImpl() {
        super(PatientPortalInviteDelivery.class);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PatientPortalInviteDelivery claim(PatientPortalInviteDelivery delivery) {
        entityManager.persist(delivery);
        entityManager.flush();
        return delivery;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PatientPortalInviteDelivery advance(Long id, State expected, State next,
            Consumer<PatientPortalInviteDelivery> change) {
        // The row lock plus the state check make this a compare-and-set: two requests racing on one
        // attempt cannot both move it out of the same state.
        PatientPortalInviteDelivery row =
                entityManager.find(PatientPortalInviteDelivery.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (row == null || row.getState() != expected) {
            return null;
        }
        if (change != null) {
            change.accept(row);
        }
        row.setState(next);
        entityManager.flush();
        return row;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<PatientPortalInviteDelivery> findRecentByDemographic(int demographicNo, int limit) {
        return entityManager
                .createQuery("SELECT d FROM PatientPortalInviteDelivery d WHERE d.demographicNo = :demographicNo "
                        + "ORDER BY d.createdAt DESC, d.id DESC", PatientPortalInviteDelivery.class)
                .setParameter("demographicNo", demographicNo)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean release(Long id, State claimed, State previous, Outcome outcome, Date updatedAt) {
        return entityManager
                .createQuery("UPDATE PatientPortalInviteDelivery d SET d.state = :previous, d.outcome = :outcome, "
                        + "d.updatedAt = :updatedAt, d.version = d.version + 1 "
                        + "WHERE d.id = :id AND d.state = :claimed")
                .setParameter("previous", previous)
                .setParameter("outcome", outcome)
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .setParameter("claimed", claimed)
                .executeUpdate() == 1;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<PatientPortalInviteDelivery> findUnfinishedByDemographic(int demographicNo) {
        return entityManager
                .createQuery("SELECT d FROM PatientPortalInviteDelivery d WHERE d.demographicNo = :demographicNo "
                        + "AND d.state IN :states ORDER BY d.createdAt, d.id", PatientPortalInviteDelivery.class)
                .setParameter("demographicNo", demographicNo)
                .setParameter("states", State.unfinished())
                .getResultList();
    }
}
