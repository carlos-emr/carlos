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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportEngine;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit") @Tag("cds-export")
class CdsExportEngineRegistryUnitTest {
    static class Fixture implements CdsExportEngine {
        String digest = "a".repeat(64); Set<CdsLogicalProfile> profiles = Set.of(CdsLogicalProfile.ONTARIO_MD_5_2);
        @Override public String compatibilityDigest() { return digest; }
        @Override public Set<CdsLogicalProfile> qualifiedProfiles() { return profiles; }
        @Override public CdsExportJobDocumentDto bind(CdsExportActorDto actor, CdsExportCommand command) { throw new AssertionError("Capability lookup must not bind a request"); }
        @Override public void execute(CdsExportLeaseDto lease, CdsExportRunControl control) { throw new AssertionError("Capability lookup must not execute clinical work"); }
    }
    @Test void shouldAdvertiseNoClinicalSupport_withoutAnInstalledQualifiedEngine() {
        var registry = new CdsExportEngineRegistry(List.of()); assertThat(registry.qualifiedProfiles()).isEmpty();
        assertThat(registry.find(CdsLogicalProfile.ONTARIO_MD_5_2)).isNull();
        assertThatThrownBy(() -> registry.require(CdsLogicalProfile.ONTARIO_MD_5_2)).hasMessage("CDS_EXPORT_PROFILE_NOT_QUALIFIED");
    }
    @Test void shouldKeepDirectionAndProfileQualificationExplicit_withoutRunningClinicalWork() {
        var engine = new Fixture(); var registry = new CdsExportEngineRegistry(List.of(engine));
        assertThat(registry.qualifiedProfiles()).containsExactly(CdsLogicalProfile.ONTARIO_MD_5_2);
        assertThat(registry.require(CdsLogicalProfile.ONTARIO_MD_5_2)).isSameAs(engine);
        assertThat(registry.find(CdsLogicalProfile.PCON_2024_02)).isNull();
    }
    @Test void shouldRejectAmbiguousEngines_insteadOfDependingOnBeanDiscoveryOrder() {
        assertThatThrownBy(() -> new CdsExportEngineRegistry(List.of(new Fixture(), new Fixture())))
                .hasMessage("CDS_EXPORT_ENGINE_AMBIGUOUS");
    }
    @Test void shouldRefuseMissingOrChangedRuntimeIdentity_beforeAdvertisingOrExecutingSupport() {
        var invalid = new Fixture(); invalid.digest = "not-a-qualification";
        assertThatThrownBy(() -> new CdsExportEngineRegistry(List.of(invalid))).hasMessage("CDS_EXPORT_ENGINE_IDENTITY_INVALID");
        var changed = new Fixture(); var registry = new CdsExportEngineRegistry(List.of(changed)); changed.digest = "b".repeat(64);
        assertThatThrownBy(registry::qualifiedProfiles).hasMessage("CDS_EXPORT_ENGINE_IDENTITY_CHANGED");
        assertThatThrownBy(() -> registry.require(CdsLogicalProfile.ONTARIO_MD_5_2)).hasMessage("CDS_EXPORT_ENGINE_IDENTITY_CHANGED");
    }
    @Test void shouldRefuseProfileSetDrift_evenWhenTheProviderLeavesItsOldDigestUnchanged() {
        var changed = new Fixture(); var registry = new CdsExportEngineRegistry(List.of(changed));
        changed.profiles = Set.of(CdsLogicalProfile.ONTARIO_MD_5_2, CdsLogicalProfile.PCON_2024_02);
        assertThatThrownBy(registry::qualifiedProfiles).hasMessage("CDS_EXPORT_ENGINE_IDENTITY_CHANGED");
    }
}
