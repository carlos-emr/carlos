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
package io.github.carlos_emr.carlos.email.core;

import java.util.Objects;

/**
 * A picture carried inside an email and shown in its formatted version through {@code cid:}: the
 * clinic's footer logo (issue #3981). It is never a link to a web address.
 *
 * @param contentId the Content-ID the HTML refers to, without angle brackets
 * @param contentType {@code image/png} or {@code image/jpeg}
 * @param bytes the picture
 * @since 2026-10-08
 */
public record EmailInlineImage(String contentId, String contentType, byte[] bytes) {

    public EmailInlineImage {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(bytes, "bytes");
    }

    /**
     * @return the file name the part carries, from the Content-ID before its {@code @}, so a mail
     *         app that lists inline pictures shows a real name and extension
     */
    public String fileName() {
        int at = contentId.indexOf('@');
        return (at > 0 ? contentId.substring(0, at) : contentId) + ("image/png".equals(contentType) ? ".png" : ".jpg");
    }
}
