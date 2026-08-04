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

import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class FaxRecipient {

    private String name;
    private String fax;
    private Date sent;
    private STATUS status;

    public FaxRecipient() {
        //default
    }

    public FaxRecipient(ObjectNode json) {
        // path() (not get()) so an absent field yields null instead of an NPE — the manager's
        // fail-fast recipient parse decides whether missing fields are acceptable, not this
        // carrier, and that decision must land before any destructive file promotion.
        this.name = json.path("name").asText(null);
        this.setFax(json.path("fax").asText(null));
    }

    public FaxRecipient(String name, String fax) {
        this.name = name;
        this.setFax(fax);
    }

    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        // Normalize then assign consistently. Previously a blank/null input was a silent no-op (it kept
        // a stale previous value) while a non-blank input with no digits stored "" — two trap states on
        // a mutable, reusable bean that could send a fax to a stale or empty destination. Now: strip to
        // digits; an absent or digit-less input clears the field to null.
        String digits = (fax == null) ? null : fax.replaceAll("\\D", "");
        this.fax = (digits == null || digits.isEmpty()) ? null : digits;
    }

    /** True when this recipient carries a usable (non-empty, digits-only) fax destination. */
    public boolean hasUsableFax() {
        return fax != null && !fax.isEmpty();
    }

    public Date getSent() {
        return sent;
    }

    public void setSent(Date sent) {
        this.sent = sent;
    }

    public STATUS getStatus() {
        return status;
    }

    public void setStatus(STATUS status) {
        this.status = status;
    }

}
