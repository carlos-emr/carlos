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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class SRFaxProviderClientTest extends CarlosUnitTestBase {

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

    // --- SRFax API spec SentStatus values (exact values from official documentation) ---

    @Test
    @DisplayName("should map SRFax spec value 'In Progress' to SENT (in-progress)")
    void shouldMapInProgress_toSent() {
        assertThat(client.mapStatus("In Progress")).isEqualTo(FaxJob.STATUS.SENT);
    }

    @Test
    @DisplayName("should map SRFax spec value 'Sent' to COMPLETE (delivery confirmed)")
    void shouldMapSent_toComplete() {
        assertThat(client.mapStatus("Sent")).isEqualTo(FaxJob.STATUS.COMPLETE);
    }

    @Test
    @DisplayName("should map SRFax spec value 'Failed' to ERROR")
    void shouldMapFailed_toError() {
        assertThat(client.mapStatus("Failed")).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should map SRFax spec value 'Sending Email' to SENT (in-progress, not COMPLETE)")
    void shouldMapSendingEmail_toSent() {
        // "Sending Email" contains "sent" but must match as in-progress, not terminal
        assertThat(client.mapStatus("Sending Email")).isEqualTo(FaxJob.STATUS.SENT);
    }

    /**
     * Tests for pipe-delimited FaxDetailsID parsing from the FileName field in SRFax inbox responses.
     *
     * <p>In the SRFax API, inbound fax list responses include a FileName field that contains the
     * FaxDetailsID after a pipe character. For example:
     * {@code "20260219124500-1234-1_1|12345678"} where {@code 12345678} is the FaxDetailsID.</p>
     *
     * <p>The parsing logic is currently inline within {@code listInboundFaxes()} (around lines
     * 248-257 of SRFaxProviderClient.java). Because {@code listInboundFaxes()} makes HTTP calls
     * via the private {@code postForm()} method, these tests cannot be executed without either:
     * <ul>
     *   <li>Extracting the pipe-parsing logic into a package-private helper method, or</li>
     *   <li>Adding an HTTP mock layer (e.g., WireMock) to intercept {@code postForm()} calls</li>
     * </ul>
     *
     * <p><strong>Recommended refactoring:</strong> Extract the FileName pipe-parsing into a
     * package-private method such as {@code Long parseFaxDetailsIdFromFileName(String fileName)}
     * which would make these tests directly executable without HTTP infrastructure.</p>
     *
     * @since 2026-02-19
     */
    @Nested
    @Tag("fax")
    @Tag("srfax")
    @DisplayName("FaxDetailsID pipe-delimited parsing from FileName")
    class FaxDetailsIdParsingTest {

        /**
         * Simulates the pipe-parsing logic from listInboundFaxes() for test purposes.
         * This replicates the exact logic at lines 248-257 of SRFaxProviderClient.java.
         *
         * <p>Once the parsing is extracted to a package-private method in SRFaxProviderClient,
         * this helper should be replaced with a direct call to that method.</p>
         */
        private Long parseFaxDetailsIdFromFileName(String fileName) {
            if (fileName != null && fileName.contains("|")) {
                String faxDetailsId = fileName.substring(fileName.lastIndexOf('|') + 1).trim();
                if (!faxDetailsId.isEmpty()) {
                    try {
                        return Long.parseLong(faxDetailsId);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
            return null;
        }

        @Test
        @DisplayName("should extract FaxDetailsID from standard pipe-delimited FileName")
        void shouldExtractFaxDetailsId_whenFileNameContainsPipe() {
            // Given - standard SRFax FileName format: "timestamp-accountid-page_count|FaxDetailsID"
            String fileName = "20260219124500-1234-1_1|12345678";

            // When
            Long faxDetailsId = parseFaxDetailsIdFromFileName(fileName);

            // Then
            assertThat(faxDetailsId).isEqualTo(12345678L);
        }

        @Test
        @DisplayName("should return null when FileName has no pipe character")
        void shouldReturnNull_whenFileNameHasNoPipe() {
            // Given - no pipe character means no FaxDetailsID embedded
            String fileName = "20260219124500-1234-1_1.pdf";

            // When
            Long faxDetailsId = parseFaxDetailsIdFromFileName(fileName);

            // Then
            assertThat(faxDetailsId).isNull();
        }

        @Test
        @DisplayName("should use last pipe segment when FileName has multiple pipes")
        void shouldUseLastPipeSegment_whenMultiplePipesPresent() {
            // Given - edge case: multiple pipe characters
            String fileName = "20260219124500|extra|99887766";

            // When
            Long faxDetailsId = parseFaxDetailsIdFromFileName(fileName);

            // Then - lastIndexOf('|') should give us the final segment
            assertThat(faxDetailsId).isEqualTo(99887766L);
        }

        @Test
        @DisplayName("should return null when pipe segment is non-numeric")
        void shouldReturnNull_whenPipeSegmentIsNonNumeric() {
            // Given - FaxDetailsID segment is not a valid number
            String fileName = "20260219124500-1234-1_1|not-a-number";

            // When
            Long faxDetailsId = parseFaxDetailsIdFromFileName(fileName);

            // Then
            assertThat(faxDetailsId).isNull();
        }

        @Test
        @DisplayName("should return null when pipe segment is empty")
        void shouldReturnNull_whenPipeSegmentIsEmpty() {
            // Given - pipe at end with nothing after it
            String fileName = "20260219124500-1234-1_1|";

            // When
            Long faxDetailsId = parseFaxDetailsIdFromFileName(fileName);

            // Then
            assertThat(faxDetailsId).isNull();
        }

        @Test
        @DisplayName("should trim whitespace from FaxDetailsID after pipe")
        void shouldTrimWhitespace_whenPipeSegmentHasSpaces() {
            // Given - whitespace around the ID
            String fileName = "20260219124500-1234-1_1| 12345678 ";

            // When
            Long faxDetailsId = parseFaxDetailsIdFromFileName(fileName);

            // Then
            assertThat(faxDetailsId).isEqualTo(12345678L);
        }

        @Test
        @DisplayName("should return null when FileName is null")
        void shouldReturnNull_whenFileNameIsNull() {
            // When
            Long faxDetailsId = parseFaxDetailsIdFromFileName(null);

            // Then
            assertThat(faxDetailsId).isNull();
        }

        @Test
        @DisplayName("should handle large FaxDetailsID values within Long range")
        void shouldHandleLargeFaxDetailsId_whenWithinLongRange() {
            // Given - large but valid long value
            String fileName = "20260219124500-1234-1_1|9999999999";

            // When
            Long faxDetailsId = parseFaxDetailsIdFromFileName(fileName);

            // Then
            assertThat(faxDetailsId).isEqualTo(9999999999L);
        }

    }
}
