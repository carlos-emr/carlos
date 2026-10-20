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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The print preview renders the stored prescription outline through
 * {@link RxPrescriptionData#fullOutLineToHtml(String)}: once encoded, one line per outline part (#3952).
 *
 * @since 2026-10-08
 */
@DisplayName("RxPrescriptionData outline HTML rendering")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxPrescriptionDataFullOutLineHtmlUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should break the outline into lines at each semicolon")
    void shouldJoinLinesWithBreaks_forPlainOutline() {
        assertThat(RxPrescriptionData.fullOutLineToHtml("AMOXIL 250 CAP; 1 cap PO TID; Qty:30 Repeats:0"))
                .isEqualTo("AMOXIL 250 CAP<br /> 1 cap PO TID<br /> Qty:30 Repeats:0");
    }

    @Test
    @DisplayName("should show a quoted drug name as entered, with no backslash")
    void shouldShowQuotesAsEntered_withoutBackslashes() {
        String outline = RxPrescriptionData.getFullOutLine(
                "CHILDREN'S \"JUNIOR\" SYRUP\n5 mL PO daily\nQty:100 mL Repeats:0");

        assertThat(RxPrescriptionData.fullOutLineToHtml(outline))
                .isEqualTo("CHILDREN'S \"JUNIOR\" SYRUP<br /> 5 mL PO daily<br /> Qty:100 mL Repeats:0")
                .doesNotContain("\\");
    }

    @Test
    @DisplayName("should keep an encoded ampersand whole rather than splitting it at its semicolon")
    void shouldKeepEntityWhole_forAmpersandInName() {
        assertThat(RxPrescriptionData.fullOutLineToHtml("SALT & SODA GARGLE; gargle BID"))
                .isEqualTo("SALT &amp; SODA GARGLE<br /> gargle BID")
                .doesNotContain("&amp<br />");
    }

    @Test
    @DisplayName("should render markup in a stored name as text")
    void shouldEncodeMarkup_inStoredName() {
        assertThat(RxPrescriptionData.fullOutLineToHtml("<img src=x onerror=alert(1)>; take as directed"))
                .isEqualTo("&lt;img src=x onerror=alert(1)&gt;<br /> take as directed");
    }

    @Test
    @DisplayName("should render an absent outline as empty")
    void shouldReturnEmpty_forNullOutline() {
        assertThat(RxPrescriptionData.fullOutLineToHtml(null)).isEmpty();
    }
}
