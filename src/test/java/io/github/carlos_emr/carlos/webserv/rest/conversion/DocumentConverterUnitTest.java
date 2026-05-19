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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.DocumentReview;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentBag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DocumentConverter}.
 *
 * @since 2026-05-05
 */
@DisplayName("DocumentConverter Unit Tests")
@Tag("unit")
@Tag("converter")
class DocumentConverterUnitTest extends CarlosUnitTestBase {
    private final DocumentConverter converter = new DocumentConverter();

    @Test
    @DisplayName("should populate reviews when collection initialized")
    void shouldPopulateReviews_whenCollectionInitialized() {
        Document document = new Document(123);
        Date reviewedAt = new Date(1_234_567_890L);
        DocumentReview review = new DocumentReview(123, "999001", reviewedAt);
        review.setId(456);
        document.setReviews(new ArrayList<>());
        document.getReviews().add(review);

        DocumentTo1 transfer = converter.getAsTransferObject(null, document);

        assertThat(transfer.getReviews()).hasSize(1);
        assertThat(transfer.getReviews().get(0).getId()).isEqualTo(456);
        assertThat(transfer.getReviews().get(0).getDocumentNo()).isEqualTo(123);
        assertThat(transfer.getReviews().get(0).getProviderNo()).isEqualTo("999001");
        assertThat(transfer.getReviews().get(0).getDateTimeReviewed()).isEqualTo(reviewedAt);
    }

    @Test
    @DisplayName("should fail when collection uninitialized")
    @SuppressWarnings("unchecked")
    void shouldFail_whenCollectionUninitialized() {
        Document document = new Document(123);
        PersistentBag<DocumentReview> reviews = mock(PersistentBag.class);
        assertThat(Hibernate.isInitialized(reviews)).isFalse();
        document.setReviews(reviews);

        assertThatThrownBy(() -> converter.getAsTransferObject(null, document))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("reviews");
    }

    @Test
    @DisplayName("should skip reviews when collection null")
    void shouldSkipReviews_whenCollectionNull() {
        Document document = new Document(123);
        document.setReviews(null);

        DocumentTo1 transfer = converter.getAsTransferObject(null, document);

        assertThat(transfer.getReviews()).isEmpty();
    }
}
