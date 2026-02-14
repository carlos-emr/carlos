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
package io.github.carlos_emr.carlos.fax.provider;

/**
 * Domain exception for provider transport failures.
 *
 * <p>This exception keeps provider-specific failures from leaking low-level transport details
 * directly into the fax orchestration flow.</p>
 *
 * @since 2026-02-11
 */
public class FaxProviderException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a provider exception with message only.
     */
    public FaxProviderException(String message) {
        super(message);
    }

    /**
     * Creates a provider exception with message and original cause.
     */
    public FaxProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
