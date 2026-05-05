/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

package io.github.carlos_emr.carlos.billings.ca.on.dto;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

/**
 * Immutable data transfer object for one Ontario claim item/detail row.
 * Stores the fixed-width MOH item fields in the string shape expected by
 * billing assemblers, export services, and JSP compatibility getters.
 */
public record BillingClaimItemDto(
        String id,
        String claimHeaderId,
        String transactionId,
        String recordId,
        String serviceCode,
        String fee,
        String serviceNumber,
        String serviceDate,
        String dx,
        String dx1,
        String dx2,
        String paid,
        String refund,
        String credit,
        String discount,
        String status,
        String timestamp,
        String location,
        String patientName) {

    public BillingClaimItemDto {
        fee = normalizeMoney("fee", fee);
        paid = normalizeMoney("paid", paid);
        refund = normalizeMoney("refund", refund);
        credit = normalizeMoney("credit", credit);
        discount = normalizeMoney("discount", discount);
    }

    public BillingClaimItemDto() {
        this(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    public BillingClaimItemDto(BillingClaimItemDto copy) {
        this(copy.id, copy.claimHeaderId, copy.transactionId, copy.recordId,
                copy.serviceCode, copy.fee, copy.serviceNumber, copy.serviceDate,
                copy.dx, copy.dx1, copy.dx2, copy.paid, copy.refund,
                copy.credit, copy.discount, copy.status, copy.timestamp,
                copy.location, copy.patientName);
    }

    public BillingClaimItemDto withId(String id) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withClaimHeaderId(String claimHeaderId) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withTransactionId(String transactionId) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withRecordId(String recordId) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withServiceCode(String serviceCode) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withFee(String fee) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withServiceNumber(String serviceNumber) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withServiceDate(String serviceDate) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withDx(String dx) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withDx1(String dx1) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withDx2(String dx2) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withPaid(String paid) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withRefund(String refund) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withCredit(String credit) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withDiscount(String discount) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withStatus(String status) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withTimestamp(String timestamp) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withLocation(String location) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }

    public BillingClaimItemDto withPatientName(String patientName) {
        return new BillingClaimItemDto(id, claimHeaderId, transactionId, recordId, serviceCode, fee, serviceNumber, serviceDate, dx, dx1, dx2, paid, refund, credit, discount, status, timestamp, location, patientName);
    }


    public String getId() { return id; }
    public String getFee() { return fee; }
    public String getDx() { return dx; }
    public String getDx1() { return dx1; }
    public String getDx2() { return dx2; }
    public String getPaid() { return paid; }
    public String getRefund() { return refund; }
    public String getCredit() { return credit; }
    public String getDiscount() { return discount; }
    public String getStatus() { return status; }
    public String getTimestamp() { return timestamp; }
    public String getLocation() { return location; }
    public String getPatientName() { return patientName; }

    private static String normalizeMoney(String field, String value) {
        if (value == null) {
            return null;
        }
        if (value.trim().isEmpty()) {
            return "";
        }
        try {
            return BillingMoney.format(BillingMoney.parseNonNegativeAmount(value, field));
        } catch (BillingValidationException e) {
            throw new BillingValidationException(
                    "BillingClaimItemDto: malformed " + field + " amount [" + value + "]", e);
        }
    }
}
