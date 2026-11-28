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
package io.github.carlos_emr.carlos.integration.patientportal.payment;

import io.github.carlos_emr.carlos.commn.model.PortalPaymentRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Payment requests sent through the portal. Callers own the transaction; see
 * {@link PortalPaymentRequestService}. Every query is parameterized.
 */
@Repository
public class PortalPaymentRequestDao {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public PortalPaymentRequest find(long id) {
        return entityManager.find(PortalPaymentRequest.class, id);
    }

    public PortalPaymentRequest findForUpdate(long id) {
        return entityManager.find(PortalPaymentRequest.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public void persist(PortalPaymentRequest request) {
        entityManager.persist(request);
    }

    /** The invoice's requests, newest first. */
    public List<PortalPaymentRequest> findByBillingNo(int billingNo) {
        return entityManager.createQuery("from PortalPaymentRequest where billingNo = :billingNo "
                        + "order by createdAt desc, id desc", PortalPaymentRequest.class)
                .setParameter("billingNo", billingNo).getResultList();
    }

    /**
     * The invoice's requests that are still waiting for its payment: created and not yet closed.
     * Locked, so a payment save and a withdrawal cannot both act on the same row.
     */
    public List<PortalPaymentRequest> findUnsettledForUpdate(int billingNo) {
        return entityManager.createQuery("from PortalPaymentRequest where billingNo = :billingNo "
                        + "and status in (:pending, :open) order by id", PortalPaymentRequest.class)
                .setParameter("billingNo", billingNo)
                .setParameter("pending", PortalPaymentRequest.PENDING)
                .setParameter("open", PortalPaymentRequest.OPEN)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    /** A bounded page of durably settled requests across invoices, after the last row examined. */
    public List<PortalPaymentRequest> paidPageAfter(long afterId, int limit) {
        return entityManager.createQuery("from PortalPaymentRequest where id > :afterId "
                        + "and status = :paid and portalSynced = false order by id", PortalPaymentRequest.class)
                .setParameter("afterId", afterId).setParameter("paid", PortalPaymentRequest.PAID)
                .setMaxResults(limit).getResultList();
    }

    /**
     * Paid requests the portal has not been told about yet, oldest first, for a bounded retry. A
     * request whose confirmation was lost has no portal id yet; the service finds it by reference.
     */
    public List<PortalPaymentRequest> findPaidNotSynced(int billingNo) {
        return entityManager.createQuery("from PortalPaymentRequest where billingNo = :billingNo "
                        + "and status = :paid and portalSynced = false "
                        + "order by id", PortalPaymentRequest.class)
                .setParameter("billingNo", billingNo).setParameter("paid", PortalPaymentRequest.PAID)
                .getResultList();
    }
}
