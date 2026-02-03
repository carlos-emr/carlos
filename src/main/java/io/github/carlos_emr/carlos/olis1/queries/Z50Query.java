/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package io.github.carlos_emr.carlos.olis1.queries;

import io.github.carlos_emr.carlos.olis1.parameters.PID51;
import io.github.carlos_emr.carlos.olis1.parameters.PID52;
import io.github.carlos_emr.carlos.olis1.parameters.PID7;
import io.github.carlos_emr.carlos.olis1.parameters.PID8;
import io.github.carlos_emr.carlos.olis1.parameters.ZPD1;

/**
 * Z50 - Identify Patient by Name, Sex, and Date of Birth
 *
 * @author jen
 */
public class Z50Query extends Query {

    private PID52 firstName = new PID52(); // mandatory
    private PID51 lastName = new PID51(); // mandatory
    private PID8 sex = new PID8(); // mandatory
    private PID7 dateOfBirth = new PID7(); // mandatory

    @Override
    public String getQueryHL7String() {
        String query = "";

        if (firstName != null)
            query += firstName.toOlisString() + "~";

        if (lastName != null)
            query += lastName.toOlisString() + "~";

        if (sex != null)
            query += sex.toOlisString() + "~";

        if (dateOfBirth != null)
            query += dateOfBirth.toOlisString() + "~";

        if (query.endsWith("~")) {
            query = query.substring(0, query.length() - 1);
        }

        return query;
    }

    public void setFirstName(PID52 firstName) {
        this.firstName = firstName;
    }

    public void setLastName(PID51 lastName) {
        this.lastName = lastName;
    }

    public void setSex(PID8 sex) {
        this.sex = sex;
    }

    public void setDateOfBirth(PID7 dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Z50;
    }

    @Override
    public void setConsentToViewBlockedInformation(ZPD1 consentToViewBlockedInformation) {
        throw new RuntimeException("Not valid for this type of query.");
    }

}
