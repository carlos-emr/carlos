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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The provider rows one chart names are looked up whatever their number; only the capture's own
 * identity is checked.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportPublicProviderLookupsDtoUnitTest {
    private static final String CAPTURE = "a".repeat(64);

    private static CdsExportCapturedTextRowDto provider(String id) {
        return new CdsExportCapturedTextRowDto(
                "provider", id, Map.of("provider_no", id, "last_name", "FIXTURE"));
    }

    @Test
    void shouldFindEveryProvider_whenAChartNamesMoreThan65536() {
        // The lookups once refused more than 65,536 provider rows, which failed the whole job.
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (int number = 1; number <= 65_537; number++)
            providers.put("P" + number, provider("P" + number));
        var lookups = new CdsExportPublicProviderLookupsDto(CAPTURE, providers);
        assertThat(lookups.find("P1").fields()).containsEntry("provider_no", "P1");
        assertThat(lookups.find("P65537").fields()).containsEntry("provider_no", "P65537");
        assertThat(lookups.find("P65538")).isNull();
    }

    @Test
    void shouldStillRefuseLookups_withoutTheCaptureIdentity() {
        assertThatThrownBy(() -> new CdsExportPublicProviderLookupsDto("not-a-digest", Map.of()))
                .hasMessage("CDS_EXPORT_PUBLIC_PROVIDER_LOOKUPS_INVALID");
        assertThatThrownBy(() -> new CdsExportPublicProviderLookupsDto(CAPTURE, null))
                .hasMessage("CDS_EXPORT_PUBLIC_PROVIDER_LOOKUPS_INVALID");
    }
}
