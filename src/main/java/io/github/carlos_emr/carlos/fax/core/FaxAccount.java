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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.fax.core;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;

/**
 * Value object representing a fax account's identity and contact information.
 *
 * <p>Used primarily for cover page generation and sender display in the fax workflow.
 * Holds facility name, letterhead name, fax number, phone, address, and supplementary
 * text. Can be constructed from a {@link FaxConfig} to inherit the configured fax number
 * and account name.</p>
 *
 * @see io.github.carlos_emr.carlos.fax.util.PdfCoverPageCreator
 * @see FaxConfig
 * @since 2026-03-17
 */
public class FaxAccount {

    private String facilityName;
    private String letterheadName;
    private String faxNumberOwner;
    private String name;
    private String subText;
    private String fax;
    private String phone;
    private String address;

    /**
     * Default no-argument constructor.
     */
    public FaxAccount() {
        // default constructor
    }

    /**
     * Constructs a FaxAccount from a fax configuration, inheriting the fax number and account name.
     *
     * @param faxConfig FaxConfig the fax configuration to extract account details from
     */
    public FaxAccount(FaxConfig faxConfig) {
        fax = faxConfig.getFaxNumber();
        faxNumberOwner = faxConfig.getAccountName();

    }

    /**
     * Returns the facility name.
     *
     * @return String the facility name
     */
    public String getFacilityName() {
        return facilityName;
    }

    /**
     * Sets the facility name.
     *
     * @param facilityName String the facility name
     */
    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    /**
     * Returns the letterhead name, falling back to the facility name if not explicitly set.
     *
     * @return String the letterhead name, or the facility name if letterhead is null
     */
    public String getLetterheadName() {
        if (letterheadName == null) {
            return facilityName;
        }
        return letterheadName;
    }

    /**
     * Sets the letterhead name displayed on fax cover pages.
     *
     * @param letterheadName String the letterhead name
     */
    public void setLetterheadName(String letterheadName) {
        this.letterheadName = letterheadName;
    }

    /**
     * Returns the account holder name.
     *
     * @return String the account name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the account holder name.
     *
     * @param name String the account name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the fax number owner label, falling back to the facility name if not set.
     *
     * @return String the fax number owner name, or the facility name if owner is null
     */
    public String getFaxNumberOwner() {
        if (faxNumberOwner == null) {
            return facilityName;
        }
        return faxNumberOwner;
    }

    /**
     * Sets the fax number owner label.
     *
     * @param faxNumberOwner String the fax number owner name
     */
    public void setFaxNumberOwner(String faxNumberOwner) {
        this.faxNumberOwner = faxNumberOwner;
    }

    /**
     * Returns the fax number.
     *
     * @return String the fax number
     */
    public String getFax() {
        return fax;
    }

    /**
     * Sets the fax number.
     *
     * @param fax String the fax number
     */
    public void setFax(String fax) {
        this.fax = fax;
    }

    /**
     * Returns the phone number.
     *
     * @return String the phone number
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the phone number.
     *
     * @param phone String the phone number
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Returns the address.
     *
     * @return String the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the address.
     *
     * @param address String the address
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Returns the sub-text label, falling back to the facility name if not explicitly set.
     *
     * @return String the sub-text, or the facility name if sub-text is null
     */
    public String getSubText() {
        if (subText == null) {
            return facilityName;
        }
        return subText;
    }

    /**
     * Sets the sub-text displayed below the title on the cover page.
     *
     * @param subText String the sub-text label
     */
    public void setSubText(String subText) {
        this.subText = subText;
    }
}
