/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.util.Map;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.RxSourceVersionValidator;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RxSaveReceiptCommitIntegrationTest.Config.class)
@Tag("integration") @Tag("mariadb-native") @Tag("prescription")
class RxConcurrencyIntegrationTest extends CarlosUnitTestBase {
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private RxSourceVersionValidator concurrency;
    @PersistenceContext private EntityManager em;
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }

    @Test void shouldPreserveFirstEdit_whenTwoDetachedDrugSnapshotsAreMerged() {
        Drug saved = tx().execute(status -> {
            var drug = new Drug(); drug.setDemographicId(1001); drug.setProviderNo("999998"); drug.setDrugForm("Tablet");
            drug.setFreqCode("OD"); em.persist(drug); return drug;
        });
        Drug first = tx().execute(status -> em.find(Drug.class, saved.getId()));
        Drug stale = tx().execute(status -> em.find(Drug.class, saved.getId()));
        assertThat(first.getRowVersion()).isEqualTo(stale.getRowVersion());
        first.setDrugForm("Capsule"); tx().executeWithoutResult(status -> em.merge(first));
        stale.setFreqCode("BID");
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> em.merge(stale)))
                .isInstanceOfAny(org.springframework.dao.OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class);
        Drug actual = tx().execute(status -> em.find(Drug.class, saved.getId()));
        assertThat(actual.getDrugForm()).isEqualTo("Capsule");
        assertThat(actual.getFreqCode()).isEqualTo("OD");
    }

    @Test void shouldRefuseChangedSourceAndStaleForm_beforeReplacingMedication() {
        Drug saved = tx().execute(status -> {
            var drug = new Drug(); drug.setDemographicId(1001); drug.setProviderNo("999998"); em.persist(drug); return drug;
        });
        long original = saved.getRowVersion();
        long current = concurrency.updateForm("999998", 1001, saved.getId(), original, "Tablet");
        assertThat(current).isGreaterThan(original);
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> concurrency.checkSources(1001, Map.of(saved.getId(), original))))
                .isInstanceOf(RxSourceVersionValidator.Conflict.class);
        assertThatThrownBy(() -> concurrency.updateForm("999998", 1001, saved.getId(), original, "Stale"))
                .isInstanceOf(RxSourceVersionValidator.Conflict.class);
        assertThatThrownBy(() -> concurrency.updateForm("999998", 1002, saved.getId(), current, "Foreign"))
                .isInstanceOf(RxSourceVersionValidator.Conflict.class);
        String actualForm = tx().execute(status -> em.find(Drug.class, saved.getId()).getDrugForm());
        assertThat(actualForm).isEqualTo("Tablet");
    }

    @Test void shouldPreserveFirstNotesEdit_andAcknowledgeItsExactRetry() {
        Prescription saved = tx().execute(status -> {
            var header = new Prescription(); header.setDemographicId(1001); header.setProviderNo("999998");
            header.setComments("Original"); em.persist(header); return header;
        });
        long updated = concurrency.updateComment("999998", 1001, saved.getId(), null, "Original", true, "First edit");
        assertThatThrownBy(() -> concurrency.updateComment("999998", 1001, saved.getId(), null, "Original", true, "Stale edit"))
                .isInstanceOf(RxSourceVersionValidator.Conflict.class);
        assertThat(concurrency.updateComment("999998", 1001, saved.getId(), null, "Original", true, "First edit")).isEqualTo(updated);
        String actualComment = tx().execute(status -> em.find(Prescription.class, saved.getId()).getComments());
        assertThat(actualComment).isEqualTo("First edit");
    }
    @Test void shouldAcceptFirstNotes_whenTheDisplayedBlankWasStoredAsNull() {
        Prescription saved = tx().execute(status -> {
            var header = new Prescription(); header.setDemographicId(1001); header.setProviderNo("999998");
            header.setComments(null); em.persist(header); return header;
        });
        assertThat(concurrency.updateComment("999998", 1001, saved.getId(), null, "", true, ""))
                .isEqualTo(saved.getRowVersion());
        long updated = concurrency.updateComment("999998", 1001, saved.getId(), null, "", true, "First notes");
        assertThat(updated).isGreaterThan(saved.getRowVersion());
        assertThatThrownBy(() -> concurrency.updateComment("999998", 1001, saved.getId(), null, "", true, "Stale notes"))
                .isInstanceOf(RxSourceVersionValidator.Conflict.class);
        assertThat(concurrency.updateComment("999998", 1001, saved.getId(), null, "", true, "First notes")).isEqualTo(updated);
        String stored = tx().execute(status -> em.find(Prescription.class, saved.getId()).getComments());
        assertThat(stored).isEqualTo("First notes");
    }

    @Test
    void shouldCompareNoteDigest_andAcknowledgeExactRetryWithoutOriginalText() {
        Prescription saved = tx().execute(status -> {
            var header = new Prescription(); header.setDemographicId(1001); header.setProviderNo("999998");
            header.setComments("Original clinical prose"); em.persist(header); return header;
        });
        String digest = RxSourceVersionValidator.commentDigest("Original clinical prose");
        long version = concurrency.updateCommentDigest("999998", 1001, saved.getId(), null, digest, "First edit");
        assertThat(concurrency.updateCommentDigest("999998", 1001, saved.getId(), null, digest, "First edit")).isEqualTo(version);
        assertThatThrownBy(() -> concurrency.updateCommentDigest("999998", 1001, saved.getId(), null, digest, "Stale edit"))
                .isInstanceOf(RxSourceVersionValidator.Conflict.class);
        assertThat(concurrency.currentForm(1001, -1)).isNull();
    }

    @Test
    void shouldKeepClinicalVersion_whenOnlyPresentationFieldsChange() {
        Drug saved = tx().execute(status -> {
            var drug = new Drug(); drug.setDemographicId(1001); drug.setProviderNo("999998"); em.persist(drug); return drug;
        });
        long version = saved.getRowVersion();
        tx().executeWithoutResult(status -> {
            Drug current = em.find(Drug.class, saved.getId());
            current.setPosition(42); current.setHideFromCpp(true); current.setHideFromDrugProfile(true);
        });
        assertThat(concurrency.currentForm(1001, saved.getId()).version()).isEqualTo(version);
        tx().executeWithoutResult(status -> concurrency.checkSources(1001, Map.of(saved.getId(), version)));
    }

}
