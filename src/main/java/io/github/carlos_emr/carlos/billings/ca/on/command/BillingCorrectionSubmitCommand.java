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

import java.util.List;

/**
 * Typed payload for committing a reviewed ON billing correction.
 */
public record BillingCorrectionSubmitCommand(String billingNo,
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
    public BillingCorrectionSubmitCommand {
        billingNo = nullToEmpty(billingNo);
        content = nullToEmpty(content);
        total = nullToEmpty(total);
        hin = nullToEmpty(hin);
        dob = nullToEmpty(dob);
        visitType = nullToEmpty(visitType);
        visitDate = nullToEmpty(visitDate);
        status = nullToEmpty(status);
        clinicRefCode = nullToEmpty(clinicRefCode);
        providerNo = nullToEmpty(providerNo);
        billingDate = nullToEmpty(billingDate);
        items = items == null ? List.of() : List.copyOf(items);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
