/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ClientImageDAO} verifying save, retrieve, and
 * ordering behavior during the Hibernate migration.
 *
 * <p>These tests validate that HBM-mapped Blob property roundtrips
 * (image_data &harr; image_contents) and the internal {@code QueueCache}
 * behave correctly after Hibernate version changes.</p>
 *
 * <p><strong>Note on caching</strong>: {@code ClientImageDAOImpl} uses a static
 * {@code QueueCache} keyed by demographic_no. Each test uses a unique
 * demographic_no value to prevent cross-test cache pollution after
 * transactional rollback.</p>
 *
 * @since 2026-02-09
 * @see ClientImageDAO
 * @see ClientImageDAOImpl
 */
@DisplayName("ClientImageDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class ClientImageDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    private ClientImageDAO clientImageDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates a {@link ClientImage} instance for testing.
     *
     * @param demographicNo int the patient demographic number
     * @param imageType     String the MIME type of the image
     * @param imageData     byte[] the raw image bytes (kept small or null for tests)
     * @param updateDate    Date the last update timestamp
     * @return ClientImage a new unsaved entity
     */
    private ClientImage createClientImage(int demographicNo, String imageType, byte[] imageData, Date updateDate) {
        ClientImage image = new ClientImage();
        image.setDemographic_no(demographicNo);
        image.setImage_type(imageType);
        image.setImage_data(imageData);
        image.setUpdate_date(updateDate);
        return image;
    }

    @Test
    @Tag("create")
    @DisplayName("should save client image when valid data provided")
    void shouldSaveClientImage_whenValidDataProvided() {
        // Given
        ClientImage image = createClientImage(10001, "image/png", new byte[]{1, 2, 3}, new Date());

        // When
        clientImageDAO.saveClientImage(image);
        entityManager.flush();

        // Then
        ClientImage retrieved = clientImageDAO.getClientImage(10001);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getDemographic_no()).isEqualTo(10001);
        assertThat(retrieved.getImage_type()).isEqualTo("image/png");
        assertThat(retrieved.getImage_data()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return client image when client ID matches")
    void shouldReturnClientImage_whenClientIdMatches() {
        // Given
        ClientImage image = createClientImage(10002, "image/jpeg", new byte[]{10, 20, 30}, new Date());
        clientImageDAO.saveClientImage(image);
        entityManager.flush();

        // When
        ClientImage found = clientImageDAO.getClientImage(10002);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo(10002);
        assertThat(found.getImage_type()).isEqualTo("image/jpeg");
        assertThat(found.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when client ID does not exist")
    void shouldReturnNull_whenClientIdDoesNotExist() {
        // When
        ClientImage found = clientImageDAO.getClientImage(99999);

        // Then
        assertThat(found).isNull();
    }

    @Test
    @Tag("update")
    @DisplayName("should update existing image when same client ID saved")
    void shouldUpdateExistingImage_whenSameClientIdSaved() {
        // Given - save first image
        ClientImage image1 = createClientImage(10004, "image/png", new byte[]{1, 2, 3}, new Date());
        clientImageDAO.saveClientImage(image1);
        entityManager.flush();

        // When - save second image with same demographic_no but different type
        // The DAO's saveClientImage copies data from the new image to the existing
        // managed entity and also persists the new image. The existing entity gets
        // update_date set to now(), making it the most recent.
        ClientImage image2 = createClientImage(10004, "image/jpeg", new byte[]{4, 5, 6},
                new Date(System.currentTimeMillis() - 3600000));
        clientImageDAO.saveClientImage(image2);
        entityManager.flush();

        // Then - retrieval should return an image with the updated type
        ClientImage found = clientImageDAO.getClientImage(10004);
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo(10004);
        assertThat(found.getImage_type()).isEqualTo("image/jpeg");
    }

    @Test
    @Tag("read")
    @DisplayName("should return most recent image when multiple exist")
    void shouldReturnMostRecentImage_whenMultipleExist() {
        // Given - persist two images with different update dates directly
        // via EntityManager to avoid the DAO's cache and update logic
        ClientImage olderImage = createClientImage(10005, "image/png", null,
                new Date(System.currentTimeMillis() - 86400000));
        entityManager.persist(olderImage);

        ClientImage newerImage = createClientImage(10005, "image/jpeg", null, new Date());
        entityManager.persist(newerImage);

        entityManager.flush();
        // Clear first-level cache so the DAO query hits the database
        entityManager.clear();

        // When
        ClientImage found = clientImageDAO.getClientImage(10005);

        // Then - should return the image with the most recent update_date
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo(10005);
        assertThat(found.getImage_type()).isEqualTo("image/jpeg");
    }
}
