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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationCompatibilityException;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.managers.RxManagerImpl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DrugConverterImpl;
import io.github.carlos_emr.carlos.webserv.rest.util.CdsMedicationCompatibilityExceptionMapper;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DrugTransfer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsMedicationCompatibilityUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"CURRENT", "HISTORICAL", "NEW", "SUSPENDED", "UNKNOWN"})
    void legacyProtocolsRefuseEveryImportedStateWithoutReturningAnchorsOrGuessedFlags(String status) {
        Drug source = medication(7);
        source.setCdsCurrentness(status);
        source.setCdsDoseText("1/2");
        source.setNoSubs((Boolean) null);
        assertThrows(CdsMedicationCompatibilityException.class,
                () -> new DrugConverterImpl().getAsTransferObject(null, source));
        assertThrows(CdsMedicationCompatibilityException.class, () -> DrugTransfer.toTransfer(source));
    }

    @Test
    void refusalIs409WithSafeActionableCode() {
        var response = new CdsMedicationCompatibilityExceptionMapper()
                .toResponse(new CdsMedicationCompatibilityException());
        assertEquals(409, response.getStatus());
        assertTrue(response.getEntity().toString().contains(CdsMedicationCompatibilityException.CODE));
        assertFalse(response.getEntity().toString().contains("7"));
    }

    @Test
    void nativeNullableNoSubstitutionStillUsesEstablishedBooleanTransferShape() {
        Drug source = medication(7);
        source.setNoSubs((Boolean) null);
        var transfer = DrugTransfer.toTransfer(source);
        assertFalse(transfer.isNoSubs());
        assertEquals(source.getBrandName(), transfer.getBrandName());
    }

    @Test
    void updateCannotStripImportedMetadataThenArchiveSource() {
        Drug source = medication(7);
        source.setCdsCurrentness("CURRENT");
        Drug replacement = medication(7); // legacy DTO has no CDS metadata
        DrugDao dao = mock(DrugDao.class);
        when(dao.find((Object) Integer.valueOf(7))).thenReturn(source);
        RxManagerImpl manager = manager(dao, mock(PrescriptionManager.class));
        assertThrows(CdsMedicationCompatibilityException.class,
                () -> manager.updateDrug(new LoggedInInfo(), replacement));
        verify(dao, never()).addNewDrug(any());
        verify(dao, never()).merge(any());
        assertFalse(source.isArchived());
    }

    @Test
    void mixedPrescribeBatchChecksEverySourceBeforeCreatingAnything() {
        Drug nativeSource = medication(6);
        Drug importedSource = medication(7);
        importedSource.setCdsCurrentness("CURRENT");
        DrugDao dao = mock(DrugDao.class);
        when(dao.find((Object) Integer.valueOf(6))).thenReturn(nativeSource);
        when(dao.find((Object) Integer.valueOf(7))).thenReturn(importedSource);
        PrescriptionManager prescriptions = mock(PrescriptionManager.class);
        RxManagerImpl manager = manager(dao, prescriptions);
        assertThrows(CdsMedicationCompatibilityException.class,
                () -> manager.prescribe(new LoggedInInfo(), List.of(medication(6), medication(7)), 1));
        verifyNoInteractions(prescriptions);
        verify(dao, never()).addNewDrug(any());
        verify(dao, never()).merge(any());
        assertEquals(0, nativeSource.getScriptNo());
        assertFalse(nativeSource.isArchived());
    }

    private RxManagerImpl manager(DrugDao dao, PrescriptionManager prescriptions) {
        RxManagerImpl manager = new RxManagerImpl() {
            @Override protected void writeCheck(LoggedInInfo info, int demographicNo) { }
            @Override protected Boolean canPrescribe(Drug drug) { return true; }
        };
        ReflectionTestUtils.setField(manager, "drugDao", dao);
        ReflectionTestUtils.setField(manager, "prescriptionManager", prescriptions);
        return manager;
    }

    private Drug medication(int id) {
        Drug source = new Drug();
        source.setId(id);
        source.setDemographicId(1);
        source.setBrandName("Source product");
        return source;
    }
}
