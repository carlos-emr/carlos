/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.fax.ringcentral;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight DTOs for RingCentral fax API responses used by the provider client.
 *
 * @since 2026-05-05
 */
public final class RingCentralResponse {

    private RingCentralResponse() {
        // DTO namespace holder.
    }

    /**
     * OAuth token response.
     */
    public static class Token {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("expires_in")
        private long expiresIn;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
        }
    }

    /**
     * Message metadata returned by send/status/list endpoints.
     */
    public static class Message {
        private String id;
        private String messageStatus;
        private String faxStatus;
        private String direction;
        private String readStatus;
        private String creationTime;
        private Party from;
        private List<Attachment> attachments;

        public Message() {
            // DTO constructor for Jackson.
        }

        private Message(Message other) {
            this.id = other.id;
            this.messageStatus = other.messageStatus;
            this.faxStatus = other.faxStatus;
            this.direction = other.direction;
            this.readStatus = other.readStatus;
            this.creationTime = other.creationTime;
            this.from = other.from == null ? null : new Party(other.from);
            this.attachments = copyAttachments(other.attachments);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMessageStatus() {
            return messageStatus;
        }

        public void setMessageStatus(String messageStatus) {
            this.messageStatus = messageStatus;
        }

        public String getFaxStatus() {
            return faxStatus;
        }

        public void setFaxStatus(String faxStatus) {
            this.faxStatus = faxStatus;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getReadStatus() {
            return readStatus;
        }

        public void setReadStatus(String readStatus) {
            this.readStatus = readStatus;
        }

        public String getCreationTime() {
            return creationTime;
        }

        public void setCreationTime(String creationTime) {
            this.creationTime = creationTime;
        }

        public Party getFrom() {
            return from == null ? null : new Party(from);
        }

        public void setFrom(Party from) {
            this.from = from == null ? null : new Party(from);
        }

        public List<Attachment> getAttachments() {
            if (attachments == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(copyAttachments(attachments));
        }

        public void setAttachments(List<Attachment> attachments) {
            this.attachments = copyAttachments(attachments);
        }

        private static List<Attachment> copyAttachments(List<Attachment> attachments) {
            if (attachments == null) {
                return null;
            }
            List<Attachment> copy = new ArrayList<>(attachments.size());
            for (Attachment attachment : attachments) {
                copy.add(attachment == null ? null : new Attachment(attachment));
            }
            return copy;
        }
    }

    /**
     * Message list response.
     */
    public static class MessageList {
        private List<Message> records;

        public MessageList() {
            // DTO constructor for Jackson.
        }

        public List<Message> getRecords() {
            if (records == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(copyMessages(records));
        }

        public void setRecords(List<Message> records) {
            this.records = copyMessages(records);
        }

        private static List<Message> copyMessages(List<Message> records) {
            if (records == null) {
                return null;
            }
            List<Message> copy = new ArrayList<>(records.size());
            for (Message message : records) {
                copy.add(message == null ? null : new Message(message));
            }
            return copy;
        }
    }

    /**
     * RingCentral phone-number holder.
     */
    public static class Party {
        private String phoneNumber;

        public Party() {
            // DTO constructor for Jackson.
        }

        private Party(Party other) {
            this.phoneNumber = other.phoneNumber;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
    }

    /**
     * Fax attachment metadata.
     */
    public static class Attachment {
        private String id;
        private String fileName;
        private String contentType;

        public Attachment() {
            // DTO constructor for Jackson.
        }

        private Attachment(Attachment other) {
            this.id = other.id;
            this.fileName = other.fileName;
            this.contentType = other.contentType;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
    }
}
