/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 */
package io.github.carlos_emr.carlos.email.core;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveAttachmentDto;
import io.github.carlos_emr.carlos.utility.EmailSendingException;

/**
 * One concrete way of putting an outbound patient email on the wire, together with the exact
 * artifact that transport produces.
 *
 * <p><strong>Why archiving is expressed as a capability of the transport rather than a
 * predicate.</strong> Outbound patient email is a legal record of what was sent, so a send that
 * leaves no archive is a retention gap. An earlier design asked a separate
 * {@code supportsOutboundArchive()} question before archiving, which restated — in a second,
 * independently maintained place — a decision {@link EmailSender}'s transport switch already
 * makes. Two statements of the same fact can disagree, and when they disagree the failure is
 * silent: the send succeeds and simply is not recorded. Making the artifact part of the transport
 * contract removes the second statement. A transport that cannot describe its own artifact cannot
 * satisfy this interface, and a transport that does not satisfy this interface cannot be returned
 * by the switch, so it cannot send at all.</p>
 *
 * <p>Implementations are single-use and stateful: {@link #prepareArtifactBytes()} captures the
 * finalized payload, {@link #sendPrepared()} transmits that same captured payload rather than
 * rebuilding it, and {@link #discardPrepared()} releases it. Preparing twice is an error. The
 * archive-before-transport ordering that {@code EmailManager} relies on is only meaningful because
 * the bytes archived and the bytes sent are the same bytes.</p>
 *
 * @since 2026-08-19
 */
public interface OutboundEmailTransport {

    /**
     * Sends immediately, without capturing an archive artifact.
     *
     * <p>Reserved for callers that are not archiving. The production send path prepares and
     * archives first; see {@code EmailManager.sendEmail}.</p>
     *
     * @throws EmailSendingException if transport delivery fails
     */
    void send() throws EmailSendingException;

    /**
     * Builds the finalized payload this transport will transmit and returns it verbatim.
     *
     * <p>The returned bytes are what {@link #sendPrepared()} puts on the wire, not a
     * reconstruction of it. This is the property that makes the archive an accurate record.</p>
     *
     * @return the exact payload bytes for this transport
     * @throws EmailSendingException if the payload cannot be built, or if one is already prepared
     */
    byte[] prepareArtifactBytes() throws EmailSendingException;

    /**
     * Describes the attachments carried by the prepared payload.
     *
     * <p>Each transport derives this from its own prepared representation, because the encoded
     * form differs — MIME parts for SMTP, base64 payload entries for a JSON API. Callable only
     * after {@link #prepareArtifactBytes()}.</p>
     *
     * @return attachment metadata for the archive, empty when the message carries no attachments
     * @throws EmailSendingException if no payload has been prepared or metadata cannot be derived
     */
    List<OutboundEmailArchiveAttachmentDto> describePreparedAttachments() throws EmailSendingException;

    /**
     * Returns the {@code OutboundEmailArchive.ARTIFACT_TYPE_*} constant naming this artifact's shape.
     *
     * @return the artifact type discriminator stored on the archive row
     */
    String getArchiveArtifactType();

    /**
     * Returns the MIME content type under which the artifact is stored in the document store.
     *
     * @return the artifact's content type
     */
    String getArchiveContentType();

    /**
     * Builds the archived artifact's file name.
     *
     * @param emailLog persisted log row that owns the archive, used to make the name identifiable
     * @return a file name for the stored artifact
     */
    String getArchiveFileName(EmailLog emailLog);

    /**
     * Transmits the payload captured by {@link #prepareArtifactBytes()}.
     *
     * @throws EmailSendingException if nothing has been prepared, or if transport delivery fails
     */
    void sendPrepared() throws EmailSendingException;

    /**
     * Releases any prepared payload. Idempotent, and safe to call when nothing was prepared.
     *
     * <p>Called from cleanup paths, including after a failure, so it must not throw.</p>
     */
    void discardPrepared();
}
