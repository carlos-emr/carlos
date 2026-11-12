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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * How the device-code job treats one item that fails in an unexpected way, and how it recognises a code
 * whose email went out before its "emailed" mark failed. The whole job runs against MariaDB in
 * {@code PortalMyCarlosDeviceCodeSyncMariaIntegrationTest}.
 *
 * @since 2026-11-12
 */
@DisplayName("Device code job: one failing item")
@Tag("unit")
class PortalMyCarlosStaffApprovalJobFailureUnitTest {

    private static final String PASSED_OVER =
            "myCarlos device code job: one request could not be handled and was passed over; failure=IllegalStateException";

    private final AtomicInteger fenceRuns = new AtomicInteger();
    private final Runnable fence = fenceRuns::incrementAndGet;

    @Test
    @DisplayName("should pass over one failing request and say so, after checking the configuration")
    void shouldPassOverAndLog_forOneFailingRequest() {
        int[] passed = new int[1];
        try (var log = LogCapture.forLogger(PortalMyCarlosStaffApprovalService.class)) {
            PortalMyCarlosStaffApprovalService.passOver("request", new IllegalStateException("FAKE"), fence, passed,
                    new HashSet<>(), null);

            assertThat(log.messages()).containsExactly(PASSED_OVER);
        }
        assertThat(passed[0]).isOne();
        assertThat(fenceRuns.get()).isOne();
    }

    @Test
    @DisplayName("should log a follow-up item that fails every run only once")
    void shouldLogOnce_forAFollowUpItemThatKeepsFailing() {
        int[] passed = new int[1];
        Set<Bytes> logged = new HashSet<>();
        Bytes item = bytes(32, 7);
        try (var log = LogCapture.forLogger(PortalMyCarlosStaffApprovalService.class)) {
            PortalMyCarlosStaffApprovalService.passOver("follow-up item", new IllegalStateException("FAKE"), fence,
                    passed, logged, item);
            PortalMyCarlosStaffApprovalService.passOver("follow-up item", new IllegalStateException("FAKE"), fence,
                    new int[1], logged, item);

            assertThat(log.messages()).hasSize(1);
        }
    }

    @Test
    @DisplayName("should still stop the run when approval is switched off")
    void shouldRethrow_whenApprovalIsSwitchedOff() {
        var refusal = new PortalMyCarlosDeviceApprovalService.Refusal("approval_unavailable");

        assertThatThrownBy(() -> PortalMyCarlosStaffApprovalService.passOver("request", refusal, fence, new int[1],
                new HashSet<>(), null)).isSameAs(refusal);
        assertThat(fenceRuns.get()).isZero();
    }

    @Test
    @DisplayName("should still stop the run when the configuration changed")
    void shouldStopTheRun_whenTheFenceFails() {
        Runnable changed = () -> {
            throw new IllegalStateException("approval configuration changed");
        };

        assertThatThrownBy(() -> PortalMyCarlosStaffApprovalService.passOver("request",
                new IllegalStateException("FAKE"), changed, new int[1], new HashSet<>(), null))
                .hasMessage("approval configuration changed");
    }

    @Test
    @DisplayName("should stop the run after three passed-over items, as for a failure that is not one item's")
    void shouldStopTheRun_afterMoreThanThreeItems() {
        int[] passed = new int[1];
        for (int i = 0; i < PortalMyCarlosStaffApprovalService.MAX_PASSED_OVER; i++) {
            PortalMyCarlosStaffApprovalService.passOver("request", new IllegalStateException("FAKE"), fence, passed,
                    new HashSet<>(), null);
        }
        var fourth = new IllegalStateException("FAKE fourth");

        assertThatThrownBy(() -> PortalMyCarlosStaffApprovalService.passOver("request", fourth, fence, passed,
                new HashSet<>(), null)).isSameAs(fourth);
        assertThat(PortalMyCarlosStaffApprovalService.MAX_PASSED_OVER).isEqualTo(3);
    }

    @Test
    @DisplayName("should recognise a sent code by its MAC while it is open and not yet marked")
    void shouldRecogniseSentCode_whenItsMarkFailed() {
        Bytes mac = bytes(32, 1);

        assertThat(PortalMyCarlosStaffApprovalService.sentButNotMarked(code(mac, null, null), mac)).isTrue();
    }

    @Test
    @DisplayName("should not take another code, a marked code, a decided code or no record for a sent one")
    void shouldNotRecogniseSentCode_otherwise() {
        Bytes mac = bytes(32, 1);
        Instant later = Instant.parse("2026-11-12T12:01:00Z");

        assertThat(PortalMyCarlosStaffApprovalService.sentButNotMarked(code(bytes(32, 2), null, null), mac)).isFalse();
        assertThat(PortalMyCarlosStaffApprovalService.sentButNotMarked(code(mac, later, null), mac)).isFalse();
        assertThat(PortalMyCarlosStaffApprovalService.sentButNotMarked(code(mac, later, later), mac)).isFalse();
        assertThat(PortalMyCarlosStaffApprovalService.sentButNotMarked(code(mac, null, null), null)).isFalse();
        assertThat(PortalMyCarlosStaffApprovalService.sentButNotMarked(null, mac)).isFalse();
    }

    /** A FAKE code row: the given MAC, emailed and matched times; never refused. */
    private static PortalMyCarlosDeviceApprovalCode code(Bytes mac, Instant emailedAt, Instant matchedAt) {
        Instant issued = Instant.parse("2026-11-12T12:00:00Z");
        return new PortalMyCarlosDeviceApprovalCode(bytes(32, 9), "clinic-1",
                UUID.fromString("10000000-0000-4000-8000-000000000001"), "code", bytes(16, 3), mac, 0, issued,
                issued.plusSeconds(600), emailedAt, matchedAt, null, 0);
    }

    private static Bytes bytes(int length, int fill) {
        byte[] value = new byte[length];
        Arrays.fill(value, (byte) fill);
        return new Bytes(value);
    }
}
