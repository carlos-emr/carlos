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
package io.github.carlos_emr.carlos.demographic.cdsexport.assembler;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportConfigurationService;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportService;
import io.github.carlos_emr.carlos.demographic.cdsexport.viewmodel.CdsExportViewModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Arrays;
import java.util.Comparator;
import org.springframework.stereotype.Service;

/** No source reads or job mutations occur while building the page. Only qualified profiles are selectable. */
@Service
public class CdsExportViewModelAssembler {
    private final CdsExportConfigurationService configuration;
    private final CdsExportService exports;
    public CdsExportViewModelAssembler(CdsExportConfigurationService configuration, CdsExportService exports) {
        this.configuration = configuration; this.exports = exports;
    }
    public CdsExportViewModel assemble(LoggedInInfo viewer) {
        var profiles = exports.qualifiedProfiles().stream().sorted(Comparator.comparing(Enum::name))
                .map(profile -> new CdsExportViewModel.Option(profile.identifier(), profile.identifier().equals("omd-5.2")
                        ? "OntarioMD 5.2" : "PCON 2024-02")).toList();
        var categories = Arrays.stream(CdsCategory.values()).map(category -> new CdsExportViewModel.Option(
                category.wireName(), category.wireName().replaceAll("([a-z])([A-Z])", "$1 $2"))).toList();
        return new CdsExportViewModel(configuration.enabled() && !profiles.isEmpty(), exports.dispatcherRunning(),
                "web-" + viewer.getLoggedInSecurity().getId(),
                viewer.getLoggedInProviderNo(), viewer.getCurrentFacility() == null ? "" : viewer.getCurrentFacility().getId().toString(),
                profiles, categories);
    }
}
