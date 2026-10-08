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
package io.github.carlos_emr.carlos.integration.patientportal.booking;

import io.github.carlos_emr.carlos.commn.model.PortalBookingOffer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Offered portal times (#3850). Callers own the transaction; see {@link PortalBookingChoiceService}. */
@Repository
public class PortalBookingOfferDao {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Serialises portal bookings for one provider, across sessions and application nodes, by
     * locking that provider's row. It must be the first statement of the booking transaction: on
     * MariaDB a consistent read taken earlier would hide a booking committed while this waited.
     *
     * @return false when the provider does not exist
     */
    public boolean lockProvider(String providerNo) {
        return !entityManager.createNativeQuery("SELECT provider_no FROM provider WHERE provider_no = ?1 FOR UPDATE")
                .setParameter(1, providerNo).getResultList().isEmpty();
    }

    public PortalBookingOffer find(String slotId) {
        return entityManager.find(PortalBookingOffer.class, slotId);
    }

    public PortalBookingOffer findForUpdate(String slotId) {
        return entityManager.find(PortalBookingOffer.class, slotId, LockModeType.PESSIMISTIC_WRITE);
    }

    public void persist(PortalBookingOffer offer) {
        entityManager.persist(offer);
    }

    /** The times sent with one prompt creation, in the order they were offered. */
    public List<PortalBookingOffer> findByOperation(String operationId) {
        return entityManager.createQuery("from PortalBookingOffer where operationId = :operation "
                        + "order by createdAt, startTime, slotId", PortalBookingOffer.class)
                .setParameter("operation", operationId).getResultList();
    }

    /** Times still on offer for a prompt that have not started. */
    public List<PortalBookingOffer> findOpenForPrompt(long promptId, Date now) {
        return entityManager.createQuery("from PortalBookingOffer where promptId = :prompt and status = :offered "
                        + "and startTime > :now order by startTime", PortalBookingOffer.class)
                .setParameter("prompt", promptId).setParameter("offered", PortalBookingOffer.OFFERED)
                .setParameter("now", now).getResultList();
    }

    /** Replacements already made for a refused pick, so a retried report sends the same ones. */
    public List<PortalBookingOffer> findReplacements(long promptId, long choiceId) {
        return entityManager.createQuery("from PortalBookingOffer where promptId = :prompt and choiceId = :choice "
                        + "and status = :offered order by startTime", PortalBookingOffer.class)
                .setParameter("prompt", promptId).setParameter("choice", choiceId)
                .setParameter("offered", PortalBookingOffer.OFFERED).getResultList();
    }

    /** Once one time is booked the portal drops the others; close them here too. */
    public int closeOthers(long promptId, String bookedSlotId, Date now) {
        return entityManager.createQuery("update PortalBookingOffer set status = :closed, updatedAt = :now "
                        + "where promptId = :prompt and slotId <> :booked and status = :offered")
                .setParameter("closed", PortalBookingOffer.CLOSED).setParameter("now", now)
                .setParameter("prompt", promptId).setParameter("booked", bookedSlotId)
                .setParameter("offered", PortalBookingOffer.OFFERED).executeUpdate();
    }
}
