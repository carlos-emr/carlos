/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Exercises Hibernate query execution, including concurrent inserts between keyset pages. */
@Tag("integration") @Tag("dao") @Tag("security") @Transactional
class DocumentDescriptionCandidateIntegrationTest extends CarlosTestBase {
    @Autowired private DocumentDao documents;
    @PersistenceContext(unitName="entityManagerFactory") private EntityManager entityManager;

    private Document create(String description, char status) {
        Document document = new Document();
        document.setDoctype("lab"); document.setDocdesc(description); document.setDocfilename("synthetic.pdf");
        document.setDoccreator("999001"); document.setResponsible("999001"); document.setStatus(status);
        document.setContenttype("application/pdf"); document.setPublic1(0); document.setNumberofpages(1);
        document.setObservationdate(new Date()); document.setUpdatedatetime(new Date());
        document.setContentdatetime(new Date()); entityManager.persist(document); entityManager.flush();
        return document;
    }

    @Test void keysetPagesDoNotCollapseDuplicateDescriptionsOrReplayAfterNewInsert() {
        String prefix = "OWNED_DESC_CANDIDATE_";
        Document oldest = create(prefix+"same", 'A');
        Document middle = create(prefix+"same", 'A');
        Document newest = create(prefix+"newest", 'A');
        create(prefix+"deleted", 'D');
        List<Document> first = documents.findDocumentDescriptionCandidates(prefix+"%", null, 2);
        assertThat(first).extracting(Document::getDocumentNo)
                .containsExactly(newest.getDocumentNo(), middle.getDocumentNo());
        create(prefix+"inserted-between-pages", 'A');
        List<Document> next = documents.findDocumentDescriptionCandidates(prefix+"%", middle.getDocumentNo(), 2);
        assertThat(next).extracting(Document::getDocumentNo).containsExactly(oldest.getDocumentNo());
    }

    @Test void quotedSearchTextIsBoundAsDataAndDoesNotWidenTheQuery() {
        Document exact = create("OWNED_DESC_' OR 1=1 --", 'A');
        create("OWNED_DESC_unrelated", 'A');
        assertThat(documents.findDocumentDescriptionCandidates("%OWNED_DESC_' OR 1=1 --%", null, 20))
                .extracting(Document::getDocumentNo).containsExactly(exact.getDocumentNo());
    }
}
