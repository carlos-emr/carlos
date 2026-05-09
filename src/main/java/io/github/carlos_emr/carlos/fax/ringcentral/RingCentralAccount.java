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

import org.apache.commons.lang3.StringUtils;

/**
 * RingCentral request identity tuple bundling the OAuth access token with the targeted account
 * and extension. Bundling these three strings into a record prevents the silent argument-swap
 * hazard that bare positional {@code String} parameters created at every connector call site.
 *
 * <p>The compact constructor rejects blank components: an account that lacks any one of the three
 * fields cannot drive a successful API call, so accepting blanks would forfeit the safety the
 * type was created for. {@link #toString()} redacts the access token so an accidental log line
 * cannot leak a bearer credential into operator output.</p>
 *
 * @since 2026-05-08
 */
public record RingCentralAccount(String accessToken, String accountId, String extensionId) {

    public RingCentralAccount {
        if (StringUtils.isAnyBlank(accessToken, accountId, extensionId)) {
            throw new IllegalArgumentException(
                    "RingCentralAccount requires non-blank accessToken, accountId, and extensionId");
        }
    }

    @Override
    public String toString() {
        return "RingCentralAccount[accessToken=***, accountId=" + accountId
                + ", extensionId=" + extensionId + "]";
    }
}
