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

import java.util.Properties;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * BC-AR 2020 page 2 carries {@code <base href="<context>/">}, so the prenatal visit rows' calendar image, written as
 * "../images/cal.gif", was requested outside the application (issue #4919).
 *
 * @since 2026-10-18
 */
@DisplayName("BC-AR 2020 prenatal visit row")
@Tag("unit")
@Tag("form")
class FrmBCAR2020RecordVisitRowUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerRecordBeans() {
        // FrmRecord's constructor looks these up; building a visit row uses neither.
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
    }

    @Test
    void shouldAddressTheCalendarImage_relativeToThePageBase() {
        String row = new FrmBCAR2020Record().createPrenatalVisitRow(new Properties(), "1");

        assertThat(row)
                .contains("<img src=\"images/cal.gif\" id=\"d_prenatalVisitDate1_cal\">")
                .doesNotContain("../images/");
    }
}
