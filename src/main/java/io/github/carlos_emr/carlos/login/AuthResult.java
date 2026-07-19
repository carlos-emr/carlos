/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.login;

/**
 * This class represents the result of an authentication attempt.
 * It contains information about the authenticated user or an error message.
 */
public class AuthResult {

    private String errMsg;
    private String providerNo;
    private String firstname;
    private String lastname;
    private String profession;
    private String roleName;
    private String expiredDays;
    private String email;

    public AuthResult(String[] strAuth) {
        if (strAuth[0].equals("expired")) {
            this.errMsg = "expired";
            return;
        }

        this.providerNo = strAuth[0];
        this.firstname = strAuth[1];
        this.lastname = strAuth[2];
        this.profession = strAuth[3];
        this.roleName = strAuth[4];
        this.expiredDays = strAuth[5];
        this.email = strAuth[6];
    }

    public AuthResult(String providerNo, String firstname, String lastname, String profession, String roleName, String expiredDays, String email) {
        this.providerNo = providerNo;
        this.firstname = firstname;
        this.lastname = lastname;
        this.profession = profession;
        this.roleName = roleName;
        this.expiredDays = expiredDays;
        this.email = email;
    }

    public AuthResult(String errMsg) {
        this.errMsg = errMsg;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getProfession() {
        return profession;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getExpiredDays() {
        return expiredDays;
    }

    public String getEmail() {
        return email;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public boolean hasNoError() {
        return this.errMsg == null;
    }

    public boolean hasError() {
        return !this.hasNoError();
    }

    /**
     * Checks if the authentication attempt resulted in an account expiration error.
     * @return True if the error message indicates account expiration, false otherwise.
     */
    public boolean isAccountExpired() {
        return this.hasError() && this.getErrMsg().equals("expired");
    }

}
