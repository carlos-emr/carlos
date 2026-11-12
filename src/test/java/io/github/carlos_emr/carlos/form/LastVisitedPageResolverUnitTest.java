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
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A saved Mental Health or Rourke 2006 record reopens on a page that exists (issue #4595).
 *
 * <p>The pages posted their {@code c_lastVisited} with the slash of an unquoted attribute's
 * {@code />}, and the reopen wrapper built the JSP name from the stored value, so every saved
 * record opened HTTP 500. Rows saved before the fix keep the slash, and the value comes back from
 * the database into a dispatcher path, so it is matched against the form's page names.</p>
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("form")
@DisplayName("LastVisitedPageResolver")
class LastVisitedPageResolverUnitTest {

    private static final String[] MENTAL_HEALTH = {"referral", "assessment", "outcome"};
    private static final String[] ROURKE_2006 = {"p1", "p2", "p3", "p4"};

    @ParameterizedTest
    @ValueSource(strings = {"referral", "assessment", "outcome"})
    @DisplayName("should keep a stored Mental Health page that exists")
    void shouldKeepStoredPage_whenItNamesAMentalHealthPage(String stored) {
        assertThat(LastVisitedPageResolver.resolve(stored, MENTAL_HEALTH)).isEqualTo(stored);
    }

    @ParameterizedTest
    @CsvSource({"referral/,referral", "assessment/,assessment", "outcome/,outcome", "' outcome/ ',outcome",
            "outcome//,outcome"})
    @DisplayName("should repair a Mental Health page saved with the attribute's slash")
    void shouldStripSlash_fromRowsSavedBeforeTheFix(String stored, String page) {
        assertThat(LastVisitedPageResolver.resolve(stored, MENTAL_HEALTH)).isEqualTo(page);
    }

    @ParameterizedTest
    @CsvSource({"p1/,p1", "p2,p2", "p3/,p3", "p4/,p4"})
    @DisplayName("should repair a Rourke 2006 page saved in its char(3) column with the slash")
    void shouldResolveRourke2006Page_fromStoredValue(String stored, String page) {
        assertThat(LastVisitedPageResolver.resolve(stored, ROURKE_2006)).isEqualTo(page);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Referral", "/", "../formmhreferral", "referral/../../x", "p5", "intake", " "})
    @DisplayName("should open the first page for a value that names no page of the form")
    void shouldOpenFirstPage_whenStoredValueIsUnknown(String stored) {
        assertThat(LastVisitedPageResolver.resolve(stored, MENTAL_HEALTH)).isEqualTo("referral");
    }

    @Test
    @DisplayName("should refuse a form with no pages")
    void shouldThrow_whenNoPagesAreGiven() {
        assertThatThrownBy(() -> LastVisitedPageResolver.resolve("referral"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
