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

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import org.apache.commons.lang3.StringUtils;

/**
 * Encapsulates the RingCentral inbound-fax download contract carried over {@link FaxJob#getFile_name()}.
 *
 * <p>The provider-agnostic {@code FaxJob} pipeline uses {@code file_name} both as a UI label and as
 * the cross-call identifier between {@code listInboundFaxes} and {@code downloadFax}/{@code markFaxAsRead}.
 * RingCentral needs both a message id and an attachment id for download, so we encode them as
 * {@code messageId:attachmentId:fileName} and extract them on the way back. Keeping that encoding
 * in one place makes the contract explicit and avoids drift between producer and consumer.</p>
 *
 * @since 2026-05-07
 */
record DownloadReference(String messageId, String attachmentId) {

    private static final String SEPARATOR = ":";
    private static final String DEFAULT_FILENAME = "ringcentral-fax.pdf";

    DownloadReference {
        if (StringUtils.isBlank(messageId) || StringUtils.isBlank(attachmentId)) {
            throw new IllegalArgumentException(
                    "DownloadReference requires non-blank messageId and attachmentId");
        }
    }

    static String format(String messageId, String attachmentId, String fileName) {
        // Delegate the blank-id invariant to the canonical constructor — single point of truth.
        DownloadReference reference = new DownloadReference(messageId, attachmentId);
        return reference.messageId() + SEPARATOR + reference.attachmentId() + SEPARATOR
                + StringUtils.defaultIfBlank(fileName, DEFAULT_FILENAME);
    }

    static DownloadReference parse(FaxJob fax) throws RingCentralException {
        if (fax == null) {
            throw new RingCentralException("Fax metadata is required for RingCentral download");
        }
        String reference = fax.getFile_name();
        if (StringUtils.isNotBlank(reference) && reference.contains(SEPARATOR)) {
            String[] parts = reference.split(SEPARATOR, 3);
            if (parts.length >= 2 && StringUtils.isNotBlank(parts[0]) && StringUtils.isNotBlank(parts[1])) {
                return new DownloadReference(parts[0], parts[1]);
            }
        }
        throw new RingCentralException("RingCentral download requires message and attachment identifiers");
    }
}
