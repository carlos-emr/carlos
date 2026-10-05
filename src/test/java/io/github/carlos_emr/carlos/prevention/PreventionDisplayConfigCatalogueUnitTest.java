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
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMappingDao;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;

/**
 * The prevention list without legacy immunizations (NVCpreventionItems.xml) is opt-in: CARLOS loads it
 * only when PREVENTION_ITEMS names it, never just because a vaccine catalogue is loaded, and the file
 * stays PreventionItems.xml without its immunizations.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Prevention list with the vaccine catalogue")
class PreventionDisplayConfigCatalogueUnitTest extends CarlosUnitTestBase {

    private static final String IMMUNIZATIONS = "Immunizations";
    private static final String PREVENTION_ITEMS = "oscar/prevention/PreventionItems.xml";
    private static final String CATALOGUE_PREVENTION_ITEMS = "oscar/prevention/NVCpreventionItems.xml";

    private final CanadianVaccineCatalogueManager catalogue = mock(CanadianVaccineCatalogueManager.class);
    private CanadianVaccineCatalogueManager savedCatalogue;
    private CVCMappingDao savedMapping;
    private CVCImmunizationDao savedImmunizations;
    private String savedItems;

    @BeforeEach
    void setUp() {
        registerMock(CVCImmunizationDao.class, mock(CVCImmunizationDao.class));
        registerMock(CanadianVaccineCatalogueManager.class, catalogue);
        registerMock(CVCMappingDao.class, mock(CVCMappingDao.class));
        savedCatalogue = PreventionDisplayConfig.cvcManager;
        savedMapping = PreventionDisplayConfig.cvcMapping;
        savedImmunizations = PreventionDisplayConfig.cvcImmunizationDao;
        PreventionDisplayConfig.cvcManager = catalogue;
        PreventionDisplayConfig.cvcMapping = mock(CVCMappingDao.class);
        PreventionDisplayConfig.cvcImmunizationDao = mock(CVCImmunizationDao.class);
        savedItems = CarlosProperties.getInstance().getProperty("PREVENTION_ITEMS");
        CarlosProperties.getInstance().remove("PREVENTION_ITEMS");
    }

    @AfterEach
    void restore() {
        PreventionDisplayConfig.cvcManager = savedCatalogue;
        PreventionDisplayConfig.cvcMapping = savedMapping;
        PreventionDisplayConfig.cvcImmunizationDao = savedImmunizations;
        if (savedItems == null) {
            CarlosProperties.getInstance().remove("PREVENTION_ITEMS");
        } else {
            CarlosProperties.getInstance().setProperty("PREVENTION_ITEMS", savedItems);
        }
    }

    @Test
    @DisplayName("should keep the full list, legacy immunizations included, even with a catalogue loaded")
    void shouldLoadFullList_whenPreventionItemsIsNotSet() throws Exception {
        when(catalogue.isCatalogueOn()).thenReturn(true);
        when(catalogue.hasCatalogue()).thenReturn(true);

        assertThat(load()).extracting(h -> h.get("name")).contains("PAP", "LDCT", "Inf", "H1N1");
        verify(catalogue, never()).isCatalogueOn();
        verify(catalogue, never()).hasCatalogue();
    }

    @Test
    @DisplayName("should load the list without legacy immunizations only when PREVENTION_ITEMS names it")
    void shouldLoadCatalogueList_whenPreventionItemsNamesIt() throws Exception {
        CarlosProperties.getInstance().setProperty("PREVENTION_ITEMS", "classpath:" + CATALOGUE_PREVENTION_ITEMS);

        List<HashMap<String, String>> loaded = load();

        assertThat(loaded).extracting(h -> h.get("name")).contains("PAP", "LDCT", "OtherA").doesNotContain("Inf", "H1N1");
        assertThat(loaded).noneMatch(h -> IMMUNIZATIONS.equals(h.get("headingName")));
    }

    @Test
    @DisplayName("should keep the catalogue list identical to PreventionItems.xml without its immunizations")
    void shouldMatchPreventionItems_whenImmunizationsAreLeftOut() throws Exception {
        List<Map<String, String>> expected = new ArrayList<>();
        for (Map<String, String> item : items(PREVENTION_ITEMS)) {
            if (!IMMUNIZATIONS.equals(item.get("headingName"))) {
                expected.add(item);
            }
        }

        assertThat(items(CATALOGUE_PREVENTION_ITEMS)).containsExactlyElementsOf(expected);
    }

    private List<HashMap<String, String>> load() throws Exception {
        Constructor<PreventionDisplayConfig> constructor = PreventionDisplayConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        PreventionDisplayConfig config = constructor.newInstance();
        config.loadPreventions();
        return config.getPreventions();
    }

    private static List<Map<String, String>> items(String resource) throws Exception {
        try (InputStream in = PreventionDisplayConfigCatalogueUnitTest.class.getClassLoader().getResourceAsStream(resource)) {
            List<Map<String, String>> items = new ArrayList<>();
            for (Element item : XmlUtils.createSecureSAXBuilder().build(in).getRootElement().getChildren("item")) {
                Map<String, String> attributes = new LinkedHashMap<>();
                for (Attribute attribute : item.getAttributes()) {
                    attributes.put(attribute.getName(), attribute.getValue());
                }
                items.add(attributes);
            }
            return items;
        }
    }
}
