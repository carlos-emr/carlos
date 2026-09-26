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
 *
 * Ported from the openo-beta/Open-O tickler attachment component
 * (PR #2491, Sebastian Ibanez) and adapted for CARLOS.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.TicklerDocs;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TicklerDocsDao}, the data access layer behind the tickler
 * attachment picker (#3984). Covers every finder including the batched
 * {@code findByTicklerIds} used by the tickler list and the reverse finders used by the
 * document and lab viewers, and confirms soft-deleted rows never surface.
 *
 * @since 2026-09-26
 */
@DisplayName("TicklerDocsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("tickler")
@Transactional
class TicklerDocsDaoIntegrationTest extends CarlosTestBase {

    private static final int TICKLER_ID = 5000;
    private static final String PROVIDER_NO = "999998";

    @Autowired
    private TicklerDocsDao ticklerDocsDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private TicklerDocs persistAttachment(int ticklerId, int documentNo, String docType, String labType) {
        TicklerDocs ticklerDocs = new TicklerDocs(ticklerId, documentNo, docType, PROVIDER_NO);
        ticklerDocs.setLabType(labType);
        ticklerDocsDao.persist(ticklerDocs);
        entityManager.flush();
        return ticklerDocs;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @Tag("read")
        @DisplayName("should persist an attachment and read it back by id")
        void shouldPersistAndFindAttachment_whenAttachmentSaved() {
            TicklerDocs saved = persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC, null);

            TicklerDocs found = ticklerDocsDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getTicklerId()).isEqualTo(TICKLER_ID);
            assertThat(found.getDocumentNo()).isEqualTo(11);
            assertThat(found.getDocType()).isEqualTo(TicklerDocs.DOCTYPE_DOC);
            assertThat(found.getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(found.getAttachDate()).isNotNull();
            assertThat(found.getDeleted()).isNull();
        }

        @Test
        @Tag("create")
        @DisplayName("should keep the lab source on a lab attachment")
        void shouldKeepLabType_whenLabAttachmentSaved() {
            TicklerDocs saved = persistAttachment(TICKLER_ID, 22, TicklerDocs.DOCTYPE_LAB, "MDS");

            assertThat(ticklerDocsDao.find(saved.getId()).getLabType()).isEqualTo("MDS");
        }
    }

    @Nested
    @DisplayName("Finders")
    class Finders {

        @Test
        @Tag("read")
        @DisplayName("should return all live attachments for a tickler, oldest first")
        void shouldReturnAllAttachments_whenFindingByTicklerId() {
            persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC, null);
            persistAttachment(TICKLER_ID, 22, TicklerDocs.DOCTYPE_LAB, "HL7");
            persistAttachment(TICKLER_ID + 1, 33, TicklerDocs.DOCTYPE_DOC, null);

            List<TicklerDocs> results = ticklerDocsDao.findByTicklerId(TICKLER_ID);

            assertThat(results).extracting(TicklerDocs::getDocumentNo).containsExactly(11, 22);
        }

        @Test
        @Tag("read")
        @Tag("filter")
        @DisplayName("should filter attachments by type")
        void shouldFilterByDocType_whenFindingByTicklerIdDocType() {
            persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC, null);
            persistAttachment(TICKLER_ID, 22, TicklerDocs.DOCTYPE_LAB, "HL7");

            List<TicklerDocs> docs = ticklerDocsDao.findByTicklerIdDocType(TICKLER_ID, TicklerDocs.DOCTYPE_DOC);

            assertThat(docs).extracting(TicklerDocs::getDocumentNo).containsExactly(11);
        }

        @Test
        @Tag("read")
        @DisplayName("should find one attachment by tickler, item and type")
        void shouldFindSpecificAttachment_whenFindingByTicklerIdDocNoDocType() {
            persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC, null);
            persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_EFORM, null);

            List<TicklerDocs> results = ticklerDocsDao.findByTicklerIdDocNoDocType(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDocType()).isEqualTo(TicklerDocs.DOCTYPE_DOC);
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return attachments for every requested tickler in one query")
        void shouldReturnAttachmentsForEveryTickler_whenFindingByTicklerIds() {
            persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC, null);
            persistAttachment(TICKLER_ID, 22, TicklerDocs.DOCTYPE_LAB, "HL7");
            persistAttachment(TICKLER_ID + 1, 33, TicklerDocs.DOCTYPE_DOC, null);
            // Not requested below, so it must not leak into the results.
            persistAttachment(TICKLER_ID + 2, 44, TicklerDocs.DOCTYPE_DOC, null);

            List<TicklerDocs> results = ticklerDocsDao.findByTicklerIds(List.of(TICKLER_ID, TICKLER_ID + 1));

            assertThat(results).extracting(TicklerDocs::getDocumentNo).containsExactlyInAnyOrder(11, 22, 33);
            assertThat(results).extracting(TicklerDocs::getTicklerId)
                    .containsExactlyInAnyOrder(TICKLER_ID, TICKLER_ID, TICKLER_ID + 1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return an empty list when no tickler ids are requested")
        void shouldReturnEmptyList_whenTicklerIdsAreEmptyOrNull() {
            persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC, null);

            assertThat(ticklerDocsDao.findByTicklerIds(List.of())).isEmpty();
            assertThat(ticklerDocsDao.findByTicklerIds(null)).isEmpty();
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should find the ticklers a document is attached to")
        void shouldFindTicklers_byDocument() {
            persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC, null);
            persistAttachment(TICKLER_ID + 1, 11, TicklerDocs.DOCTYPE_DOC, null);
            // Same number, different type: not this document.
            persistAttachment(TICKLER_ID + 2, 11, TicklerDocs.DOCTYPE_EFORM, null);

            List<TicklerDocs> results = ticklerDocsDao.findByDocument(11, TicklerDocs.DOCTYPE_DOC);

            assertThat(results).extracting(TicklerDocs::getTicklerId).containsExactly(TICKLER_ID, TICKLER_ID + 1);
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should find the ticklers a lab is attached to, keyed by lab source")
        void shouldFindTicklers_byLabAndLabType() {
            persistAttachment(TICKLER_ID, 77, TicklerDocs.DOCTYPE_LAB, "HL7");
            // Same lab number under another source is a different lab.
            persistAttachment(TICKLER_ID + 1, 77, TicklerDocs.DOCTYPE_LAB, "MDS");

            List<TicklerDocs> results = ticklerDocsDao.findByLab(77, "HL7");

            assertThat(results).extracting(TicklerDocs::getTicklerId).containsExactly(TICKLER_ID);
        }
    }

    @Nested
    @DisplayName("Soft delete")
    class SoftDelete {

        @Test
        @Tag("read")
        @Tag("delete")
        @DisplayName("should exclude soft-deleted attachments from every finder")
        void shouldExcludeSoftDeleted_whenAttachmentMarkedDeleted() {
            TicklerDocs attachment = persistAttachment(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC, null);
            TicklerDocs lab = persistAttachment(TICKLER_ID, 77, TicklerDocs.DOCTYPE_LAB, "HL7");
            persistAttachment(TICKLER_ID + 1, 33, TicklerDocs.DOCTYPE_DOC, null);

            attachment.setDeleted(TicklerDocs.DELETED);
            ticklerDocsDao.merge(attachment);
            lab.setDeleted(TicklerDocs.DELETED);
            ticklerDocsDao.merge(lab);
            entityManager.flush();

            assertThat(ticklerDocsDao.findByTicklerId(TICKLER_ID)).isEmpty();
            assertThat(ticklerDocsDao.findByTicklerIdDocType(TICKLER_ID, TicklerDocs.DOCTYPE_DOC)).isEmpty();
            assertThat(ticklerDocsDao.findByTicklerIdDocNoDocType(TICKLER_ID, 11, TicklerDocs.DOCTYPE_DOC)).isEmpty();
            assertThat(ticklerDocsDao.findByDocument(11, TicklerDocs.DOCTYPE_DOC)).isEmpty();
            assertThat(ticklerDocsDao.findByLab(77, "HL7")).isEmpty();
            assertThat(ticklerDocsDao.findByTicklerIds(List.of(TICKLER_ID, TICKLER_ID + 1)))
                    .extracting(TicklerDocs::getDocumentNo).containsExactly(33);
        }
    }
}
