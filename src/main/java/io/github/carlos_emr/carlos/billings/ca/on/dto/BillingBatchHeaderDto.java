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

/**
 * Legacy bean representation of an OHIP batch-header record.
 *
 * <p>This DTO keeps both camelCase and underscore-style accessors because the
 * migrated services, JSPs, and older helper code still read it through mixed
 * naming conventions. The class remains mutable to preserve that compatibility
 * while the surrounding workflow is being typed incrementally.</p>
 */
public class BillingBatchHeaderDto {
    String id;
    String diskId;
    String transcId;
    String recId;
    String specId;
    String mohOffice;
    String batchId;
    String operator;
    String groupNum;
    String providerRegNum;
    String specialty;
    String hCount;
    String rCount;
    String tCount;
    String batchDate;

    String createdatetime;
    String updatedatetime;
    String creator;
    String action;
    String comment;

    public String getBatchDate() {
        return batchDate;
    }

    public void setBatchDate(String batchDate) {
        this.batchDate = batchDate;
    }

    /** @deprecated use {@link #getBatchDate()} */
    @Deprecated
    public String getBatch_date() {
        return getBatchDate();
    }

    /** @deprecated use {@link #setBatchDate(String)} */
    @Deprecated
    public void setBatch_date(String batchDate) {
        setBatchDate(batchDate);
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    /** @deprecated use {@link #getBatchId()} */
    @Deprecated
    public String getBatch_id() {
        return getBatchId();
    }

    /** @deprecated use {@link #setBatchId(String)} */
    @Deprecated
    public void setBatch_id(String batchId) {
        setBatchId(batchId);
    }

    public String getDiskId() {
        return diskId;
    }

    public void setDiskId(String diskId) {
        this.diskId = diskId;
    }

    /** @deprecated use {@link #getDiskId()} */
    @Deprecated
    public String getDisk_id() {
        return getDiskId();
    }

    /** @deprecated use {@link #setDiskId(String)} */
    @Deprecated
    public void setDisk_id(String diskId) {
        setDiskId(diskId);
    }

    public String getGroupNum() {
        return groupNum;
    }

    public void setGroupNum(String groupNum) {
        this.groupNum = groupNum;
    }

    /** @deprecated use {@link #getGroupNum()} */
    @Deprecated
    public String getGroup_num() {
        return getGroupNum();
    }

    /** @deprecated use {@link #setGroupNum(String)} */
    @Deprecated
    public void setGroup_num(String groupNum) {
        setGroupNum(groupNum);
    }

    public String getHCount() {
        return hCount;
    }

    public void setHCount(String hCount) {
        this.hCount = hCount;
    }

    /** @deprecated use {@link #getHCount()} */
    @Deprecated
    public String getH_count() {
        return getHCount();
    }

    /** @deprecated use {@link #setHCount(String)} */
    @Deprecated
    public void setH_count(String hCount) {
        setHCount(hCount);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMohOffice() {
        return mohOffice;
    }

    public void setMohOffice(String mohOffice) {
        this.mohOffice = mohOffice;
    }

    /** @deprecated use {@link #getMohOffice()} */
    @Deprecated
    public String getMoh_office() {
        return getMohOffice();
    }

    /** @deprecated use {@link #setMohOffice(String)} */
    @Deprecated
    public void setMoh_office(String mohOffice) {
        setMohOffice(mohOffice);
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getProviderRegNum() {
        return providerRegNum;
    }

    public void setProviderRegNum(String providerRegNum) {
        this.providerRegNum = providerRegNum;
    }

    /** @deprecated use {@link #getProviderRegNum()} */
    @Deprecated
    public String getProvider_reg_num() {
        return getProviderRegNum();
    }

    /** @deprecated use {@link #setProviderRegNum(String)} */
    @Deprecated
    public void setProvider_reg_num(String providerRegNum) {
        setProviderRegNum(providerRegNum);
    }

    public String getRCount() {
        return rCount;
    }

    public void setRCount(String rCount) {
        this.rCount = rCount;
    }

    /** @deprecated use {@link #getRCount()} */
    @Deprecated
    public String getR_count() {
        return getRCount();
    }

    /** @deprecated use {@link #setRCount(String)} */
    @Deprecated
    public void setR_count(String rCount) {
        setRCount(rCount);
    }

    public String getRecId() {
        return recId;
    }

    public void setRecId(String recId) {
        this.recId = recId;
    }

    /** @deprecated use {@link #getRecId()} */
    @Deprecated
    public String getRec_id() {
        return getRecId();
    }

    /** @deprecated use {@link #setRecId(String)} */
    @Deprecated
    public void setRec_id(String recId) {
        setRecId(recId);
    }

    public String getSpecId() {
        return specId;
    }

    public void setSpecId(String specId) {
        this.specId = specId;
    }

    /** @deprecated use {@link #getSpecId()} */
    @Deprecated
    public String getSpec_id() {
        return getSpecId();
    }

    /** @deprecated use {@link #setSpecId(String)} */
    @Deprecated
    public void setSpec_id(String specId) {
        setSpecId(specId);
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getTCount() {
        return tCount;
    }

    public void setTCount(String tCount) {
        this.tCount = tCount;
    }

    /** @deprecated use {@link #getTCount()} */
    @Deprecated
    public String getT_count() {
        return getTCount();
    }

    /** @deprecated use {@link #setTCount(String)} */
    @Deprecated
    public void setT_count(String tCount) {
        setTCount(tCount);
    }

    public String getTranscId() {
        return transcId;
    }

    public void setTranscId(String transcId) {
        this.transcId = transcId;
    }

    /** @deprecated use {@link #getTranscId()} */
    @Deprecated
    public String getTransc_id() {
        return getTranscId();
    }

    /** @deprecated use {@link #setTranscId(String)} */
    @Deprecated
    public void setTransc_id(String transcId) {
        setTranscId(transcId);
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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

    public String getUpdatedatetime() {
        return updatedatetime;
    }

    public void setUpdatedatetime(String updatedatetime) {
        this.updatedatetime = updatedatetime;
    }

}
