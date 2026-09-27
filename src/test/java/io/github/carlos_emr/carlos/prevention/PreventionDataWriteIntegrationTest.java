// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.prevention;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Tag("prevention")
@Isolated("PreventionData retains static DAO references")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PreventionDataWriteIntegrationTest extends CarlosTestBase {
    private static final int PATIENT = 89003995;
    @Autowired private PreventionDao preventionDao;
    @Autowired private PreventionExtDao extensionDao;
    @Autowired private PartialDateDao partialDateDao;
    @Autowired private DemographicMergedDao mergedDao;
    private Integer mergedFixtureId;
    private boolean createdDemographics;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager em;
    private final Map<String, Object> originalBeans = new HashMap<>();
    private TransactionTemplate tx;

    @BeforeEach void setUpWrites() {
        tx = new TransactionTemplate(transactionManager);
        replaceBean("preventionDao", preventionDao);
        replaceBean("preventionExtDao", extensionDao);
        replaceBean("partialDateDao", partialDateDao);
        assertThat(preventionDao.findByDemographicId(PATIENT)).isEmpty();
    }

    private void replaceBean(String name, Object bean) {
        originalBeans.put(name, ReflectionTestUtils.getField(PreventionData.class, name));
        ReflectionTestUtils.setField(PreventionData.class, name, bean);
    }

    @AfterEach void cleanUpWrites() {
        try {
            tx.executeWithoutResult(status -> {
                if (mergedFixtureId != null) {
                    em.createQuery("delete from DemographicMerged d where d.id = :id")
                            .setParameter("id", mergedFixtureId).executeUpdate();
                }
                List<Prevention> owned = preventionDao.findByDemographicId(PATIENT);
                for (Prevention record : owned) {
                    em.createQuery("delete from PreventionExt e where e.preventionId = :id")
                            .setParameter("id", record.getId()).executeUpdate();
                    em.createQuery("delete from PartialDate p where p.tableName = :tableName and p.tableId = :id")
                            .setParameter("tableName", io.github.carlos_emr.carlos.commn.model.PartialDate.PREVENTION)
                            .setParameter("id", record.getId()).executeUpdate();
                    em.clear();
                    em.createQuery("delete from Prevention p where p.id = :id")
                            .setParameter("id", record.getId()).executeUpdate();
                }
                if (createdDemographics) {
                    em.createNativeQuery("delete from demographic where demographic_no in (89003995,89003996) and last_name = 'PreventionWriteOwned'")
                            .executeUpdate();
                }
            });
        } finally {
            originalBeans.forEach((name, bean) -> ReflectionTestUtils.setField(PreventionData.class, name, bean));
        }
    }

    private Integer insert(String date, ArrayList<Map<String, String>> extra) {
        return PreventionData.insertPreventionData("999998", "" + PATIENT, date, "999998", "",
                "Flu", "0", "", "0", extra, "46233009", null);
    }

    private ArrayList<Map<String, String>> extensions() {
        return new ArrayList<>(List.of(Map.of("comments", "owned prevention transaction regression")));
    }

    private void failExtensionWrite() {
        PreventionExtDao failing = mock(PreventionExtDao.class);
        doAnswer(invocation -> {
            extensionDao.persist(invocation.getArgument(0, PreventionExt.class));
            throw new IllegalStateException("synthetic extension failure after persistence");
        }).when(failing).persist(any(PreventionExt.class));
        ReflectionTestUtils.setField(PreventionData.class, "preventionExtDao", failing);
    }

    @Test void shouldRollBackRecordAndPartialDate_whenAnExtensionFails() {
        failExtensionWrite();
        assertThatThrownBy(() -> insert("2026-09", extensions())).isInstanceOf(IllegalStateException.class);
        assertThat(preventionDao.findByDemographicId(PATIENT)).isEmpty();
        assertThat(em.createQuery("select e from PreventionExt e where e.val = :marker")
                .setParameter("marker", "owned prevention transaction regression").getResultList()).isEmpty();
    }

    @Test void shouldKeepOriginalRecordVisible_whenReplacementFails() {
        Integer original = insert("2026-09-01", new ArrayList<>());
        failExtensionWrite();
        assertThatThrownBy(() -> PreventionData.updatetPreventionData("" + original, "999998",
                "" + PATIENT, "2026-09-02", "999998", "", "Flu", "0", "", "0", extensions(), "46233009"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(preventionDao.find(original).isDeleted()).isFalse();
        assertThat(preventionDao.findByDemographicId(PATIENT)).hasSize(1);
    }

    @Test void shouldCommitReplacementAndExtensionsTogether_whenSaveSucceeds() {
        Integer original = insert("2026-09-01", new ArrayList<>());
        Integer replacement = PreventionData.updatetPreventionData("" + original, "999998",
                "" + PATIENT, "2026-09-02", "999998", "", "Flu", "0", "", "0", extensions(), "46233009");
        assertThat(preventionDao.find(original).isDeleted()).isTrue();
        assertThat(preventionDao.find(replacement).isDeleted()).isFalse();
        assertThat(extensionDao.findByPreventionId(replacement)).hasSize(1);
    }

    @Test void shouldRejectUnrelatedPatient_beforeChangingOriginalRecord() {
        Integer original = insert("2026-09-01", new ArrayList<>());
        assertThatThrownBy(() -> PreventionData.updatetPreventionData("" + original, "999998",
                "89003996", "2026-09-02", "999998", "", "Flu", "0", "", "0", extensions(), "46233009"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(preventionDao.find(original).isDeleted()).isFalse();
    }
    @Test void shouldAllowCurrentMergedParent_andRejectUnmergedParent() {
        Integer original = insert("2026-09-01", new ArrayList<>());
        tx.executeWithoutResult(status -> {
            for (int demographic : List.of(PATIENT, 89003996)) {
                em.createNativeQuery("insert into demographic (demographic_no, first_name, last_name, sex, year_of_birth, month_of_birth, date_of_birth, hin, ver, provider_no) values (:id, 'Test', 'PreventionWriteOwned', 'F', '1980', '01', '02', '', '', '999998')")
                        .setParameter("id", demographic).executeUpdate();
            }
        });
        createdDemographics = true;
        mergedFixtureId = tx.execute(status -> {
            var merged = new io.github.carlos_emr.carlos.commn.model.DemographicMerged();
            merged.setDemographicNo(PATIENT);
            merged.setMergedTo(89003996);
            merged.setDeleted(0);
            mergedDao.persist(merged);
            return merged.getId();
        });
        assertThatCode(() -> PreventionData.requirePreventionInChart(original, 89003996)).doesNotThrowAnyException();
        tx.executeWithoutResult(status -> {
            var merged = mergedDao.find(mergedFixtureId);
            merged.setDeleted(1);
            mergedDao.merge(merged);
        });
        assertThatThrownBy(() -> PreventionData.requirePreventionInChart(original, 89003996))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2026-02-30", "2026-13", "2026-01-01 25:00", "bad"})
    void shouldRejectInvalidDates_withoutPersistingRows(String date) {
        assertThatThrownBy(() -> insert(date, extensions())).isInstanceOf(IllegalArgumentException.class);
        assertThat(preventionDao.findByDemographicId(PATIENT)).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2026", "2026-09"})
    void shouldPersistPartialDateWithRecord_whenSaveSucceeds(String date) {
        Integer id = insert(date, extensions());
        String display = partialDateDao.getDatePartial("2026-09-01", 
                io.github.carlos_emr.carlos.commn.model.PartialDate.PREVENTION, id,
                io.github.carlos_emr.carlos.commn.model.PartialDate.PREVENTION_PREVENTIONDATE);
        assertThat(display).isEqualTo(date);
    }

    @Test void shouldPreserveUndatedImports_withoutInventingAClinicalDate() {
        Integer id = insert("", extensions());
        assertThat(preventionDao.find(id).getPreventionDate()).isNull();
        assertThat(extensionDao.findByPreventionId(id)).hasSize(1);
    }

}
