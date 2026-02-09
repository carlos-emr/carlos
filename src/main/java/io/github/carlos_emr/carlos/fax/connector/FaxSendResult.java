/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 */
package io.github.carlos_emr.carlos.fax.connector;

import io.github.carlos_emr.carlos.commn.model.FaxJob;

/**
 * Result of sending a fax through a {@link FaxConnector}.
 *
 * @since 2026-02-09
 */
public class FaxSendResult {

    private final boolean success;
    private final long externalJobId;
    private final FaxJob.STATUS status;
    private final String statusMessage;

    public FaxSendResult(boolean success, long externalJobId, FaxJob.STATUS status, String statusMessage) {
        this.success = success;
        this.externalJobId = externalJobId;
        this.status = status;
        this.statusMessage = statusMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getExternalJobId() {
        return externalJobId;
    }

    public FaxJob.STATUS getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
