/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

/**
 * Validation utility ensuring patient gender information conforms to BC MSP rules. Prevents claim
 * rejections by verifying that service codes restricted to specific genders (e.g., maternity care)
 * are appropriately matched with the patient's demographic data.
 *
 * @since 2026-06-20
 */
public class SexValidator
        extends ServiceCodeValidator {
    private String gender = "DEFAULT"; //Not all service codes will have a rule
    private String inputSex = ""; //sex of the patient that is being validated

    /**
     * Creates a new SexValidator
     */
    public SexValidator() {
    }

    /**
     * Creates a new SexValidator initializing the service code and input gender
     *
     * @param svcCode  String
     * @param inputSex int
     */
    public SexValidator(String svcCode, String inputSex) {
        this.serviceCode = svcCode;
        this.inputSex = inputSex;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setInputSex(String inputSex) {
        this.inputSex = inputSex;
    }

    public String getGender() {
        return gender;
    }

    public String getInputSex() {
        return inputSex;
    }

    public boolean isValid() {
        return gender.equals("DEFAULT") ? true : (this.inputSex.equals(this.gender));
    }
}
