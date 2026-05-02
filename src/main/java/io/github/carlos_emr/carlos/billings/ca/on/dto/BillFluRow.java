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

import java.util.Date;

/**
 * One row from the flu (G590A / G591A) billing report query.
 *
 * <p>{@code content} is the first projected field because the report reads
 * {@code <specialty>} XML out of it to bucket rows into walk-in vs clinic
 * totals.</p>
 *
 * @since 2026-05-01
 */
public record BillFluRow(
        String content,
        Integer id,
        String total,
        String status,
        Date billingDate,
        String demographicName) {
    public BillFluRow {
        content = content == null ? "" : content;
        total = total == null ? "" : total;
        status = status == null ? "" : status;
        demographicName = demographicName == null ? "" : demographicName;
    }
}
