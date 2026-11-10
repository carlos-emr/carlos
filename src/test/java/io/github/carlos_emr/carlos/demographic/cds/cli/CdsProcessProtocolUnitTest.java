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
package io.github.carlos_emr.carlos.demographic.cds.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Exact shared protocol semantics, independently exercised for the export direction. */
@Tag("unit")
class CdsProcessProtocolUnitTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final String NONCE = "12345678-1234-4234-8234-123456789012";
    private static final String[] ARGS = {"--operation", "export", "--provider", "999998"};
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void shouldReadOnlyTheChosenDirectionsHeap_withJavaPropertiesEscaping() throws Exception {
        byte[] bytes =
                "cds.import.cliHeapMiB=invalid\ncds.export.cliHeapMiB=2048\n"
                        .getBytes(StandardCharsets.ISO_8859_1);
        assertThat(
                        CdsProcessConfiguration.parse(
                                        Path.of("/synthetic"), bytes, CdsProcessScope.EXPORT)
                                .heapMiB())
                .isEqualTo(2048);
        assertThatThrownBy(
                        () ->
                                CdsProcessConfiguration.parse(
                                        Path.of("/synthetic"), bytes, CdsProcessScope.IMPORT))
                .hasMessage("CDS_HEAP_CONFIGURATION_INVALID");
        byte[] escaped =
                ("cds.export.cliHeapMiB=" + "\\u0032\\u0030\\u0034\\u0038")
                        .getBytes(StandardCharsets.ISO_8859_1);
        assertThat(
                        CdsProcessConfiguration.parse(
                                        Path.of("/synthetic"), escaped, CdsProcessScope.EXPORT)
                                .heapMiB())
                .isEqualTo(2048);
    }

    @Test
    void shouldUseTheSameBoundedDefault_andRefuseInvalidExportBudgets() throws Exception {
        assertThat(
                        CdsProcessConfiguration.parse(
                                        Path.of("/synthetic"), new byte[0], CdsProcessScope.EXPORT)
                                .heapMiB())
                .isEqualTo(512);
        for (String value :
                new String[] {"127", "16385", "0", "-1", "2m", "512 #comment", "", "999999"}) {
            byte[] bytes = ("cds.export.cliHeapMiB=" + value).getBytes(StandardCharsets.ISO_8859_1);
            assertThatThrownBy(
                            () ->
                                    CdsProcessConfiguration.parse(
                                            Path.of("/synthetic"), bytes, CdsProcessScope.EXPORT))
                    .hasMessage("CDS_HEAP_CONFIGURATION_INVALID");
        }
    }

    @Test
    void shouldKeepDistinctFixedTicketRoots_andNeverAcceptAnArbitraryPropertiesPath() {
        assertThat(CdsProcessScope.IMPORT.ticketDirectory())
                .isEqualTo(Path.of("/run/carlos-cds-delegations"));
        assertThat(CdsProcessScope.EXPORT.ticketDirectory())
                .isEqualTo(Path.of("/run/carlos-cds-export-delegations"));
        assertThatThrownBy(
                        () ->
                                CdsProcessConfiguration.load(
                                        Map.of("CARLOS_CDS_PROPERTIES", "/tmp/forged.properties"),
                                        CdsProcessScope.EXPORT))
                .hasMessage("CDS_PRODUCTION_CONFIGURATION_REQUIRED");
    }

    @Test
    void shouldVerifyTheExactExportTicket_andPreserveAMachineAccount() throws Exception {
        var proof = verify(ticket(), ARGS);
        assertThat(proof.operator()).isEqualTo("migration$");
        assertThat(proof.operatorUid()).isEqualTo(1234);
        assertThat(proof.nonce()).isEqualTo(NONCE);
        assertThat(proof.toString()).doesNotContain("migration$");
    }

    @Test
    void shouldRefuseProviderOperationAndArgumentSubstitution_forProcessProtocolContract() throws Exception {
        for (String key : new String[] {"provider", "operation", "argvSha256"}) {
            var altered = ticket();
            altered.put(key, key.equals("argvSha256") ? "a".repeat(64) : "other");
            assertThatThrownBy(() -> verify(altered, ARGS)).hasMessage("CDS_DELEGATION_DENIED");
        }
        assertThatThrownBy(
                        () ->
                                verify(
                                        ticket(),
                                        new String[] {
                                            "--operation", "cancel", "--provider", "999998"
                                        }))
                .hasMessage("CDS_DELEGATION_DENIED");
    }

    @Test
    void shouldRefuseExpiredFutureAndOverlongDelegationWindows_forProcessProtocolContract() throws Exception {
        for (long[] window : new long[][] {{-600, -1}, {60, 120}, {-10, 400}}) {
            var altered = ticket();
            altered.put("issuedEpochSeconds", NOW.getEpochSecond() + window[0]);
            altered.put("expiresEpochSeconds", NOW.getEpochSecond() + window[1]);
            assertThatThrownBy(() -> verify(altered, ARGS)).hasMessage("CDS_DELEGATION_DENIED");
        }
    }

    @Test
    void shouldRefuseUnknownDuplicateOrTrailingTicketData_forProcessProtocolContract() throws Exception {
        var altered = ticket();
        altered.put("isAdmin", true);
        assertThatThrownBy(() -> verify(altered, ARGS)).hasMessage("CDS_DELEGATION_DENIED");
        String text = JSON.writeValueAsString(ticket());
        for (String malformed :
                new String[] {
                    text + "{}", text.substring(0, text.length() - 1) + ",\"provider\":\"999998\"}"
                }) {
            assertThatThrownBy(
                            () ->
                                    CdsProcessDelegationVerifier.verifyDocument(
                                            malformed.getBytes(StandardCharsets.UTF_8),
                                            NONCE + ".json",
                                            "999998",
                                            "export",
                                            ARGS,
                                            NOW))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldBindCanonicalNonceFilename_andRefuseOversizedDocuments() throws Exception {
        byte[] bytes = JSON.writeValueAsBytes(ticket());
        assertThatThrownBy(
                        () ->
                                CdsProcessDelegationVerifier.verifyDocument(
                                        bytes, "another.json", "999998", "export", ARGS, NOW))
                .hasMessage("CDS_DELEGATION_DENIED");
        assertThatThrownBy(
                        () ->
                                CdsProcessDelegationVerifier.verifyDocument(
                                        new byte[65537],
                                        NONCE + ".json",
                                        "999998",
                                        "export",
                                        ARGS,
                                        NOW))
                .hasMessage("CDS_DELEGATION_DENIED");
    }

    @Test
    void shouldLengthPrefixArguments_insteadOfHashingAnAmbiguousConcatenation() {
        assertThat(CdsProcessDelegationVerifier.digest(new String[] {"a", "bc"}))
                .isNotEqualTo(CdsProcessDelegationVerifier.digest(new String[] {"ab", "c"}));
    }

    private static Map<String, Object> ticket() {
        var value = new LinkedHashMap<String, Object>();
        value.put("protocol", 1);
        value.put("operator", "migration$");
        value.put("operatorUid", 1234L);
        value.put("provider", "999998");
        value.put("operation", "export");
        value.put(
                "argvSha256", HexFormat.of().formatHex(CdsProcessDelegationVerifier.digest(ARGS)));
        value.put("nonce", NONCE);
        value.put("issuedEpochSeconds", NOW.getEpochSecond() - 1);
        value.put("expiresEpochSeconds", NOW.getEpochSecond() + 299);
        return value;
    }

    private static CdsProcessDelegationVerifier.Delegation verify(
            Map<String, Object> document, String[] arguments) throws Exception {
        return CdsProcessDelegationVerifier.verifyDocument(
                JSON.writeValueAsBytes(document),
                NONCE + ".json",
                "999998",
                "export",
                arguments,
                NOW);
    }
}
