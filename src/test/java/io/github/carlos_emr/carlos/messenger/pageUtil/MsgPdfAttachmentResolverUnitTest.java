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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.util.Date;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.messenger.pageUtil.MsgPdfAttachmentResolver.Attachment;
import io.github.carlos_emr.carlos.messenger.pageUtil.MsgPdfAttachmentResolver.Item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MsgPdfAttachmentResolver")
@Tag("unit")
@Tag("messenger")
class MsgPdfAttachmentResolverUnitTest {

    private static final ResourceBundle LABELS = new ListResourceBundle() {
        @Override
        protected Object[][] getContents() {
            return new Object[][]{
                    {"messenger.generatePreviewPDF.information", "Information"},
                    {"messenger.generatePreviewPDF.encounter", "Encounter:"},
                    {"messenger.generatePreviewPDF.currentPrescriptions", "Current Prescriptions"}};
        }
    };

    private final EChartDao eChartDao = mock(EChartDao.class);
    private final MsgPdfAttachmentResolver resolver = new MsgPdfAttachmentResolver(eChartDao);

    @Test
    @DisplayName("should map each item key to its fixed internal route for the validated patient")
    void shouldResolveFixedRoutes_forEachItem() {
        EChart chart = new EChart();
        chart.setId(9);
        chart.setTimestamp(new Date(0));
        when(eChartDao.getLatestChart(12)).thenReturn(chart);

        assertThat(resolver.available(12, "FAKE-Doe, Pat", LABELS))
                .extracting(Attachment::route)
                .containsExactly(
                        "/demographic/DemographicPdfLabel?demographic_no=12",
                        "/encounter/ViewEcharthistoryprint?echartid=9&demographic_no=12",
                        "/rx/ViewPrintDrugProfile2?demographic_no=12");
    }

    @Test
    @DisplayName("should compute titles on the server from the chooser labels")
    void shouldComputeTitles_fromLabels() {
        assertThat(resolver.resolve(Item.DEMOGRAPHIC, 12, "FAKE-Doe, Pat", LABELS))
                .get().extracting(Attachment::title).isEqualTo("FAKE-Doe, Pat Information");
        assertThat(resolver.resolve(Item.PRESCRIPTIONS, 12, "", LABELS))
                .get().extracting(Attachment::title).isEqualTo("Current Prescriptions");
    }

    @Test
    @DisplayName("should drop angle brackets that would split the stored <TITLE> record")
    void shouldStripAngleBrackets_fromTitles() {
        assertThat(resolver.resolve(Item.DEMOGRAPHIC, 12, "FAKE</TITLE><CONTENT>x", LABELS))
                .get().extracting(Attachment::title).asString()
                .doesNotContain("<").doesNotContain(">");
    }

    @Test
    @DisplayName("should offer no encounter item when the patient has no encounter record")
    void shouldOmitEncounter_whenNoChart() {
        when(eChartDao.getLatestChart(12)).thenReturn(null);

        assertThat(resolver.resolve(Item.ENCOUNTER, 12, "", LABELS)).isEmpty();
        assertThat(resolver.available(12, "", LABELS)).extracting(Attachment::item)
                .containsExactly(Item.DEMOGRAPHIC, Item.PRESCRIPTIONS);
    }

    @Test
    @DisplayName("should resolve nothing for a non-positive patient or an unknown key")
    void shouldResolveNothing_forInvalidInput() {
        assertThat(resolver.resolve(Item.DEMOGRAPHIC, 0, "", LABELS)).isEmpty();
        assertThat(resolver.resolve(null, 12, "", LABELS)).isEmpty();
        assertThat(Item.fromKey("../admin")).isEmpty();
        assertThat(Item.fromKey(null)).isEmpty();
        assertThat(Item.fromKey("prescriptions")).contains(Item.PRESCRIPTIONS);
    }

    @Test
    @DisplayName("should require read on the module each item's page belongs to")
    void shouldNameSecurityObject_forEachItem() {
        assertThat(Item.DEMOGRAPHIC.securityObject()).isEqualTo("_demographic");
        assertThat(Item.ENCOUNTER.securityObject()).isEqualTo("_eChart");
        assertThat(Item.PRESCRIPTIONS.securityObject()).isEqualTo("_rx");
    }
}
