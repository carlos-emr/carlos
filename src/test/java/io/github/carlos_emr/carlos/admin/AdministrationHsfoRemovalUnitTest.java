/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the removal of the retired HSFO / HSFO2 (Ontario hypertension registry) submission
 * entries from the Administration panel (issue #4564).
 *
 * <p>The two menu links pointed at {@code admin/RecommitHSFO} and {@code admin/RecommitHSFO2},
 * for which no Struts action, servlet or class exists, so they answered 404 on any site that set
 * {@code hsfo.loginSiteCode} or {@code hsfo2.loginSiteCode}. The maintainers confirmed the
 * feature was never used, so the entries, their translations and the sample configuration that
 * invited a site to enable them were removed rather than re-routed.
 *
 * <p>The {@code form_hsfo2_visit}, {@code hsfo2_patient}, {@code hsfo2_system} and
 * {@code hsfo_recommit_schedule} tables are deliberately NOT covered here: they are created by a
 * published Flyway migration and may hold imported OSCAR 19 data, so they are retained.
 *
 * @since 2026-10-15
 */
@DisplayName("Administration HSFO Removal Tests")
@Tag("unit")
@Tag("fast")
@Tag("admin")
class AdministrationHsfoRemovalUnitTest {

    private static final Path LEFT_NAV =
            Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf");
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path CARLOS_PROPERTIES = RESOURCES.resolve("carlos.properties");

    @Test
    @DisplayName("should not offer HSFO submission links in the administration left nav")
    void shouldNotOfferHsfoLinks_inAdministrationLeftNav() throws IOException {
        String jsp = Files.readString(LEFT_NAV, StandardCharsets.UTF_8);

        assertThat(jsp)
                .as("RecommitHSFO / RecommitHSFO2 have no route; the links must not come back")
                .doesNotContainIgnoringCase("RecommitHSFO")
                .doesNotContainIgnoringCase("hsfo.loginSiteCode")
                .doesNotContainIgnoringCase("hsfo2.loginSiteCode")
                .doesNotContain("admin.admin.hsfoSubmit")
                .doesNotContain("admin.admin.hsfo2Submit");
    }

    @Test
    @DisplayName("should keep the neighbouring DrugRef update link in the left nav")
    void shouldKeepDrugrefUpdateLink_inAdministrationLeftNav() throws IOException {
        String jsp = Files.readString(LEFT_NAV, StandardCharsets.UTF_8);

        assertThat(jsp)
                .as("removing the HSFO blocks must not take the following menu entry with them")
                .contains("${ctx}/admin/ViewUpdateDrugref")
                .contains("admin.admin.UpdateDrugref");
    }

    @Test
    @DisplayName("should not define HSFO menu labels in any message bundle")
    void shouldNotDefineHsfoMenuLabels_inMessageBundles() throws IOException {
        List<Path> bundles;
        try (Stream<Path> files = Files.list(RESOURCES)) {
            bundles = files
                    .filter(p -> p.getFileName().toString().startsWith("oscarResources")
                            && p.getFileName().toString().endsWith(".properties"))
                    .toList();
        }

        assertThat(bundles).as("expected the oscarResources_* bundles to be found").isNotEmpty();
        for (Path bundle : bundles) {
            assertThat(Files.readString(bundle, StandardCharsets.ISO_8859_1))
                    .as("%s must not carry the orphaned HSFO menu labels", bundle.getFileName())
                    .doesNotContain("admin.admin.hsfoSubmit")
                    .doesNotContain("admin.admin.hsfo2Submit");
        }
    }

    @Test
    @DisplayName("should not ship any HSFO setting in carlos.properties")
    void shouldNotShipHsfoSettings_inCarlosProperties() throws IOException {
        String properties = Files.readString(CARLOS_PROPERTIES, StandardCharsets.ISO_8859_1);

        assertThat(properties)
                .as("carlos.properties must not invite a site to enable the retired HSFO integration, "
                        + "commented out or not")
                .doesNotContainIgnoringCase("hsfo");
    }
}
