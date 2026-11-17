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

import java.util.List;

/**
 * Thrown by {@code EmailManager.sendEmail} when a field is too long for the {@code emailLog}
 * table. Nothing has been persisted or sent when this is thrown; callers should show the
 * violations to the provider so they can shorten the text.
 *
 * <p>The message lists only message keys and sizes, never field content, so it carries no PHI.</p>
 *
 * @see EmailFieldLengthValidator
 * @since 2026-09-24
 */
public class EmailFieldLengthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<EmailFieldLengthValidator.Violation> violations;

    /**
     * @param violations the over-length fields; must not be empty
     */
    public EmailFieldLengthException(List<EmailFieldLengthValidator.Violation> violations) {
        super("Email fields exceed storage limits: " + violations);
        this.violations = List.copyOf(violations);
    }

    /**
     * @return the over-length fields, never empty
     */
    public List<EmailFieldLengthValidator.Violation> getViolations() {
        return violations;
    }
}
