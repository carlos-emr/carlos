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
package io.github.carlos_emr.carlos.fax.hylafax;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for HylaFax command-output parsing.
 *
 * @since 2026-05-05
 */
@Tag("unit")
@Tag("fax")
@Tag("hylafax")
@DisplayName("HylaFaxJobParser Unit Tests")
class HylaFaxJobParserTest extends CarlosUnitTestBase {

    private final HylaFaxJobParser parser = new HylaFaxJobParser();

    @Test
    @DisplayName("should map queued status to SENT")
    void shouldMapQueuedStatus_toSent() {
        assertThat(parser.mapStatus("Waiting for modem to come ready")).isEqualTo(FaxJob.STATUS.SENT);
    }

    @Test
    @DisplayName("should map failed status to ERROR")
    void shouldMapFailedStatus_toError() {
        assertThat(parser.mapStatus("No answer from remote")).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should map done status to COMPLETE")
    void shouldMapDoneStatus_toComplete() {
        assertThat(parser.mapStatus("Done")).isEqualTo(FaxJob.STATUS.COMPLETE);
    }

    @Test
    @DisplayName("should parse submitted job id from sendfax output")
    void shouldParseSubmittedJobId_fromSendfaxOutput() {
        assertThat(parser.parseSubmittedJobId("request id is 12345 for host fax1")).isEqualTo(12345L);
    }

    @Test
    @DisplayName("should parse status row for matching job id")
    void shouldParseStatusRow_forMatchingJobId() {
        String faxstat = """
                JID  Pri S  Owner Number       Pages Dials     TTS Status
                123  127 W  fax   15551212     0:1   0:12  00:00 Waiting for modem
                """;

        FaxJob result = parser.parseStatus(faxstat, 123L);

        assertThat(result.getJobId()).isEqualTo(123L);
        assertThat(result.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
        assertThat(result.getStatusString()).contains("Waiting");
    }

    @Test
    @DisplayName("should parse recvq listing into inbound fax jobs")
    void shouldParseRecvqListing_intoInboundFaxJobs() {
        String listing = "1770000000000\t2048\tfax00001.tif\t+15551212\n";

        List<FaxJob> result = parser.parseRecvqListing(listing);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFile_name()).isEqualTo("fax00001.tif");
        assertThat(result.get(0).getStatus()).isEqualTo(FaxJob.STATUS.RECEIVED);
        assertThat(result.get(0).getRecipient()).isEqualTo("+15551212");
    }
}
