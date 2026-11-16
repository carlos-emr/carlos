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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cdsimport.assembler.CdsImportViewModelAssembler;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Registration and delegation lists are as long as the clinic needs: no character or entry count
 * refuses a list setting, and a source namespace is bounded only by the token rule (128
 * characters, the width of the run and lineage columns that store it).
 */
@Tag("unit")
class CdsImportConfigurationServiceUnitTest {
    /** 600 sixteen-character provider numbers: more than 10,000 characters as one list. */
    private static final List<String> PROVIDERS =
            IntStream.range(0, 600).mapToObj(n -> String.format("PROVIDER%08d", n)).toList();

    @Test
    void shouldAuthorizeDelegation_whenProviderListExceeds8192Characters() {
        Properties settings = new Properties();
        String list = String.join(", ", PROVIDERS);
        assertThat(list.length()).isGreaterThan(8192);
        settings.setProperty("cds.import.operator.clinic_operator.providers", list);
        var configuration = new CdsImportConfigurationService(settings);
        for (String provider : List.of(PROVIDERS.get(0), PROVIDERS.get(299), PROVIDERS.get(599)))
            assertThatCode(
                            () ->
                                    configuration.requireDelegation(
                                            "clinic_operator", provider, null, null))
                    .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                configuration.requireDelegation(
                                        "clinic_operator", "PROVIDER00000600", null, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldApplyFacilityScope_whenFacilityListExceeds8192Characters() {
        Properties settings = new Properties();
        settings.setProperty(
                "cds.import.operator.clinic_operator.providers", String.join(",", PROVIDERS));
        settings.setProperty(
                "cds.import.operator.clinic_operator.facilities",
                IntStream.rangeClosed(1_000_000, 1_002_000)
                        .mapToObj(Integer::toString)
                        .collect(Collectors.joining(",")));
        var configuration = new CdsImportConfigurationService(settings);
        assertThatCode(
                        () ->
                                configuration.requireDelegation(
                                        "clinic_operator", PROVIDERS.get(7), 1_002_000, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                configuration.requireDelegation(
                                        "clinic_operator", PROVIDERS.get(7), 7, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldAcceptSourceNamespace_whenNamespaceHas128Characters() {
        String namespace = "n".repeat(128);
        Properties settings = new Properties();
        settings.setProperty("cds.import.source." + namespace + ".profiles", "omd-5.2");
        var configuration = new CdsImportConfigurationService(settings);
        assertThatCode(() -> configuration.requireSource(namespace, "omd-5.2"))
                .doesNotThrowAnyException();
        String longer = namespace + "n";
        assertThatThrownBy(() -> configuration.requireSource(longer, "omd-5.2"))
                .isInstanceOf(CdsImportException.class)
                .hasMessage("IMPORT_IDENTITY_TOKEN_INVALID");
    }

    @Test
    void shouldOfferEverySource_whenMoreThan200NamespacesAreRegistered() {
        Properties settings = new Properties();
        List<String> namespaces =
                IntStream.range(0, 600).mapToObj(n -> "clinic-source-" + n).toList();
        String registry = String.join(",", namespaces);
        assertThat(registry.length()).isGreaterThan(8192);
        settings.setProperty("cds.import.sourceNamespaces", registry);
        for (String namespace : namespaces)
            settings.setProperty("cds.import.source." + namespace + ".profiles", "omd-5.2");
        var programs = mock(CdsProgramContextService.class);
        when(programs.defaultProgramId(anyString())).thenReturn(null);
        var actor = mock(LoggedInInfo.class);
        var security = new Security();
        security.setId(42);
        when(actor.getLoggedInSecurity()).thenReturn(security);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        var model =
                new CdsImportViewModelAssembler(
                                new CdsImportConfigurationService(settings), programs)
                        .assemble(actor);
        assertThat(model.sources()).hasSize(600);
        assertThat(model.sources().get(599).namespace()).isEqualTo("clinic-source-599");
    }
}
