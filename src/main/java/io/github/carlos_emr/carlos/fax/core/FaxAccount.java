/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 */

package io.github.carlos_emr.carlos.fax.core;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;

public class FaxAccount {

    private String facilityName;
    private String letterheadName;
    private String faxNumberOwner;
    private String name;
    private String subText;
    private String fax;
    private String phone;
    private String address;

    public FaxAccount() {
        // default constructor
    }

    public FaxAccount(FaxConfig faxConfig) {
        fax = faxConfig.getFaxNumber();
        faxNumberOwner = faxConfig.getAccountName();

    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getLetterheadName() {
        if (letterheadName == null) {
            return facilityName;
        }
        return letterheadName;
    }

    public void setLetterheadName(String letterheadName) {
        this.letterheadName = letterheadName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFaxNumberOwner() {
        if (faxNumberOwner == null) {
            return facilityName;
        }
        return faxNumberOwner;
    }

    public void setFaxNumberOwner(String faxNumberOwner) {
        this.faxNumberOwner = faxNumberOwner;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getSubText() {
        if (subText == null) {
            return facilityName;
        }
        return subText;
    }

    public void setSubText(String subText) {
        this.subText = subText;
    }
}
