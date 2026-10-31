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
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DocumentTransfer;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real local CXF SOAP fault/serialization boundary, including refusal before any report file read. */
@Tag("unit")
class CdsDocumentWsUnitTest extends CarlosSoapTestBase {
    @Mock private DocumentManager documents;
    @Override protected Object getServiceBean() {
        var service=new DocumentWs(); injectDependency(service,"documentManager",documents); return service;
    }
    @Override protected Class<?> getServiceInterface() { return DocumentWs.class; }

    @Test
    void shouldFaultBeforeBinaryFileLoading_whenImportedReportUsesTheSingleDocumentEndpoint() throws Exception {
        Document imported=spy(new Document(123)); imported.setCdsContentPrecision("UNKNOWN");
        when(documents.getDocument(any(LoggedInInfo.class),eq(123))).thenReturn(imported);
        assertThatThrownBy(()->createClient(DocumentWs.class).getDocument(123))
                .hasMessageContaining("legacy export cannot represent");
        verify(imported,never()).getDocumentFileContentsAsBytes();
    }

    @Test
    void shouldFaultBeforeLoadingAnySelectedBinary_whenAnIncrementalPageContainsAnImportedReport() throws Exception {
        Document ordinary=spy(new Document(1)); Document imported=spy(new Document(2)); imported.setCdsContentPrecision("YEAR");
        when(documents.getDocumentsUpdateAfterDate(any(LoggedInInfo.class),any(Date.class),eq(10)))
                .thenReturn(List.of(ordinary,imported));
        assertThatThrownBy(()->createClient(DocumentWs.class).getDocumentsUpdateAfterDate(new Date(),10))
                .hasMessageContaining("legacy export cannot represent");
        verify(ordinary,never()).getDocumentFileContentsAsBytes(); verify(imported,never()).getDocumentFileContentsAsBytes();
    }

    @Test
    void shouldReturnAnOrdinaryNativeDocument_whenTheSourcePrecisionMarkerIsAbsent() throws Exception {
        Document ordinary=spy(new Document(123)); ordinary.setContentdatetime(new Date(123000));
        doReturn(new byte[]{1,2,3}).when(ordinary).getDocumentFileContentsAsBytes();
        when(documents.getDocument(any(LoggedInInfo.class),eq(123))).thenReturn(ordinary);
        DocumentTransfer transfer=createClient(DocumentWs.class).getDocument(123);
        assertThat(transfer.getDocumentNo()).isEqualTo(123); assertThat(transfer.getFileContents()).containsExactly(1,2,3);
        assertThat(transfer.getCdsContentPrecision()).isNull(); assertThat(transfer.getContentdatetime()).isEqualTo(new Date(123000));
    }
}
