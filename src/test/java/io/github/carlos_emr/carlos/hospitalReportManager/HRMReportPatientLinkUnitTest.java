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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;

/**
 * An HRM report linked to two patients acts for the patient whose chart opened it (#5060).
 *
 * @since 2026-10-19
 */
@DisplayName("HRMReportPatientLink")
@Tag("unit")
class HRMReportPatientLinkUnitTest {

    private static final HRMDocumentToDemographic FIRST = link(3207);
    private static final HRMDocumentToDemographic SECOND = link(3206);

    @Test
    void shouldChooseTheChartsPatient_whenTheReportIsLinkedToThem() {
        assertThat(HRMReportPatientLink.choose(List.of(FIRST, SECOND), "3206")).isSameAs(SECOND);
    }

    @Test
    void shouldChooseTheFirstLink_whenNoPatientIsGiven() {
        assertThat(HRMReportPatientLink.choose(List.of(FIRST, SECOND), null)).isSameAs(FIRST);
    }

    @Test
    void shouldChooseTheFirstLink_whenTheGivenPatientIsNotLinked() {
        assertThat(HRMReportPatientLink.choose(List.of(FIRST, SECOND), "9999")).isSameAs(FIRST);
    }

    @Test
    void shouldChooseTheFirstLink_whenTheGivenValueIsNotAPatientNumber() {
        assertThat(HRMReportPatientLink.choose(List.of(FIRST, SECOND), "3206 OR 1=1")).isSameAs(FIRST);
        assertThat(HRMReportPatientLink.choose(List.of(FIRST, SECOND), "")).isSameAs(FIRST);
        assertThat(HRMReportPatientLink.choose(List.of(FIRST, SECOND), "99999999999")).isSameAs(FIRST);
    }

    @Test
    void shouldChooseNothing_whenTheReportHasNoPatientLink() {
        assertThat(HRMReportPatientLink.choose(List.of(), "3206")).isNull();
        assertThat(HRMReportPatientLink.choose(null, "3206")).isNull();
    }

    private static HRMDocumentToDemographic link(int demographicNo) {
        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setDemographicNo(demographicNo);
        return link;
    }
}
