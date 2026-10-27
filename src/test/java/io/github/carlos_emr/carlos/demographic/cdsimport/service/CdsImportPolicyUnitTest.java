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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class CdsImportPolicyUnitTest {
    private final CdsImportCommand command = new CdsImportCommand(Path.of("/source/patient.xml"), "request", "T099",
            null, null, "source", "omd-5.2", false, false, false, 0, null, false);

    @Test void shouldRejectMixedNormalizationPolicy_whenZoneOrNativeRepresentationChanges() {
        Properties first = new Properties(); first.setProperty("cds.import.clinicalZone", "America/Toronto");
        String accepted = config(first).normalizationPolicyDigest(command);
        Properties zone = new Properties(); zone.putAll(first); zone.setProperty("cds.import.clinicalZone", "UTC");
        Properties note = new Properties(); note.putAll(first); note.setProperty("cds.import.native.maxNoteBytes", "131072");
        assertThat(config(zone).normalizationPolicyDigest(command)).isNotEqualTo(accepted);
        assertThat(config(note).normalizationPolicyDigest(command)).isNotEqualTo(accepted);
    }

    @Test void shouldKeepPolicyStable_whenOnlyResourcesOrLiveDelegationChange() {
        String accepted = config(new Properties()).normalizationPolicyDigest(command);
        Properties later = new Properties(); later.setProperty("cds.import.maxSourceBytes", "999999");
        later.setProperty("cds.import.enabled", "false"); later.setProperty("cds.import.cliHeapMiB", "1024");
        later.setProperty("cds.import.operator.operator.providers", "T099,T002");
        assertThat(config(later).normalizationPolicyDigest(command)).isEqualTo(accepted);
    }

    @Test void shouldBindMappingOverridesForThisSource_whenOtherSourceRegistrationChanges() {
        String accepted = config(new Properties()).normalizationPolicyDigest(command);
        Properties other = new Properties(); other.setProperty("cds.import.source.another.mapping.status", "custom");
        assertThat(config(other).normalizationPolicyDigest(command)).isEqualTo(accepted);
        other.setProperty("cds.import.source.source.mapping.status", "custom");
        assertThat(config(other).normalizationPolicyDigest(command)).isNotEqualTo(accepted);
    }

    private static CdsImportConfigurationService config(Properties properties) { return new CdsImportConfigurationService(properties); }
}
