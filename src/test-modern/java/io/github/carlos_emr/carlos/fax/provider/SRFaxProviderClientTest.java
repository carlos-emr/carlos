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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SRFax status mapping semantics.
 * Tests the mapping of various SRFax status strings to internal FaxJob.STATUS enum values.
 *
 * @since 2026-02-11
 */
@Tag("unit")
@Tag("fax")
@Tag("srfax")
@DisplayName("SRFaxProviderClient Unit Tests")
class SRFaxProviderClientTest extends OpenOUnitTestBase {

    private SRFaxProviderClient client;

    @BeforeEach
    void setUp() {
        client = new SRFaxProviderClient();
    }

    @Test
    @DisplayName("should return COMPLETE status when SRFax reports successfully sent")
    void shouldReturnCompleteStatus_whenSuccessfullySent() {
        // When
        FaxJob.STATUS result = client.mapStatus("Successfully Sent");

        // Then
        assertThat(result).isEqualTo(FaxJob.STATUS.COMPLETE);
    }

    @Test
    @DisplayName("should return SENT status when SRFax reports in queue")
    void shouldReturnSentStatus_whenInQueue() {
        // When
        FaxJob.STATUS result = client.mapStatus("In queue");

        // Then
        assertThat(result).isEqualTo(FaxJob.STATUS.SENT);
    }

    @Test
    @DisplayName("should return CANCELLED status when SRFax reports cancelled by user")
    void shouldReturnCancelledStatus_whenCancelledByUser() {
        // When
        FaxJob.STATUS result = client.mapStatus("Cancelled by user");

        // Then
        assertThat(result).isEqualTo(FaxJob.STATUS.CANCELLED);
    }

    @Test
    @DisplayName("should return ERROR status when SRFax reports no answer")
    void shouldReturnErrorStatus_whenNoAnswer() {
        // When
        FaxJob.STATUS result = client.mapStatus("No answer");

        // Then
        assertThat(result).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should return ERROR status when SRFax reports busy")
    void shouldReturnErrorStatus_whenBusy() {
        // When
        FaxJob.STATUS result = client.mapStatus("Busy");

        // Then
        assertThat(result).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should return SENT status when SRFax reports retry")
    void shouldReturnSentStatus_whenRetry() {
        // When
        FaxJob.STATUS result = client.mapStatus("Retry");

        // Then
        assertThat(result).isEqualTo(FaxJob.STATUS.SENT);
    }

    @Test
    @DisplayName("should return UNKNOWN status when status string is unrecognized")
    void shouldReturnUnknownStatus_whenStatusUnrecognized() {
        // When
        FaxJob.STATUS result = client.mapStatus("Other state");

        // Then
        assertThat(result).isEqualTo(FaxJob.STATUS.UNKNOWN);
    }

    @Test
    @DisplayName("should return UNKNOWN status when status string is null")
    void shouldReturnUnknownStatus_whenStatusIsNull() {
        // When
        FaxJob.STATUS result = client.mapStatus(null);

        // Then
        assertThat(result).isEqualTo(FaxJob.STATUS.UNKNOWN);
    }
}
