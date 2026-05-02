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

import java.util.List;

/**
 * Data carrier for an OHIP "diskname" (claim batch) plus its filename rows.
 *
 * <p>The disk-level scalars ({@code id}, {@code monthCode}, {@code batchcount},
 * {@code ohipfilename}, {@code groupno}, {@code creator}, {@code claimrecord},
 * {@code status}, {@code total}, plus the create / update timestamps) describe
 * the disk header. The {@link #getFilenames() filenames} list carries one
 * {@link DiskFilenameRow} per provider/filename associated with the disk —
 * each row holds its own per-row claim record / status / total which
 * {@code OhipClaimFileService} updates during OHIP file generation.</p>
 */
public class BillingDiskNameDto {
    private String id;
    private String monthCode;
    private String batchcount;
    private String ohipfilename;
    private String groupno;
    private String creator;
    private String claimrecord;
    private String createdatetime;
    private String status;
    private String total;
    private String updatedatetime;
    private List<DiskFilenameRow> filenames;

    public String getBatchcount() {
        return batchcount;
    }

    public void setBatchcount(String batchcount) {
        this.batchcount = batchcount;
    }

    public String getClaimrecord() {
        return claimrecord;
    }

    public void setClaimrecord(String claimrecord) {
        this.claimrecord = claimrecord;
    }

    public String getCreatedatetime() {
        return createdatetime;
    }

    public void setCreatedatetime(String createdatetime) {
        this.createdatetime = createdatetime;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getGroupno() {
        return groupno;
    }

    public void setGroupno(String groupno) {
        this.groupno = groupno;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMonthCode() {
        return monthCode;
    }

    public void setMonthCode(String monthCode) {
        this.monthCode = monthCode;
    }

    public String getOhipfilename() {
        return ohipfilename;
    }

    public void setOhipfilename(String ohipfilename) {
        this.ohipfilename = ohipfilename;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTotal() {
        return total;
    }

    public void setTotal(String total) {
        this.total = total;
    }

    public String getUpdatedatetime() {
        return updatedatetime;
    }

    public void setUpdatedatetime(String updatedatetime) {
        this.updatedatetime = updatedatetime;
    }

    public List<DiskFilenameRow> getFilenames() {
        return filenames == null ? List.of() : filenames;
    }

    public void setFilenames(List<DiskFilenameRow> filenames) {
        this.filenames = filenames == null ? List.of() : List.copyOf(filenames);
    }
}
