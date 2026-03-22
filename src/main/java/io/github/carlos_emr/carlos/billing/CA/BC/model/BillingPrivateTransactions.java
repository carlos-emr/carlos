/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.billing.CA.BC.model;

import static jakarta.persistence.GenerationType.IDENTITY;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import io.github.carlos_emr.carlos.entities.PrivateBillTransaction;

/**
 * JPA entity representing a private billing transaction in the BC billing system.
 * Maps to the {@code billing_private_transactions} table, storing payment receipt
 * information for private (non-MSP) billing transactions.
 *
 * @since 2026-03-17
 */
@Entity
@Table(name = "billing_private_transactions")
public class BillingPrivateTransactions extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @Column(name = "billingmaster_no", nullable = false)
    private int billingmasterNo;

    @Column(name = "amount_received", nullable = false)
    private double amountReceived;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "creation_date", nullable = false, length = 19)
    private Date creationDate;

    @Column(name = "payment_type_id", nullable = false)
    private int paymentTypeId;

    public BillingPrivateTransactions() {
    }

    public BillingPrivateTransactions(int billingmasterNo, double amountReceived, Date creationDate, int paymentTypeId) {
        this.billingmasterNo = billingmasterNo;
        this.amountReceived = amountReceived;
        this.creationDate = creationDate;
        this.paymentTypeId = paymentTypeId;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getBillingmasterNo() {
        return this.billingmasterNo;
    }

    public void setBillingmasterNo(int billingmasterNo) {
        this.billingmasterNo = billingmasterNo;
    }

    public double getAmountReceived() {
        return this.amountReceived;
    }

    public void setAmountReceived(double amountReceived) {
        this.amountReceived = amountReceived;
    }

    public Date getCreationDate() {
        return this.creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public int getPaymentTypeId() {
        return this.paymentTypeId;
    }

    public void setPaymentTypeId(int paymentTypeId) {
        this.paymentTypeId = paymentTypeId;
    }

    /**
     * Converts this entity to a {@link PrivateBillTransaction} transfer object.
     * If the target is null, a new instance is created.
     *
     * @param target PrivateBillTransaction the target object to populate, or null to create a new one
     * @return PrivateBillTransaction the populated transfer object
     */
    public PrivateBillTransaction toTx(PrivateBillTransaction target) {
        if (target == null)
            target = new PrivateBillTransaction();
        target.setAmount_received(getAmountReceived());
        target.setBillingmaster_no(getBillingmasterNo());
        target.setCreation_date(getCreationDate());
        target.setId(getId());
        target.setPayment_type(getPaymentTypeId());

        return target;
    }
}
