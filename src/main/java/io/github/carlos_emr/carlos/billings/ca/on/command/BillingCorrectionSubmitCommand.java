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
package io.github.carlos_emr.carlos.billings.ca.on.command;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;

import java.time.LocalDate;
import java.util.List;

/**
 * Typed payload for committing a reviewed ON billing correction.
 */
public record BillingCorrectionSubmitCommand(String billingNo,
                                             String content,
                                             BillingMoney total,
                                             String hin,
                                             LocalDate dob,
                                             String visitType,
                                             LocalDate visitDate,
                                             String status,
                                             String clinicRefCode,
                                             String providerNo,
                                             LocalDate billingDate,
                                             List<BillingCorrectionSubmitItemCommand> items) {
    public BillingCorrectionSubmitCommand(String billingNo,
                                          String content,
                                          String total,
                                          String hin,
                                          String dob,
                                          String visitType,
                                          String visitDate,
                                          String status,
                                          String clinicRefCode,
                                          String providerNo,
                                          String billingDate,
                                          List<BillingCorrectionSubmitItemCommand> items) {
        this(billingNo,
                content,
                Commands.requiredStoredCents(total == null || total.isEmpty() ? "0" : total, "total"),
                hin,
                Commands.isoDate(dob, "dob"),
                visitType,
                Commands.isoDate(visitDate, "visitDate"),
                status,
                clinicRefCode,
                providerNo,
                Commands.isoDate(billingDate, "billingDate"),
                items);
    }

    public BillingCorrectionSubmitCommand {
        billingNo = Commands.nullToEmpty(billingNo);
        content = Commands.nullToEmpty(content);
        if (total == null) {
            throw new IllegalArgumentException("total is required");
        }
        hin = Commands.nullToEmpty(hin);
        visitType = Commands.nullToEmpty(visitType);
        status = Commands.nullToEmpty(status);
        clinicRefCode = Commands.nullToEmpty(clinicRefCode);
        providerNo = Commands.nullToEmpty(providerNo);
        items = items == null ? List.of() : List.copyOf(items);
    }

    public String totalStored() {
        return total.toStoredCents();
    }

    public String dobText() {
        return Commands.isoText(dob);
    }

    public String visitDateText() {
        return Commands.isoText(visitDate);
    }

    public String billingDateText() {
        return Commands.isoText(billingDate);
    }
}
