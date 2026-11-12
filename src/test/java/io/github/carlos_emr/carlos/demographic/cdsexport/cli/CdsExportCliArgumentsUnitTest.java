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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Internal argv emitted by the installed Python adapter; no command parser may discover a patient or open a file. */
@Tag("unit")
@Tag("cds-export")
class CdsExportCliArgumentsUnitTest {
    private static final String RUN = "22222222-2222-4222-8222-222222222222";
    private static final String NONCE = "33333333-3333-4333-8333-333333333333";

    @Test
    void shouldShareStrictAbsolutePathValidation_betweenCliSelectionAndServerRoots() {
        for (String path :
                List.of(
                        "",
                        "relative/path",
                        "/safe/../other",
                        "/safe/\tbad",
                        "/safe/\nbad",
                        "/" + "a".repeat(8192))) {
            var values = new ArrayList<>(List.of(fresh()));
            int option = values.indexOf("--physicians");
            values.set(option, "--patients-file");
            values.set(option + 1, path);
            assertThatThrownBy(() -> CdsExportCliArguments.parse(values.toArray(String[]::new)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("CDS_EXPORT_ARGUMENTS_INVALID");
            var properties = new java.util.Properties();
            properties.setProperty("cds.export.selectionRoot", path);
            properties.setProperty("cds.import.stagingRoot", path);
            var configuration =
                    new io.github.carlos_emr.carlos.demographic.cdsexport.service
                            .CdsExportConfigurationService(properties);
            assertThatThrownBy(configuration::selectionRoot)
                    .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
            assertThatThrownBy(configuration::retainedImportRoot)
                    .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
        }
    }

    @Test
    void shouldFixTheExplicitRosterIntent_andUseTheVerifiedSubmissionNonce() {
        var args = CdsExportCliArguments.parse(fresh());
        var command = args.command(List.of(), NONCE);
        assertThat(command.selection()).isEqualTo(CdsExportCommand.Selection.PHYSICIAN_ROSTER);
        assertThat(command.physicianNos()).containsExactly("100", "200");
        assertThat(command.categories()).hasSize(16);
        assertThat(command.submissionKey()).isEqualTo(NONCE);
        assertThat(args.toString()).doesNotContain("recipient");
    }

    @Test
    void shouldUseOnlyProtectedCapturedIds_forAnExplicitSelection() {
        var values = new ArrayList<>(List.of(fresh()));
        int index = values.indexOf("--physicians");
        values.set(index, "--patients-file");
        values.set(index + 1, "/var/lib/carlos-emr/selections/selected.json");
        values.addAll(List.of("--submission-key", "operator-submission"));
        var args = CdsExportCliArguments.parse(values.toArray(String[]::new));
        var command = args.command(List.of(28, 27), NONCE);
        assertThat(command.patientNos()).containsExactly(27, 28);
        assertThat(command.submissionKey()).isEqualTo("operator-submission");
        assertThat(command.selection()).isEqualTo(CdsExportCommand.Selection.SELECTED_PATIENTS);
    }

    @Test
    void shouldRefuseUnknownDuplicateConflictingAndMissingOptions_forCliArgumentsContract() {
        for (List<String> suffix :
                List.of(
                        List.of("--provider", "200"),
                        List.of("--unknown", "x"),
                        List.of("--detach", "--detach"),
                        List.of("--patients-file", "/safe/selection.json"),
                        List.of("--run", RUN))) {
            var values = new ArrayList<>(List.of(fresh()));
            values.addAll(suffix);
            assertThatThrownBy(() -> CdsExportCliArguments.parse(values.toArray(String[]::new)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(
                        () ->
                                CdsExportCliArguments.parse(
                                        new String[] {"--operation", "export", "--provider"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectCategoryDuplicatesOrOmittedDemographics_withoutFallingBackToAll() {
        for (String categories :
                new String[] {"ClinicalNotes", "Demographics,Demographics", "unknown"}) {
            var values = new ArrayList<>(List.of(fresh()));
            values.addAll(List.of("--categories", categories));
            assertThatThrownBy(() -> CdsExportCliArguments.parse(values.toArray(String[]::new)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldKeepEveryExistingRunOperation_onItsOriginalIntent() {
        for (String operation : new String[] {"status", "resume", "cancel", "report"}) {
            String[] base = {"--operation", operation, "--provider", "100", "--run", RUN};
            assertThat(CdsExportCliArguments.parse(base).runId()).isEqualTo(RUN);
            for (List<String> replacement :
                    List.of(
                            List.of("--facility", "3"),
                            List.of("--profile", "omd-5.2"),
                            List.of("--categories", "all"),
                            List.of("--recipient", "another"))) {
                var values = new ArrayList<>(List.of(base));
                values.addAll(replacement);
                assertThatThrownBy(() -> CdsExportCliArguments.parse(values.toArray(String[]::new)))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Test
    void shouldRecoverSubmissionStatus_withoutAnInventedRunIdOrReplacementOptions() {
        var args =
                CdsExportCliArguments.parse(
                        new String[] {
                            "--operation",
                            "status",
                            "--provider",
                            "100",
                            "--submission-key",
                            "original-key"
                        });
        assertThat(args.submissionKey()).isEqualTo("original-key");
        assertThat(args.runId()).isNull();
        assertThatThrownBy(
                        () ->
                                CdsExportCliArguments.parse(
                                        new String[] {
                                            "--operation",
                                            "status",
                                            "--provider",
                                            "100",
                                            "--submission-key",
                                            "key",
                                            "--run",
                                            RUN
                                        }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireAnOpaqueArtifactAndAbsoluteNewDestination_forDownload() {
        var args =
                CdsExportCliArguments.parse(
                        new String[] {
                            "--operation",
                            "download",
                            "--provider",
                            "100",
                            "--run",
                            RUN,
                            "--artifact",
                            "opaque-artifact",
                            "--output",
                            "/safe/export.cms"
                        });
        assertThat(args.artifactId()).isEqualTo("opaque-artifact");
        assertThatThrownBy(
                        () ->
                                CdsExportCliArguments.parse(
                                        new String[] {
                                            "--operation",
                                            "download",
                                            "--provider",
                                            "100",
                                            "--run",
                                            RUN
                                        }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowDetachOnly_forNewSubmissionOrExplicitResume() {
        var values = new ArrayList<>(List.of(fresh()));
        values.add("--detach");
        assertThat(CdsExportCliArguments.parse(values.toArray(String[]::new)).detach()).isTrue();
        assertThat(
                        CdsExportCliArguments.parse(
                                        new String[] {
                                            "--operation",
                                            "resume",
                                            "--provider",
                                            "100",
                                            "--run",
                                            RUN,
                                            "--detach"
                                        })
                                .detach())
                .isTrue();
        assertThatThrownBy(
                        () ->
                                CdsExportCliArguments.parse(
                                        new String[] {
                                            "--operation",
                                            "status",
                                            "--provider",
                                            "100",
                                            "--run",
                                            RUN,
                                            "--detach"
                                        }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String[] fresh() {
        return new String[] {
            "--operation",
            "export",
            "--provider",
            "100",
            "--profile",
            "omd-5.2",
            "--physicians",
            "200,100",
            "--recipient",
            "recipient",
            "--audit-plan",
            "audit"
        };
    }
}
