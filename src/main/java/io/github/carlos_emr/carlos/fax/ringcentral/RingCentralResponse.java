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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight DTOs for RingCentral fax API responses used by the provider client.
 *
 * <p>All variants are records with canonical-constructor defensive copies. Lists are normalized
 * to {@link List#copyOf} so callers cannot mutate stored state through a returned reference.
 * Jackson 2.12+ binds records via canonical constructor with component-level
 * {@link JsonProperty} — no {@code @JsonCreator} indirection is needed.</p>
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
     * Message metadata returned by send/status/list endpoints. Fully immutable: the canonical
     * constructor copies the attachment list via {@link List#copyOf} so subsequent mutation of
     * the source list cannot reach this record. Accessor methods preserve the legacy
     * {@code getXxx} naming so existing call sites continue to compile.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("id") String id,
            @JsonProperty("messageStatus") String messageStatus,
            @JsonProperty("faxStatus") String faxStatus,
            @JsonProperty("direction") String direction,
            @JsonProperty("readStatus") String readStatus,
            @JsonProperty("creationTime") String creationTime,
            @JsonProperty("from") Party from,
            @JsonProperty("attachments") List<Attachment> attachments) {

        public Message {
            // Snapshot then wrap in an unmodifiable view rather than List.copyOf so a
            // RingCentral response that sends null attachment elements (rare schema drift) does
            // not throw at construction — the listInboundFaxes loop handles per-element null
            // skipping with a clearer log line.
            attachments = attachments == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(attachments));
        }

        public String getId() { return id; }
        public String getMessageStatus() { return messageStatus; }
        public String getFaxStatus() { return faxStatus; }
        public String getDirection() { return direction; }
        public String getReadStatus() { return readStatus; }
        public String getCreationTime() { return creationTime; }
        public Party getFrom() { return from; }
        public List<Attachment> getAttachments() { return attachments; }
    }

    /**
     * Inbox-list response. Tracks the optional {@code navigation.nextPage} cursor so the connector
     * can walk multi-page inboxes without losing records past the per-page limit. Records are
     * defensively copied at construction time.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageList(
            @JsonProperty("records") List<Message> records,
            @JsonProperty("navigation") Navigation navigation) {

        public MessageList {
            // See Message canonical constructor — null elements are tolerated here so the
            // skip-null-records test path in listInboundFaxes remains exercisable.
            records = records == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(records));
        }

        public List<Message> getRecords() { return records; }
        public Navigation getNavigation() { return navigation; }
    }
}
