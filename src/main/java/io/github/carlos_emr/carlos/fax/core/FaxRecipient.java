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

/**
 * Value object representing a fax recipient with name, fax number, send timestamp, and delivery status.
 *
 * <p>Used in the fax workflow to track individual recipients for outbound faxes, including
 * copy-to recipients. The fax number is automatically sanitized on set to contain only digits.
 * Can be constructed from a JSON object node (for parsing copy-to recipient arrays) or from
 * explicit name and fax number parameters.</p>
 *
 * @see io.github.carlos_emr.carlos.fax.util.PdfCoverPageCreator
 * @see io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS
 * @since 2026-03-17
 */
public class FaxRecipient {

    private String name;
    private String fax;
    private Date sent;
    private STATUS status;

    /**
     * Default no-argument constructor.
     */
    public FaxRecipient() {
        //default
    }

    /**
     * Constructs a FaxRecipient from a JSON object containing {@code name} and {@code fax} fields.
     *
     * @param json ObjectNode the JSON object with recipient data
     */
    public FaxRecipient(ObjectNode json) {
        this.name = json.get("name").asText();
        this.setFax(json.get("fax").asText());
    }

    /**
     * Constructs a FaxRecipient with a name and fax number.
     *
     * @param name String the recipient name
     * @param fax String the fax number (non-digit characters are stripped automatically)
     */
    public FaxRecipient(String name, String fax) {
        this.name = name;
        this.setFax(fax);
    }

    /**
     * Returns the recipient name.
     *
     * @return String the recipient name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the recipient name.
     *
     * @param name String the recipient name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the sanitized fax number (digits only).
     *
     * @return String the fax number containing only digits
     */
    public String getFax() {
        return fax;
    }

    /**
     * Sets the fax number, stripping all non-digit characters for normalization.
     *
     * @param fax String the fax number (may contain formatting characters like dashes or spaces)
     */
    public void setFax(String fax) {
        if (fax != null && !fax.trim().isEmpty()) {
            // Strip all non-digit characters for consistent storage
            this.fax = fax.replaceAll("\\D", "").trim();
        }
    }

    /**
     * Returns the timestamp when the fax was sent.
     *
     * @return Date the send timestamp, or null if not yet sent
     */
    public Date getSent() {
        return sent;
    }

    /**
     * Sets the timestamp when the fax was sent.
     *
     * @param sent Date the send timestamp
     */
    public void setSent(Date sent) {
        this.sent = sent;
    }

    /**
     * Returns the delivery status of this recipient's fax.
     *
     * @return STATUS the fax delivery status
     */
    public STATUS getStatus() {
        return status;
    }

    /**
     * Sets the delivery status of this recipient's fax.
     *
     * @param status STATUS the fax delivery status
     */
    public void setStatus(STATUS status) {
        this.status = status;
    }

}
