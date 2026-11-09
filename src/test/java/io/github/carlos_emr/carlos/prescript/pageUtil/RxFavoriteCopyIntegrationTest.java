/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import io.github.carlos_emr.carlos.commn.model.Favorites;
import io.github.carlos_emr.carlos.commn.model.FavoritesPrivilege;
import io.github.carlos_emr.carlos.managers.RxFavoriteCopyService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.*;
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
class RxFavoriteCopyIntegrationTest extends CarlosUnitTestBase {
    static class CopyFault { final AtomicInteger remaining = new AtomicInteger(); }
    @Autowired private CopyFault fault;
    @Autowired private RxFavoriteCopyService copies;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager em;
    @AfterEach void reset() { fault.remaining.set(0); }
    @Test void shouldRollbackEveryCopy_whenTheSecondTemplateInsertFails() {
        var tx = new TransactionTemplate(transactions);
        List<Favorites> selected = tx.execute(status -> {
            var share = new FavoritesPrivilege(); share.setProviderNo("997101"); share.setOpenToPublic(true); em.persist(share);
            var first = new Favorites(); first.setProviderNo("997101"); first.setFavoriteName("Synthetic first"); first.setSpecial("Keep directions");
            var second = new Favorites(); second.setProviderNo("997101"); second.setFavoriteName("Synthetic second"); second.setSpecial("Keep other directions");
            em.persist(first); em.persist(second); return List.of(first, second);
        });
        fault.remaining.set(2);
        assertThatThrownBy(() -> copies.copy("997101", "997102", selected)).isInstanceOf(IllegalStateException.class);
        Long afterFailure = tx.execute(status -> em.createQuery("select count(f) from Favorites f where f.providerNo='997102'", Long.class).getSingleResult());
        assertThat(afterFailure).isZero();
        copies.copy("997101", "997102", selected);
        Long afterRetry = tx.execute(status -> em.createQuery("select count(f) from Favorites f where f.providerNo='997102'", Long.class).getSingleResult());
        assertThat(afterRetry).isEqualTo(2L);
        List<String> instructions = tx.execute(status -> em.createQuery("select f.special from Favorites f where f.providerNo='997102' order by f.favoriteName", String.class).getResultList());
        assertThat(instructions).containsExactly("Keep directions", "Keep other directions");
    }
}
