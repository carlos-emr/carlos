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

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnInvoiceTotalsService;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.PortalPaymentRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalPaymentRequestDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalPaymentRequestRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asks a patient, through the portal, to pay an Ontario invoice, and closes the request when the
 * invoice is paid (billing through the portal, Phase 0). The portal takes no payment: staff record
 * it in CARLOS's own third-party payment screen.
 *
 * <p><b>The amount is the invoice's, never the browser's.</b> The request carries the balance
 * owing now ({@link BillingOnInvoiceTotalsService#calculateBalanceOwing}) in whole cents and the
 * invoice's GST as its tax portion, read here when the request is sent. A request whose amount
 * would differ from the open one for the same invoice is refused: staff withdraw the old one first,
 * so the patient is never shown two amounts for one bill.
 *
 * <p><b>The portal is called outside any database transaction</b>, so a slow or failing portal never
 * holds a lock on an invoice. The local row is created first ({@code pending}) with fixed random
 * references; a lost answer is repaired by sending the same operation again, which the portal
 * answers with the original request.
 *
 * <p><b>Paying is separate from telling the portal.</b> {@link #settleLocally} marks the invoice's
 * open requests paid when the balance reaches zero, in its own transaction, and needs no portal
 * privilege; {@link #reportPaid} tells the portal and may be retried by any later call, so a portal
 * outage never undoes or delays recording a payment.
 */
@Service
public class PortalPaymentRequestService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    /** The portal accepts a due date up to this many days ahead. */
    static final int MAX_DUE_DAYS_AHEAD = 365;
    private static final BigDecimal CENTS = BigDecimal.valueOf(100);

    private final PortalPaymentRequestDao requests;
    private final BillingONCHeader1Dao headers;
    private final BillingOnInvoiceTotalsService totals;
    private final BillingONExtDao extensions;
    private final TransactionTemplate transactions;
    private final Supplier<PatientPortalService> portal;
    private final Clock clock;

    /**
     * The portal client is looked up only when a call needs it, the way the staff actions do: its
     * bean is not an injection candidate, and it fails on purpose while the portal is misconfigured.
     */
    @Autowired
    public PortalPaymentRequestService(PortalPaymentRequestDao requests, BillingONCHeader1Dao headers,
            BillingOnInvoiceTotalsService totals, BillingONExtDao extensions,
            PlatformTransactionManager transactionManager) {
        this(requests, headers, totals, extensions, new TransactionTemplate(transactionManager),
                () -> SpringUtils.getBean(PatientPortalService.class), Clock.systemDefaultZone());
    }

    PortalPaymentRequestService(PortalPaymentRequestDao requests, BillingONCHeader1Dao headers,
            BillingOnInvoiceTotalsService totals, BillingONExtDao extensions, TransactionTemplate transactions,
            Supplier<PatientPortalService> portal, Clock clock) {
        this.requests = requests;
        this.headers = headers;
        this.totals = totals;
        this.extensions = extensions;
        this.transactions = transactions;
        this.portal = portal;
        this.clock = clock;
    }

    /** What {@link #send} did: the stored request, and whether this call created it. */
    public record Sent(PortalPaymentRequest request, boolean created) {}

    /**
     * Sends a request for the invoice's balance owing now. A repeat for the same open request
     * returns it (the portal's operation is idempotent).
     *
     * @throws PortalPaymentRequestException the invoice is not this patient's, has nothing owing,
     *     the amount or due date cannot be sent, or a different request is already open
     * @throws PatientPortalException the portal refused or could not be reached; a patient without
     *     a portal account is {@link PatientPortalException#isAccountAbsent()} and leaves nothing behind
     */
    public Sent send(PatientPortalStaffContext staff, String providerNo, int demographicNo, int billingNo,
            String category, LocalDate dueOn) {
        LocalDate today = LocalDate.now(clock);
        if (dueOn == null || dueOn.isBefore(today) || dueOn.isAfter(today.plusDays(MAX_DUE_DAYS_AHEAD))) {
            throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.DUE_DATE_INVALID);
        }
        PortalPaymentRequest local = transactions.execute(status -> prepare(
                providerNo, demographicNo, billingNo, category, dueOn));
        PatientPortalPaymentRequestRequest wire = new PatientPortalPaymentRequestRequest(local.getOperationId(),
                local.getInvoiceRef(), local.getCategory(), local.getAmountCents(), local.getTaxCents(),
                local.getDueOn());
        PatientPortalPaymentRequestDto.Creation confirmed;
        try {
            confirmed = portal.get().createPaymentRequest(demographicNo, wire, staff);
        } catch (PatientPortalException refused) {
            if (refused.isAccountAbsent() && PortalPaymentRequest.PENDING.equals(local.getStatus())
                    && local.getPortalRequestId() == null) {
                // The portal stored nothing for a patient with no account: close the local row so a
                // later send, once the patient has an account, starts clean.
                transactions.executeWithoutResult(status -> close(local.getId()));
            }
            throw refused;
        }
        boolean created = Boolean.TRUE.equals(transactions.execute(status ->
                confirm(local.getId(), confirmed.request().id())));
        return new Sent(requests.find(local.getId()), created);
    }

    /** Reads the invoice, and creates or reuses the pending row. Runs in a transaction. */
    private PortalPaymentRequest prepare(String providerNo, int demographicNo, int billingNo, String category,
            LocalDate dueOn) {
        // The invoice's own row is locked first (the payment save locks it too): two sends for one
        // invoice, a double click or two staff, queue here, so the second finds the first request
        // below instead of both inserting an open request with its own reference.
        BillingONCHeader1 header = headers.findForUpdate(billingNo);
        if (header == null || header.getDemographicNo() == null || header.getDemographicNo() != demographicNo) {
            throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.INVOICE_NOT_FOUND);
        }
        BigDecimal owing = totals.calculateBalanceOwing(billingNo);
        if (owing == null || owing.signum() <= 0) {
            throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.NOTHING_OWING);
        }
        long amountCents = cents(owing);
        if (amountCents < PatientPortalPaymentRequestRequest.MIN_AMOUNT_CENTS
                || amountCents > PatientPortalPaymentRequestRequest.MAX_AMOUNT_CENTS) {
            throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.AMOUNT_OUT_OF_RANGE);
        }
        long taxCents = taxCentsOf(extensions.getAccountVal(billingNo, BillingONExtDao.KEY_GST),
                header.getTotal(), owing, amountCents);
        for (PortalPaymentRequest open : requests.findUnsettledForUpdate(billingNo)) {
            if (open.getAmountCents() == amountCents && open.getTaxCents() == taxCents
                    && open.getCategory().equals(category) && open.getDueOn().equals(dueOn)) {
                return open;
            }
            throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.OPEN_REQUEST_DIFFERS);
        }
        // Only an unsettled (pending or open) request is ever reused above. After a withdrawal, a
        // resend is a NEW request with a NEW random reference and operation id: the portal keeps
        // invoice_ref unique per clinic for good, even for a withdrawn request, so reusing the old
        // reference would be refused as a different request.
        PortalPaymentRequest created = new PortalPaymentRequest();
        created.setInvoiceRef(UUID.randomUUID().toString());
        created.setOperationId(UUID.randomUUID().toString());
        created.setBillingNo(billingNo);
        created.setDemographicNo(demographicNo);
        created.setAmountCents(amountCents);
        created.setTaxCents(taxCents);
        created.setCategory(category);
        created.setDueOn(dueOn);
        created.setStatus(PortalPaymentRequest.PENDING);
        created.setPortalSynced(false);
        created.setCreatedBy(providerNo);
        created.setCreatedAt(Date.from(clock.instant()));
        requests.persist(created);
        return created;
    }

    /** @return true when this call moved the row from pending to open */
    private boolean confirm(long id, long portalId) {
        PortalPaymentRequest row = requests.findForUpdate(id);
        boolean created = PortalPaymentRequest.PENDING.equals(row.getStatus());
        if (created) {
            row.setStatus(PortalPaymentRequest.OPEN);
        }
        row.setPortalRequestId(portalId);
        return created;
    }

    private void close(long id) {
        PortalPaymentRequest row = requests.findForUpdate(id);
        if (PortalPaymentRequest.PENDING.equals(row.getStatus())) {
            row.setStatus(PortalPaymentRequest.WITHDRAWN);
        }
    }

    /**
     * Withdraws a request that has not been paid; a repeat is a no-op. The portal is told first, so
     * a request is never withdrawn here while the patient can still read it.
     *
     * @throws PortalPaymentRequestException not found for this invoice, or already paid
     */
    public PortalPaymentRequest withdraw(PatientPortalStaffContext staff, int demographicNo, int billingNo,
            long requestId) {
        PortalPaymentRequest row = transactions.execute(status -> requests.find(requestId));
        if (row == null || row.getBillingNo() != billingNo || row.getDemographicNo() != demographicNo) {
            throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.REQUEST_NOT_FOUND);
        }
        if (PortalPaymentRequest.PAID.equals(row.getStatus())) {
            throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.REQUEST_PAID);
        }
        if (PortalPaymentRequest.WITHDRAWN.equals(row.getStatus())) {
            return row;
        }
        Long portalId = portalIdOf(staff, row);
        if (portalId != null) {
            portal.get().withdrawPaymentRequest(demographicNo, portalId, staff);
        }
        transactions.executeWithoutResult(status -> {
            PortalPaymentRequest locked = requests.findForUpdate(requestId);
            if (PortalPaymentRequest.PAID.equals(locked.getStatus())) {
                throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.REQUEST_PAID);
            }
            locked.setStatus(PortalPaymentRequest.WITHDRAWN);
        });
        return requests.find(requestId);
    }

    /**
     * The portal's id for a row: the stored one, or, when the confirmation was lost, the one the
     * portal lists for this patient under the row's reference. Null when the portal has none.
     */
    private Long portalIdOf(PatientPortalStaffContext staff, PortalPaymentRequest row) {
        if (row.getPortalRequestId() != null) {
            return row.getPortalRequestId();
        }
        for (PatientPortalPaymentRequestDto listed : portal.get().listPaymentRequests(row.getDemographicNo(), staff)) {
            if (row.getInvoiceRef().equals(listed.invoiceRef())) {
                long id = listed.id();
                transactions.executeWithoutResult(status -> requests.findForUpdate(row.getId()).setPortalRequestId(id));
                return id;
            }
        }
        return null;
    }

    /**
     * Marks the invoice's unsettled requests paid once its balance is zero or less. Runs in its own
     * transaction after the payment was saved, needs no portal privilege, and does not call the
     * portal. Safe to repeat.
     *
     * @return how many requests it closed
     */
    public int settleLocally(int billingNo) {
        Integer settled = transactions.execute(status -> {
            List<PortalPaymentRequest> open = requests.findUnsettledForUpdate(billingNo);
            if (open.isEmpty()) {
                return 0;
            }
            BigDecimal owing = totals.calculateBalanceOwing(billingNo);
            if (owing == null || owing.signum() > 0) {
                return 0;
            }
            Date now = Date.from(clock.instant());
            for (PortalPaymentRequest row : open) {
                row.setStatus(PortalPaymentRequest.PAID);
                row.setPaidAt(now);
                row.setPortalSynced(false);
            }
            return open.size();
        });
        return settled == null ? 0 : settled;
    }

    /**
     * Tells the portal about the invoice's paid requests it has not been told about. Best effort: a
     * portal that cannot be reached leaves the rows for the next call, and never throws, because the
     * payment is already recorded.
     *
     * @param staff null when the caller holds no portal privilege: nothing is sent
     * @return how many requests the portal now knows are paid
     */
    public int reportPaid(PatientPortalStaffContext staff, int billingNo) {
        if (staff == null) {
            return 0;
        }
        List<PortalPaymentRequest> waiting = transactions.execute(status -> requests.findPaidNotSynced(billingNo));
        int reported = 0;
        for (PortalPaymentRequest row : waiting == null ? List.<PortalPaymentRequest>of() : waiting) {
            try {
                // A request whose confirmation was lost has no portal id yet: find it by reference. If
                // the portal never stored it there is nothing to close and the row is simply synced.
                Long portalId = portalIdOf(staff, row);
                if (portalId != null) {
                    portal.get().markPaymentRequestPaid(row.getDemographicNo(), portalId, staff);
                }
                markSynced(row.getId());
                reported++;
            } catch (PatientPortalException refused) {
                if (refused.kind() == PatientPortalException.Kind.CONFLICT) {
                    // The portal will never take this one as paid (it was withdrawn there while the
                    // payment was being saved): stop asking, or every later view would warn again.
                    markSynced(row.getId());
                }
                LOGGER.warn("A paid payment request was not reported to the portal; exceptionClass={}",
                        refused.getClass().getSimpleName());
            } catch (IllegalStateException unreachable) {
                // Nothing about the request is logged: the class names the failure, the row stays.
                LOGGER.warn("A paid payment request was not reported to the portal; exceptionClass={}",
                        unreachable.getClass().getSimpleName());
            }
        }
        return reported;
    }

    private void markSynced(long id) {
        transactions.executeWithoutResult(status -> requests.findForUpdate(id).setPortalSynced(true));
    }

    /**
     * The invoice's requests, newest first, after settling and reporting any payment that is
     * waiting, so staff looking at the screen repair a missed push.
     */
    public List<PortalPaymentRequest> listForInvoice(PatientPortalStaffContext staff, int demographicNo,
            int billingNo) {
        // The staff context was resolved for this patient only, so the invoice must be theirs before
        // anything is settled or reported for it.
        BillingONCHeader1 header = transactions.execute(status -> headers.find(billingNo));
        if (header == null || header.getDemographicNo() == null || header.getDemographicNo() != demographicNo) {
            throw new PortalPaymentRequestException(PortalPaymentRequestException.Reason.INVOICE_NOT_FOUND);
        }
        settleLocally(billingNo);
        reportPaid(staff, billingNo);
        List<PortalPaymentRequest> listed = transactions.execute(status -> requests.findByBillingNo(billingNo));
        return listed == null ? List.of() : listed;
    }

    /**
     * The tax portion of the amount due now: the invoice's GST scaled by the share of the invoice
     * still owing, rounded half even to the cent, so an invoice of 100.00 plus 13.00 GST with 56.50
     * owing says 6.50, not 13.00. Nothing paid yet gives the whole GST. Never more than the amount
     * itself, and 0 when the invoice total or GST is unknown or not positive.
     */
    static long taxCentsOf(BigDecimal gst, BigDecimal invoiceTotal, BigDecimal owing, long amountCents) {
        if (gst == null || invoiceTotal == null || gst.signum() <= 0 || invoiceTotal.signum() <= 0) {
            return 0;
        }
        BigDecimal share = gst.multiply(owing).divide(invoiceTotal, 2, RoundingMode.HALF_EVEN);
        return Math.min(amountCents, Math.max(0, cents(share)));
    }

    /** Whole cents of a money amount, rounded half up to the cent. */
    static long cents(BigDecimal money) {
        return money.setScale(2, RoundingMode.HALF_UP).multiply(CENTS).longValueExact();
    }
}
