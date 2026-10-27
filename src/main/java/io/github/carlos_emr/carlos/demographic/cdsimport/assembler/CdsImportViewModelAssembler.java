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
package io.github.carlos_emr.carlos.demographic.cdsimport.assembler;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportConfigurationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.viewmodel.CdsImportViewModel;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsProgramContextService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Read-only construction of one bounded import-page view model; no clinical or upload writes. */
@Service
public class CdsImportViewModelAssembler {
    private final CdsImportConfigurationService configuration;
    private final CdsProgramContextService programs;
    public CdsImportViewModelAssembler(CdsImportConfigurationService configuration, CdsProgramContextService programs) {
        this.configuration = configuration; this.programs = programs;
    }

    public CdsImportViewModel assemble(HttpServletRequest request, LoggedInInfo actor) {
        String configured = configuration.value("cds.import.sourceNamespaces", "");
        if (configured.length() > 8192) throw new IllegalStateException("IMPORT_SOURCE_REGISTRY_TOO_LARGE");
        Set<String> namespaces = new LinkedHashSet<>();
        for (String value : configured.split(",")) {
            if (!value.isBlank()) namespaces.add(value.trim());
            if (namespaces.size() > 200) throw new IllegalStateException("IMPORT_SOURCE_REGISTRY_TOO_LARGE");
        }
        List<CdsImportViewModel.Source> sources = new ArrayList<>();
        for (String namespace : namespaces) {
            CdsImportConfigurationService.requireToken(namespace);
            for (String profile : List.of("pcon-2024-02", "omd-5.2")) {
                if (java.util.Arrays.stream(configuration.value("cds.import.source." + namespace + ".profiles", "").split(","))
                        .map(String::trim).anyMatch(profile::equals)) {
                    sources.add(new CdsImportViewModel.Source(namespace, profile,
                            namespace + " — " + (profile.equals("omd-5.2") ? "OntarioMD 5.2" : "PCON-2024-02 / 5.1")));
                }
            }
        }
        Integer selectedProgram = programs.defaultProgramId(actor.getLoggedInProviderNo());
        String program = selectedProgram == null ? "" : selectedProgram.toString();
        String facility = actor.getCurrentFacility() == null ? "" : String.valueOf(actor.getCurrentFacility().getId());
        return new CdsImportViewModel(configuration.enabled(),
                configuration.value("OSCAR_LEARNING", "no").equalsIgnoreCase("yes"),
                "web-" + actor.getLoggedInSecurity().getId(), actor.getLoggedInProviderNo(), program, facility,
                Math.toIntExact(configuration.number("cds.import.upload.maxChunkBytes", 4L << 20)),
                configuration.number("cds.import.upload.maxFileBytes", 64L << 30), sources);
    }
}
