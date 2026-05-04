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
 * One distinct {@code CtlBillingService} {@code (serviceType, serviceTypeName)}
 * pair, returned by
 * {@code CtlBillingServiceDao.getUniqueServiceTypes(...)}.
 *
 * @since 2026-05-01
 */
public record UniqueServiceTypeRow(String serviceType, String serviceTypeName) {
    public UniqueServiceTypeRow {
        serviceType = serviceType == null ? "" : serviceType;
        serviceTypeName = serviceTypeName == null ? "" : serviceTypeName;
    }
}
