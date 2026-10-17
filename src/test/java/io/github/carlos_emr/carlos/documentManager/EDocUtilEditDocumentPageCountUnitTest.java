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
package io.github.carlos_emr.carlos.documentManager;

import java.lang.reflect.Field;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #4817: replacing a document's file from its Edit popup. {@link EDocUtil#editDocumentSQL} copied
 * the new file's name, content type and content date to the document row, but not its page count, so
 * the inbox view, the document display and Split kept showing the old file's pages.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("document")
@DisplayName("EDocUtil.editDocumentSQL page count on a file replacement (#4817)")
class EDocUtilEditDocumentPageCountUnitTest extends CarlosUnitTestBase {

    private static final int DOCUMENT_NO = 7;

    private final DocumentDao documentDao = mock(DocumentDao.class);
    private final Document stored = new Document();
    private Field cachedDocumentDao;
    private Object previousDocumentDao;

    @BeforeEach
    void setUp() throws Exception {
        // EDocUtil caches its DAO in a static field the first time any caller asks, so an earlier test's
        // DAO may be there already. Put this test's mock in and restore the previous one afterwards.
        cachedDocumentDao = EDocUtil.class.getDeclaredField("documentDao");
        cachedDocumentDao.setAccessible(true);
        previousDocumentDao = cachedDocumentDao.get(null);
        cachedDocumentDao.set(null, documentDao);

        stored.setDocumentNo(DOCUMENT_NO);
        stored.setDocfilename("20261016120000FAKE-original.pdf");
        stored.setContenttype("application/pdf");
        stored.setNumberofpages(1);
        // editDocumentSQL looks the row up with an Integer, which is the find(Object) overload.
        when(documentDao.find((Object) Integer.valueOf(DOCUMENT_NO))).thenReturn(stored);
    }

    @AfterEach
    void tearDown() throws Exception {
        cachedDocumentDao.set(null, previousDocumentDao);
    }

    /** The document as the Edit popup's save builds it, with a file name only when a file was chosen. */
    private static EDoc edited(String fileName) {
        EDoc edoc = new EDoc("FAKE-document", "others", fileName, "", "999998", "999998", "", 'A',
                "2026-10-17", "", "", "demographic", "1");
        edoc.setDocId(String.valueOf(DOCUMENT_NO));
        edoc.setReceivedDate("2026-10-17");
        return edoc;
    }

    private Document merged() {
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentDao).merge(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("should store the replacement's page count when a new file is chosen")
    void shouldStoreReplacementPageCount_whenFileIsReplaced() {
        EDoc edoc = edited("FAKE-R.pdf");
        edoc.setContentType("application/pdf");
        edoc.setNumberOfPages(3);

        EDocUtil.editDocumentSQL(edoc, false);

        Document saved = merged();
        assertThat(saved.getDocfilename()).isEqualTo(edoc.getFileName());
        assertThat(saved.getNumberofpages()).isEqualTo(3);
    }

    @Test
    @DisplayName("should keep the stored page count when no new file is chosen")
    void shouldKeepPageCount_whenNoFileIsChosen() {
        EDocUtil.editDocumentSQL(edited(""), false);

        Document saved = merged();
        assertThat(saved.getDocfilename()).isEqualTo("20261016120000FAKE-original.pdf");
        assertThat(saved.getNumberofpages()).isEqualTo(1);
    }
}
