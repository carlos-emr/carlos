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

package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveAttachment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("OutboundEmailArchiveAttachmentDaoImpl")
@Tag("unit")
@Tag("dao")
class OutboundEmailArchiveAttachmentDaoImplUnitTest {

    private OutboundEmailArchiveAttachmentDaoImpl dao;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        dao = new OutboundEmailArchiveAttachmentDaoImpl();
        entityManager = mock(EntityManager.class);
        dao.entityManager = entityManager;
    }

    @Test
    void shouldRejectPhysicalDeletionMethods() {
        OutboundEmailArchiveAttachment attachment = new OutboundEmailArchiveAttachment();
        List<OutboundEmailArchiveAttachment> attachments = List.of(attachment);

        assertThatThrownBy(() -> dao.remove(attachment))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("retained with their parent archive");
        assertThatThrownBy(() -> dao.remove(888))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("retained with their parent archive");
        assertThatThrownBy(() -> dao.batchRemove(attachments))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("retained with their parent archive");
        assertThatThrownBy(() -> dao.batchRemove(attachments, 25))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("retained with their parent archive");
        assertThatThrownBy(() -> dao.batchRemoveAtomically(attachments))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("retained with their parent archive");
        assertThatThrownBy(() -> dao.batchRemoveAtomically(attachments, 25))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("retained with their parent archive");
        assertThatThrownBy(() -> dao.batchRemoveWithIndependentCommits(attachments))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("retained with their parent archive");
        assertThatThrownBy(() -> dao.batchRemoveWithIndependentCommits(attachments, 25))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("retained with their parent archive");
        verifyNoInteractions(entityManager);
    }
}
