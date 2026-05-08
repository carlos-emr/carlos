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
 * <p>Records bind via Jackson's canonical-constructor support (component-level
 * {@link JsonProperty} annotations are sufficient — no {@code @JsonCreator} indirection). Mutable
 * holders {@code Message} and {@code MessageList} guard their list state via copy-on-write in the
 * setter; the getters return unmodifiable views of that already-copied state without re-copying.</p>
 *
 * @since 2026-05-05
 */
public final class RingCentralResponse {

    private RingCentralResponse() {
    }

    public record Token(@JsonProperty("access_token") String accessToken,
                        @JsonProperty("expires_in") long expiresIn) {
    }

    public record Party(@JsonProperty("phoneNumber") String phoneNumber) {
    }

    public record Attachment(@JsonProperty("id") String id,
                             @JsonProperty("fileName") String fileName,
                             @JsonProperty("contentType") String contentType) {
    }

    public record NextPage(@JsonProperty("uri") String uri) {
    }

    public record Navigation(@JsonProperty("nextPage") NextPage nextPage) {
    }

    /**
     * Message metadata returned by send/status/list endpoints. Mutable bean (Jackson populates
     * via setters); the attachment list is copied on write and read access returns an
     * unmodifiable view of the stored copy. Note that {@code Message} itself is mutable, so
     * a caller holding a {@code Message} reference obtained from {@link MessageList#getRecords()}
     * can still mutate that instance's scalar fields — the unmodifiability guarantee is on the
     * collection shape, not on the individual records. Inbox-flow callers do not retain
     * references across calls, so this is safe in practice.
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
            return from;
        }

        public void setFrom(Party from) {
            this.from = from;
        }

        public List<Attachment> getAttachments() {
            if (attachments == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(attachments);
        }

        public void setAttachments(List<Attachment> attachments) {
            this.attachments = attachments == null ? null : new ArrayList<>(attachments);
        }
    }

    /**
     * Inbox-list response. Tracks the optional {@code navigation.nextPage} cursor so the connector
     * can walk multi-page inboxes without losing records past the per-page cap.
     */
    public static class MessageList {
        private List<Message> records;
        private Navigation navigation;

        public MessageList() {
        }

        public List<Message> getRecords() {
            if (records == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(records);
        }

        public void setRecords(List<Message> records) {
            this.records = records == null ? null : new ArrayList<>(records);
        }

        public Navigation getNavigation() {
            return navigation;
        }

        public void setNavigation(Navigation navigation) {
            this.navigation = navigation;
        }
    }
}
