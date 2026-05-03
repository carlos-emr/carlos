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

import java.math.BigDecimal;

/**
 * One submitted service-code row from the ON billing correction validation form.
 */
public record BillingCorrectionLineCommand(String serviceCode,
                                           BigDecimal billingUnit,
                                           BillingMoney billingAmount) {
    public BillingCorrectionLineCommand(String serviceCode, String billingUnit, String billingAmount) {
        this(serviceCode,
                Commands.quantity(billingUnit, "billingUnit"),
                Commands.optionalMoney(billingAmount, "billingAmount"));
    }

    public BillingCorrectionLineCommand {
        serviceCode = Commands.nullToEmpty(serviceCode);
        billingUnit = billingUnit == null ? Commands.quantity(null, "billingUnit") : billingUnit;
    }

    public String billingUnitText() {
        return Commands.quantityText(billingUnit);
    }

    public String billingAmountText() {
        return billingAmount == null ? "" : billingAmount.format();
    }
}
