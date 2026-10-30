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

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import static org.assertj.core.api.Assertions.*;

/** Each accepted schema type survives a real write and is recognized by the native page configuration. */
@Tag("integration")
@Isolated("PreventionDisplayConfig is a legacy static catalogue")
@org.springframework.test.context.ContextConfiguration(classes = CdsImmunizationProjectionIntegrationTest.CvcConfiguration.class,
        inheritLocations = false)
class CdsImmunizationProjectionIntegrationTest extends CarlosTestBase {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.ImportResource("classpath:test-context-full.xml")
    static class CvcConfiguration {
        @org.springframework.context.annotation.Bean
        io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager cvcManager() {
            return new io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager();
        }
    }

    @TempDir Path directory;
    @Autowired @Qualifier("preventionDao") private PreventionDao preventions;
    @Autowired private PreventionExtDao extensions;
    @Autowired private PartialDateDao dates;
    @Autowired private CVCMappingDao mappings;
    @Autowired private CVCImmunizationDao vaccines;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    void shouldReadBackConfiguredTypesAndSourceNames_forEverySchemaImmunizationType() throws Exception {
        var factory = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        var parser = factory.newDocumentBuilder();
        var types = new ArrayList<String>();
        try (var input = getClass().getResourceAsStream("/cds-import/schema/EMR_Data_Migration_Schema_DT.xsd")) {
            var schema = parser.parse(input);
            var definitions = schema.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
            for (int i = 0; i < definitions.getLength(); i++) {
                var definition = (org.w3c.dom.Element) definitions.item(i);
                if (!"immunizationType".equals(definition.getAttribute("name"))) continue;
                var values = definition.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "enumeration");
                for (int j = 0; j < values.getLength(); j++) types.add(((org.w3c.dom.Element) values.item(j)).getAttribute("value"));
            }
        }
        assertThat(types).hasSizeGreaterThan(20);
        types.add("unmapped source type");
        var normalization = new CdsClinicalNormalizationService(new CdsFieldParser());
        var writer = new CdsImmunizationPersister(preventions, extensions, dates, normalization,
                ZoneId.systemDefault().getId(), "classpath:oscar/prevention/PreventionItems.xml", mappings, vaccines);
        var display = PreventionDisplayConfig.getInstance();
        display.loadPreventions();
        for (String type : types) {
            var source = normalization.immunization(CdsClinicalFixture.record(directory, "Immunizations", Map.of(
                    "ImmunizationName", "Synthetic product café", "ImmunizationType", type, "RefusedFlag/ynIndicatorsimple", "N")));
            writer.validate(source);
            var receipt = writer.write(974235, "999998", source);
            int id = Integer.parseInt(receipt.get("preventions"));
            entityManager.flush();
            entityManager.clear();
            var stored = preventions.find(id);
            assertThat(display.getPrevention(stored.getPreventionType())).as("Native page recognizes %s", type).isNotNull();
            assertThat(extensions.getPreventionExt(id)).containsEntry("cdsName", "Synthetic product café").containsEntry("cdsType", type);
        }
    }
}
