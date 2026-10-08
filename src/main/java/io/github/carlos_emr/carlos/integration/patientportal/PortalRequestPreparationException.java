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
package io.github.carlos_emr.carlos.integration.patientportal;


import java.io.Serial;

/**
 * CARLOS refused to build a portal request from its own data, so nothing was sent: a provider
 * name the portal cannot accept, a permission set outside the contract, a malformed identifier.
 *
 * <p>A dedicated type lets the web boundary answer these as a 400 without also swallowing an
 * unrelated {@link IllegalArgumentException}, which is a programming error and must stay a 500.
 * Messages are fixed text naming the rule, never the rejected value.
 *
 * @since 2026-09-23
 */
public final class PortalRequestPreparationException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PortalRequestPreparationException(String message) {
        super(message);
    }
}
