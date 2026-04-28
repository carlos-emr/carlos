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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.ArrayList;
/**
 * Data carrier for {@code BillingDiskNameData}.
 *
 * <p>These classes carry legacy billing state between services, actions, and
 * JSPs. Prefer explicit fields and accessors here over loosely typed request
 * attributes in the view layer.</p>
 */
public class BillingDiskNameData {
    String id;
    String monthCode;
    String batchcount;
    String ohipfilename;
    String groupno;
    String creator;
    String claimrecord;
    String createdatetime;
    String status;
    String total;
    String updatedatetime;
    ArrayList vecFilenameId;
    ArrayList htmlfilename;
    ArrayList providerohipno;
    ArrayList providerno;
    ArrayList vecClaimrecord;
    ArrayList vecStatus;
    ArrayList vecTotal;

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

    public ArrayList getHtmlfilename() {
        return htmlfilename;
    }

    public void setHtmlfilename(ArrayList htmlfilename) {
        this.htmlfilename = htmlfilename;
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

    public ArrayList getProviderohipno() {
        return providerohipno;
    }

    public void setProviderohipno(ArrayList providerohipno) {
        this.providerohipno = providerohipno;
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

    public ArrayList getVecClaimrecord() {
        return vecClaimrecord;
    }

    public void setVecClaimrecord(ArrayList vecClaimrecord) {
        this.vecClaimrecord = vecClaimrecord;
    }

    public ArrayList getVecStatus() {
        return vecStatus;
    }

    public void setVecStatus(ArrayList vecStatus) {
        this.vecStatus = vecStatus;
    }

    public ArrayList getVecTotal() {
        return vecTotal;
    }

    public void setVecTotal(ArrayList vecTotal) {
        this.vecTotal = vecTotal;
    }

    public ArrayList getProviderno() {
        return providerno;
    }

    public void setProviderno(ArrayList providerno) {
        this.providerno = providerno;
    }

    public String getUpdatedatetime() {
        return updatedatetime;
    }

    public void setUpdatedatetime(String updatedatetime) {
        this.updatedatetime = updatedatetime;
    }

    public ArrayList getVecFilenameId() {
        return vecFilenameId;
    }

    public void setVecFilenameId(ArrayList vecFilenameId) {
        this.vecFilenameId = vecFilenameId;
    }

}
