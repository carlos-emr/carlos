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
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixed NHS gateway (tools/ai-clinical-summary-draft/browser) answers with passages it does not
 * generate. If the server's passage rules tighten, the gateway silently stops working: every generation
 * fails and the NHS live checks cannot run. This test runs each of its passages through the server's
 * own validation against the exact committed synthetic note.
 */
class ChartUpdateNhsFixtureGatewayUnitTest {
    // The same note pattern the Python tooling reads (openrouter_agent.committed_notes).
    private static final Pattern SEED_NOTE = Pattern.compile("INSERT INTO casemgmt_note [^\\n]+?SELECT @nhs_demographic_no,'999998',"
            + "CONVERT\\(0x([0-9a-fA-F]+) USING utf8mb4\\),CONVERT\\(0x([0-9a-fA-F]+) USING utf8mb4\\)");
    // Each seeded note starts with a provenance header; the clinical text follows this line (openrouter_agent.BOUNDARY).
    private static final String BOUNDARY = "Source text below is preserved verbatim, including encoding and clinical inconsistencies.\n\n";

    @Test void shouldAcceptEveryFixedPassage_whenValidatedAgainstItsCommittedNote() throws Exception {
        JsonNode cases = JSON.readTree(Files.readString(
                Path.of("tools/ai-clinical-summary-draft/browser/nhs_fixture_proposals.json"))).get("cases");
        Map<String, String> notes = seedNotesBySha256();
        assertThat(cases.size()).isEqualTo(3);
        for (var fixture : cases.properties()) {
            String source = notes.get(fixture.getValue().get("source_sha256").asText());
            assertThat(source).as("%s source note in the committed seed", fixture.getKey()).isNotNull();
            var output = JSON.createObjectNode().set("proposals", fixture.getValue().get("proposals"));
            var proposals = ChartUpdateProposals.validate(output, source);
            // The NHS live checks expect one reminder and two chart entries per note.
            assertThat(proposals).as(fixture.getKey()).hasSize(3);
            assertThat(proposals.stream().filter(proposal -> "tickler".equals(proposal.kind())).count())
                    .as(fixture.getKey()).isEqualTo(1);
        }
    }

    private static Map<String, String> seedNotesBySha256() throws Exception {
        String seed = Files.readString(Path.of(".devcontainer/db/scripts/nhs-synthetic/patients.sql"));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Map<String, String> notes = new HashMap<>();
        var matcher = SEED_NOTE.matcher(seed);
        while (matcher.find()) {
            String seeded = new String(HexFormat.of().parseHex(matcher.group(1)), StandardCharsets.UTF_8);
            int start = seeded.indexOf(BOUNDARY);
            if (start < 0) continue;
            String body = seeded.substring(start + BOUNDARY.length());
            notes.put(HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8))), body);
        }
        assertThat(notes).as("synthetic notes in the committed seed").isNotEmpty();
        return notes;
    }
}
