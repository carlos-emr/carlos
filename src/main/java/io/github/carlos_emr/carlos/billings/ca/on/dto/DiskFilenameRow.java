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
 * One filename row inside a {@link BillingDiskNameDto}: the
 * {@code BillingONFilename} row's id, its rendered HTML filename, the
 * provider OHIP / billing numbers, and the per-row claim-record / status /
 * total carried alongside.
 *
 * <p>{@code claimRecord}, {@code status}, {@code total} carry per-row data
 * because {@code OhipClaimFileService} updates each filename row with
 * record-count and total values during OHIP file generation, and the MRI
 * grid renders them per row. Creator paths duplicate the disk-header
 * scalar across each row so any consumer reading {@code .get(0)} sees the
 * pre-fold value.</p>
 *
 * @since 2026-05-01
 */
public record DiskFilenameRow(
        String filenameId,
        String htmlFilename,
        String providerOhipNo,
        String providerNo,
        String claimRecord,
        String status,
        String total) {
    public DiskFilenameRow {
        filenameId = filenameId == null ? "" : filenameId;
        htmlFilename = htmlFilename == null ? "" : htmlFilename;
        providerOhipNo = providerOhipNo == null ? "" : providerOhipNo;
        providerNo = providerNo == null ? "" : providerNo;
        claimRecord = claimRecord == null ? "" : claimRecord;
        status = status == null ? "" : status;
        total = total == null ? "" : total;
    }
}
