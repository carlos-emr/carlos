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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.List;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportPharmacySourcePrivilegeUnitTest {
    @Test void bothPhysicalSourcesRequireExplicitRxAuthorization() {
        var control = mock(CdsExportRunControl.class);
        for (var source : List.<CdsExportNativeSourceReader>of(new CdsExportPharmacySourceReader(mock(PharmacyInfoDao.class)), new CdsExportPharmacyLinkSourceReader(mock(DemographicPharmacyDao.class)))) source.authorizeReadScope(27, control);
        verify(control, times(2)).authorizePharmacy(27);
    }
    @Test void aLegacyControlCannotSilentlySatisfyTheAdditionalNativePrivilege() {
        var legacy = new CdsExportRunControl() { public void checkpoint() { } public void captureCheckpoint() { } public void authorizePatient(int patient) { } public void authorizeCategory(int patient, CdsCategory category, boolean sensitive) { } };
        assertThatThrownBy(() -> new CdsExportPharmacySourceReader(mock(PharmacyInfoDao.class)).authorizeReadScope(27, legacy)).hasMessage("CDS_EXPORT_PHARMACY_AUTHORIZATION_REQUIRED");
        assertThatThrownBy(() -> new CdsExportPharmacyLinkSourceReader(mock(DemographicPharmacyDao.class)).authorizeReadScope(27, legacy)).hasMessage("CDS_EXPORT_PHARMACY_AUTHORIZATION_REQUIRED");
    }
}
