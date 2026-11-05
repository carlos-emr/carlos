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
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Both HTTP print callers receive bytes only after complete PDF assembly. */
@Tag("unit")
class CaseManagementPrintAssemblyUnitTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldStageAndCleanPdf_whenAssemblySucceedsOrFails(boolean fail) throws Exception {
        var stagedFile = new AtomicReference<File>();
        var response = new ByteArrayOutputStream();
        List<Object> inputs = List.of("synthetic.pdf");
        byte[] pdf = "%PDF-synthetic-completed-document".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (var paths = mockStatic(PathValidationUtils.class, invocation -> {
                 Object result = invocation.callRealMethod();
                 if ("createSecureTempFile".equals(invocation.getMethod().getName())) {
                     File file = (File) result;
                     stagedFile.set(file);
                     if (Files.getFileStore(file.toPath()).supportsFileAttributeView("posix")) {
                         assertThat(Files.getPosixFilePermissions(file.toPath())).containsExactlyInAnyOrder(
                                 PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                     }
                 }
                 return result;
             });
             var merger = mockStatic(ConcatPDF.class)) {
            merger.when(() -> ConcatPDF.concatRequired(eq(inputs), any(OutputStream.class)))
                    .thenAnswer(invocation -> {
                        OutputStream output = invocation.getArgument(1);
                        output.write(pdf);
                        output.flush();
                        assertThat(response.size()).isZero();
                        if (fail) throw new IllegalStateException("Synthetic late assembly failure");
                        return null;
                    });
            if (fail) {
                assertThatIllegalStateException().isThrownBy(() -> CaseManagementPrint.mergeCompletePdf(inputs, response));
                assertThat(response.size()).isZero();
            } else {
                CaseManagementPrint.mergeCompletePdf(inputs, response);
                assertThat(response.toByteArray()).isEqualTo(pdf);
            }
            assertThat(stagedFile.get()).isNotNull().doesNotExist();
            paths.verify(() -> PathValidationUtils.createSecureTempFile("chart-print-complete-", ".pdf"));
            merger.verify(() -> ConcatPDF.concatRequired(eq(inputs), any(OutputStream.class)));
        }
    }
}
