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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

/**
 * One row from
 * {@code BillingServiceDao.findBillingServiceAndCtlBillingServiceByMagic} —
 * a {@code BillingService} joined with its matching {@code CtlBillingService}
 * row, projected to just the fields the billing-form service-grid composer
 * actually reads.
 *
 * @param serviceCode      OHIP service code (from {@code BillingService})
 * @param description      human-readable description ({@code BillingService})
 * @param value            fee-schedule value as a decimal string
 * @param percentage       fee-schedule percentage as a decimal string
 * @param displayStyle     id of the CSS-style row to apply (may be null when no styling)
 * @param sliFlag          flag indicating whether the code participates in SLI
 * @param serviceType      service-type code (from {@code CtlBillingService})
 * @param serviceGroupName service-group display name ({@code CtlBillingService})
 * @since 2026-05-01
 */
public record ServiceCodeMagicRow(
        String serviceCode,
        String description,
        String value,
        String percentage,
        Integer displayStyle,
        Boolean sliFlag,
        String serviceType,
        String serviceGroupName) {
    public ServiceCodeMagicRow {
        serviceCode = serviceCode == null ? "" : serviceCode;
        description = description == null ? "" : description;
        value = value == null ? "" : value;
        percentage = percentage == null ? "" : percentage;
        serviceType = serviceType == null ? "" : serviceType;
        serviceGroupName = serviceGroupName == null ? "" : serviceGroupName;
    }
}
