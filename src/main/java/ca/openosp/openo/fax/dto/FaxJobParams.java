//CHECKSTYLE:OFF
/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2025.
 */

package ca.openosp.openo.fax.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Data Transfer Object for fax job parameters.
 * Encapsulates all parameters required to create and queue a fax job.
 * Uses builder pattern for flexible and readable object construction.
 *
 * @since 2025-01-19
 */
public class FaxJobParams {

    private String faxFilePath;
    private String recipient;
    private String recipientFaxNumber;
    private String senderFaxNumber;
    private Integer demographicNo;
    private String comments;
    private String coverpage;
    private String[] copyToRecipients;

    private FaxJobParams() {
        // Private constructor - use builder()
    }

    /**
     * Converts this DTO to a Map suitable for FaxManager methods.
     *
     * @return Map containing all fax job parameters
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("faxFilePath", faxFilePath);
        map.put("recipient", recipient);
        map.put("recipientFaxNumber", recipientFaxNumber);
        map.put("senderFaxNumber", senderFaxNumber);
        map.put("demographicNo", demographicNo);
        map.put("comments", comments);
        map.put("coverpage", coverpage);
        map.put("copyToRecipients", copyToRecipients);
        return map;
    }

    /**
     * Creates a new builder for constructing FaxJobParams instances.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for FaxJobParams using fluent interface pattern.
     */
    public static class Builder {
        private final FaxJobParams params = new FaxJobParams();

        /**
         * Sets the fax file path (PDF document to be faxed).
         *
         * @param faxFilePath path to the fax PDF file
         * @return this builder for method chaining
         */
        public Builder faxFilePath(String faxFilePath) {
            params.faxFilePath = faxFilePath;
            return this;
        }

        /**
         * Sets the recipient name.
         *
         * @param recipient name of the fax recipient
         * @return this builder for method chaining
         */
        public Builder recipient(String recipient) {
            params.recipient = recipient;
            return this;
        }

        /**
         * Sets the recipient fax number.
         *
         * @param recipientFaxNumber fax number to send to
         * @return this builder for method chaining
         */
        public Builder recipientFaxNumber(String recipientFaxNumber) {
            params.recipientFaxNumber = recipientFaxNumber;
            return this;
        }

        /**
         * Sets the sender fax number.
         *
         * @param senderFaxNumber sender's fax number
         * @return this builder for method chaining
         */
        public Builder senderFaxNumber(String senderFaxNumber) {
            params.senderFaxNumber = senderFaxNumber;
            return this;
        }

        /**
         * Sets the demographic number (patient ID).
         *
         * @param demographicNo patient demographic number
         * @return this builder for method chaining
         */
        public Builder demographicNo(Integer demographicNo) {
            params.demographicNo = demographicNo;
            return this;
        }

        /**
         * Sets the comments for the fax cover page.
         *
         * @param comments fax cover page comments
         * @return this builder for method chaining
         */
        public Builder comments(String comments) {
            params.comments = comments;
            return this;
        }

        /**
         * Sets the cover page template.
         *
         * @param coverpage cover page template identifier
         * @return this builder for method chaining
         */
        public Builder coverpage(String coverpage) {
            params.coverpage = coverpage;
            return this;
        }

        /**
         * Sets the array of copy-to recipients.
         *
         * @param copyToRecipients array of additional recipients
         * @return this builder for method chaining
         */
        public Builder copyToRecipients(String[] copyToRecipients) {
            params.copyToRecipients = copyToRecipients;
            return this;
        }

        /**
         * Builds and returns the FaxJobParams instance.
         *
         * @return constructed FaxJobParams object
         */
        public FaxJobParams build() {
            return params;
        }
    }

    // Getters for testing/debugging purposes

    public String getFaxFilePath() {
        return faxFilePath;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getRecipientFaxNumber() {
        return recipientFaxNumber;
    }

    public String getSenderFaxNumber() {
        return senderFaxNumber;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public String getComments() {
        return comments;
    }

    public String getCoverpage() {
        return coverpage;
    }

    public String[] getCopyToRecipients() {
        return copyToRecipients;
    }
}
