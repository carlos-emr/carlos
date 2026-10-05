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
package io.github.carlos_emr.carlos.form.pdfservlet;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@Tag("unit")
@Tag("prescription")
class PrescriptionFaxServiceUnitTest extends PrescriptionPdfUnitTestBase {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("should reserve spool artifacts before publishing the sender-visible document")
    void shouldReserveSpoolArtifacts_whenPublishingDocument(boolean sharedDirectory, @TempDir Path tempDir) throws Exception {
        String previousFaxFileLocation = CarlosProperties.getInstance().getProperty("fax_file_location");
        Path documentDir = Files.createDirectory(tempDir.resolve("documents"));
        Path faxDir = sharedDirectory ? documentDir : Files.createDirectory(tempDir.resolve("fax"));
        Path documentPdf = documentDir.resolve("prescription_rx-123.pdf");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream() {
            @Override
            public synchronized void writeTo(java.io.OutputStream output) throws java.io.IOException {
                // Observe the publication boundary itself, not just final cleanup. The
                // background sender can open DOCUMENT_DIR as soon as this path exists.
                if (Files.exists(documentPdf)) {
                    assertThat(faxDir.resolve("prescription_rx-123.txt")).hasContent("4165551212");
                    assertThat(faxDir.resolve("prescription_rx-123.pdf")).exists();
                }
                super.writeTo(output);
            }
        };
        bytes.write("test pdf".getBytes(StandardCharsets.UTF_8));
        try {
            CarlosProperties.getInstance().setProperty("fax_file_location", faxDir.toString());
            newFaxService().prepareFaxFiles(documentDir.toString(), "rx-123", "prescription_rx-123.pdf",
                    "4165551212", bytes);
            assertThat(documentPdf).hasContent("test pdf");
        } finally {
            restoreProperty("fax_file_location", previousFaxFileLocation);
        }
    }

    @Test
    @DisplayName("should defer to the signature gate when the fax permission check throws")
    void shouldNotReportPermissionDenial_whenPrivilegeCheckThrows() {
        // Answering "denied" here would emit the specific permission wording, which under a directive
        // confirms the script exists. Answering "not denied" is not an authorization: resolveSignature
        // Image withholds the signature on the same failure, so the fax still refuses — as "not signed",
        // the same answer a non-existent script id gives.
        Prescription prescription = new Prescription();
        prescription.setDemographicId(DEMOGRAPHIC_NO);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), anyString()))
                .thenThrow(new PatientDirectiveException("directive blocks this chart"));
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        assertThat(newFaxService().isFaxDeniedByPrivilege(prescription, loggedInInfo)).isFalse();
    }

    @Test
    @DisplayName("should let an unexpected privilege-lookup failure abort the fax instead of deferring it")
    void shouldPropagateFailure_whenPrivilegeLookupFailsUnexpectedly() throws Exception {
        stubStoredSignature();
        Prescription prescription = prescriptionDao.find(SCRIPT_ID);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.READ), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenThrow(new IllegalStateException("datasource down"));

        assertThatThrownBy(() -> newFaxService().isFaxDeniedByPrivilege(prescription, mock(LoggedInInfo.class)))
                .isInstanceOf(IllegalStateException.class);
    }
}
