/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The clinic email footer logo rows (issue #3981): which row is the logo in use, and that the
 * lock is only taken inside a transaction.
 */
@Tag("integration")
@Tag("dao")
@Tag("email")
@Transactional
class EmailFooterLogoDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private EmailFooterLogoDao logoDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    @Tag("read")
    @DisplayName("should return the newest logo not removed, with its picture")
    void shouldFindNewestLogo_whenSomeRemoved() {
        persist("image/png", new byte[] {1}, null);
        EmailFooterLogo current = persist("image/jpeg", new byte[] {2, 3}, null);
        persist("image/png", new byte[] {4}, new Date());
        entityManager.flush();
        entityManager.clear();

        EmailFooterLogo found = logoDao.findCurrent();

        assertThat(found.getId()).isEqualTo(current.getId());
        assertThat(found.getContentType()).isEqualTo("image/jpeg");
        assertThat(found.getImageData()).containsExactly(2, 3);
        assertThat(logoDao.lockCurrent()).extracting(EmailFooterLogo::getId).hasSize(2).first().isEqualTo(current.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should return nothing when every logo was removed")
    void shouldReturnNull_whenAllRemoved() {
        persist("image/png", new byte[] {1}, new Date());
        entityManager.flush();

        assertThat(logoDao.findCurrent()).isNull();
        assertThat(logoDao.lockCurrent()).isEmpty();
    }

    @Test
    @Tag("read")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should refuse to lock outside a transaction")
    void shouldRefuseLock_whenNoTransaction() {
        assertThatThrownBy(() -> logoDao.lockCurrent()).isInstanceOf(IllegalTransactionStateException.class);
    }

    private EmailFooterLogo persist(String contentType, byte[] bytes, Date removedAt) {
        EmailFooterLogo logo = new EmailFooterLogo();
        logo.setContentType(contentType);
        logo.setImageData(bytes);
        logo.setWidth(10);
        logo.setHeight(5);
        logo.setSha256("a".repeat(64));
        logo.setUploadedBy("999998");
        logo.setUploadedAt(new Date());
        logo.setRemovedAt(removedAt);
        logo.setRemovedBy(removedAt == null ? null : "999998");
        logoDao.persist(logo);
        return logo;
    }
}
