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
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RxSatelliteClinicAddress wire format")
@Tag("unit")
@Tag("prescription")
class RxSatelliteClinicAddressUnitTest extends CarlosUnitTestBase {

    private static final String[] KEYS = {"multisites", "clinicSatelliteName", "clinicSatelliteAddress",
            "clinicSatelliteCity", "clinicSatelliteProvince", "clinicSatellitePostal", "clinicSatellitePhone",
            "clinicSatelliteFax"};
    private final String[] previous = new String[KEYS.length];
    private SiteDao siteDao;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < KEYS.length; i++) {
            // Raw stored value: getProperty() warns and substitutes a default for a missing key,
            // and this only snapshots state to restore it.
            previous[i] = (String) CarlosProperties.getInstance().get(KEYS[i]);
            CarlosProperties.getInstance().remove(KEYS[i]);
        }
        siteDao = mock(SiteDao.class);
        registerMock(SiteDao.class, siteDao);
    }

    @AfterEach
    void restoreProperties() {
        for (int i = 0; i < KEYS.length; i++) {
            if (previous[i] == null) {
                CarlosProperties.getInstance().remove(KEYS[i]);
            } else {
                CarlosProperties.getInstance().setProperty(KEYS[i], previous[i]);
            }
        }
    }

    @Test
    @DisplayName("should compose the block the servlet parses, encoding the clinic values")
    void shouldComposeBlock_inServletWireShape() {
        String block = RxSatelliteClinicAddress.html("Dr A", "North &amp; Co", "2 North Ave", "Barrie", "ON", "L4M 1A1",
                "7055551111", "7055552222", "Tel", "Fax");

        assertThat(block).isEqualTo("<b>Dr A</b><br>North &amp;amp; Co<br>2 North Ave<br>Barrie, ON L4M 1A1<br>Tel: 7055551111<br>Fax: 7055552222");
        assertThat(RxSatelliteClinicAddress.clinicPart(block)).isEqualTo("<br>North &amp;amp; Co<br>2 North Ave<br>Barrie, ON L4M 1A1<br>Tel: 7055551111<br>Fax: 7055552222");
    }

    @Test
    @DisplayName("should return null for a value that is not a block")
    void shouldReturnNull_forTextWithoutPrescriberName() {
        assertThat(RxSatelliteClinicAddress.clinicPart(null)).isNull();
        assertThat(RxSatelliteClinicAddress.clinicPart("Just a clinic name")).isNull();
    }

    @Test
    @DisplayName("should list the provider's active sites when multisites is on")
    void shouldListActiveSites_whenMultisitesEnabled() {
        CarlosProperties.getInstance().setProperty("multisites", "true");
        Site site = new Site();
        site.setName("North Site");
        site.setAddress("2 North Ave");
        site.setCity("Barrie");
        site.setProvince("ON");
        site.setPostal("L4M 1A1");
        site.setPhone("7055551111");
        site.setFax("7055552222");
        when(siteDao.getActiveSitesByProviderNo("999998")).thenReturn(List.of(site));

        List<String> blocks = RxSatelliteClinicAddress.blocksFor("999998", "Tel", "Fax");

        assertThat(blocks).containsExactly(RxSatelliteClinicAddress.html("", "North Site", "2 North Ave", "Barrie", "ON",
                "L4M 1A1", "7055551111", "7055552222", "Tel", "Fax"));
    }

    @Test
    @DisplayName("should list the clinicSatellite* properties when multisites is off")
    void shouldListPropertySatellites_whenMultisitesDisabled() {
        CarlosProperties.getInstance().setProperty("clinicSatelliteName", "East|West");
        CarlosProperties.getInstance().setProperty("clinicSatelliteAddress", "1 East St|2 West St");
        CarlosProperties.getInstance().setProperty("clinicSatelliteCity", "Ajax|Milton");
        CarlosProperties.getInstance().setProperty("clinicSatelliteProvince", "ON|ON");
        CarlosProperties.getInstance().setProperty("clinicSatellitePostal", "L1S 1A1|L9T 1A1");
        CarlosProperties.getInstance().setProperty("clinicSatellitePhone", "9055550001|9055550002");
        // A shorter list reads as blank instead of failing the whole page.
        CarlosProperties.getInstance().setProperty("clinicSatelliteFax", "9055550003");

        List<String> blocks = RxSatelliteClinicAddress.blocksFor("999998", "Tel", "Fax");

        assertThat(blocks).containsExactly(
                RxSatelliteClinicAddress.html("", "East", "1 East St", "Ajax", "ON", "L1S 1A1", "9055550001", "9055550003", "Tel", "Fax"),
                RxSatelliteClinicAddress.html("", "West", "2 West St", "Milton", "ON", "L9T 1A1", "9055550002", "", "Tel", "Fax"));
    }

    @Test
    @DisplayName("should offer no blocks when neither sites nor satellite properties are configured")
    void shouldOfferNothing_whenNoSatelliteSourceConfigured() {
        assertThat(RxSatelliteClinicAddress.blocksFor("999998", "Tel", "Fax")).isEmpty();
    }
}
