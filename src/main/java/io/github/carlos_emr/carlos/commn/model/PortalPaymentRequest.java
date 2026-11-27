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
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Date;

/**
 * A payment request sent to a patient through the portal for one Ontario invoice (billing through
 * the portal, Phase 0).
 *
 * <p>The portal receives only {@link #getInvoiceRef() invoiceRef}, an opaque random reference; this
 * row is the only place that says which invoice it stands for. The amount and tax are whole cents
 * taken from the invoice when the request is sent, never from the browser. Rows are never deleted:
 * {@link #getStatus() status} records what became of the request.
 */
@Entity
@Table(name = "portal_payment_request")
public class PortalPaymentRequest extends AbstractModel<Long> {

    /** Created locally; the portal has not yet confirmed it, so the patient cannot see it. */
    public static final String PENDING = "pending";
    /** The portal confirmed it: the patient can read it under Messages. */
    public static final String OPEN = "open";
    /** The invoice was paid in CARLOS; the portal may not have been told yet (see {@link #isPortalSynced()}). */
    public static final String PAID = "paid";
    /** Withdrawn by staff, for example because the invoice changed. */
    public static final String WITHDRAWN = "withdrawn";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Opaque random reference sent to the portal; never the invoice number. */
    @Column(name = "invoice_ref", length = 64, nullable = false, unique = true)
    private String invoiceRef;

    /** CARLOS's stable id for the send, so a retry returns the portal's original request. */
    @Column(name = "operation_id", length = 64, nullable = false, unique = true)
    private String operationId;

    @Column(name = "billing_no", nullable = false)
    private int billingNo;

    @Column(name = "demographic_no", nullable = false)
    private int demographicNo;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "tax_cents", nullable = false)
    private long taxCents;

    @Column(name = "category", length = 32, nullable = false)
    private String category;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    /** The portal's id for the request, known once it confirmed the send. */
    @Column(name = "portal_request_id")
    private Long portalRequestId;

    /** False while a paid request still has to be reported to the portal. */
    @Column(name = "portal_synced", nullable = false)
    private boolean portalSynced;

    @Column(name = "created_by", length = 6, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Column(name = "paid_at")
    private Date paidAt;

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) { this.id = id; }
    public String getInvoiceRef() { return invoiceRef; }
    public void setInvoiceRef(String invoiceRef) { this.invoiceRef = invoiceRef; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public int getBillingNo() { return billingNo; }
    public void setBillingNo(int billingNo) { this.billingNo = billingNo; }
    public int getDemographicNo() { return demographicNo; }
    public void setDemographicNo(int demographicNo) { this.demographicNo = demographicNo; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public long getTaxCents() { return taxCents; }
    public void setTaxCents(long taxCents) { this.taxCents = taxCents; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getDueOn() { return dueOn; }
    public void setDueOn(LocalDate dueOn) { this.dueOn = dueOn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPortalRequestId() { return portalRequestId; }
    public void setPortalRequestId(Long portalRequestId) { this.portalRequestId = portalRequestId; }
    public boolean isPortalSynced() { return portalSynced; }
    public void setPortalSynced(boolean portalSynced) { this.portalSynced = portalSynced; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getPaidAt() { return paidAt; }
    public void setPaidAt(Date paidAt) { this.paidAt = paidAt; }
}
