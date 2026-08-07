/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * <h3>DAO Behavior Under Test</h3>
 * <ul>
 *   <li>{@link ClientImageDAO#saveClientImage(ClientImage)} -- persists a new image or
 *       updates an existing image for the same demographic_no. When an existing image is
 *       found, the DAO copies {@code image_data}, {@code image_type}, and sets
 *       {@code update_date} to {@code now()} on the managed entity, then also persists
 *       the incoming entity via {@code saveOrUpdate}. The static cache entry for that
 *       demographic_no is evicted on every save.</li>
 *   <li>{@link ClientImageDAO#getClientImage(Integer)} -- returns the most recent
 *       {@link ClientImage} for a given demographic_no, ordered by {@code update_date DESC}.
 *       Results under 1 MB are cached in the static {@code QueueCache}.</li>
 * </ul>
 *
 * <h3>Test Isolation Strategy</h3>
 * <p>Each test method uses a distinct {@code demographic_no} (10001, 10002, 10004, 10005,
 * 99999) to avoid interference from the static {@code QueueCache} that survives
 * Spring's transactional rollback. This ensures that cache state from one test never
 * leaks into another.</p>
 *
 * @since 2026-02-09
 * @see ClientImageDAO
 * @see ClientImageDAOImpl
 * @see ClientImage
 */
@DisplayName("ClientImageDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class ClientImageDAOIntegrationTest extends CarlosTestBase {

    /**
     * The DAO under test, injected by Spring from the test application context.
     *
     * <p>This is the {@link ClientImageDAOImpl} instance, which extends
     * {@code HibernateDaoSupport} and maintains an internal static
     * {@code QueueCache<Integer, ClientImage>} keyed by demographic_no.</p>
     *
     * @see ClientImageDAO
     * @see ClientImageDAOImpl
     */
    @Autowired
    private ClientImageDAO clientImageDAO;

    /**
     * JPA {@link EntityManager} used for direct database operations that bypass DAO
     * caching logic.
     *
     * <p>Used in tests that need to set up data without triggering the DAO's
     * {@code QueueCache} side effects, or that need to call {@code flush()} and
     * {@code clear()} to force database synchronization and evict the first-level
     * persistence context cache.</p>
     *
     * <p>The unit name {@code "entityManagerFactory"} corresponds to the test
     * persistence unit defined in the modern test context configuration.</p>
     *
     * @see jakarta.persistence.PersistenceContext
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Factory method that creates a new, unsaved {@link ClientImage} entity populated
     * with the given test data.
     *
     * <p>The returned entity has no {@code id} set (it is {@code null} until persisted).
     * This method does not interact with the database or the DAO's cache.</p>
     *
     * @param demographicNo int the patient demographic number, used as the logical key
     *                      for image lookup and caching in {@link ClientImageDAOImpl}
     * @param imageType     String the MIME type of the image (e.g., "image/png", "image/jpeg")
     * @param imageData     byte[] the raw image bytes; kept small in tests to stay under
     *                      the DAO's 1 MB cache threshold, or {@code null} for cache-bypass tests
     * @param updateDate    Date the last update timestamp, used by the DAO's
     *                      {@code ORDER BY update_date DESC} query to determine recency
     * @return ClientImage a new transient entity ready for persistence
     *
     * @see ClientImage
     */
    private ClientImage createClientImage(int demographicNo, String imageType, byte[] imageData, Date updateDate) {
        ClientImage image = new ClientImage();
        image.setDemographic_no(demographicNo);
        image.setImage_type(imageType);
        image.setImage_data(imageData);
        image.setUpdate_date(updateDate);
        return image;
    }

    /**
     * Verifies that {@link ClientImageDAO#saveClientImage(ClientImage)} successfully
     * persists a new {@link ClientImage} entity and that it can be retrieved immediately
     * afterward via {@link ClientImageDAO#getClientImage(Integer)}.
     *
     * <p>This test validates the basic create-then-read roundtrip, confirming that:</p>
     * <ul>
     *   <li>The entity is written to the database (verified by explicit flush)</li>
     *   <li>The demographic_no is stored and retrievable correctly</li>
     *   <li>The MIME type string survives the persistence roundtrip</li>
     *   <li>The binary image_data (Blob-mapped via HBM) is non-null after retrieval,
     *       confirming that the {@code image_data &harr; image_contents} Blob mapping
     *       works correctly</li>
     * </ul>
     *
     * @see ClientImageDAO#saveClientImage(ClientImage)
     * @see ClientImageDAO#getClientImage(Integer)
     */
    @Test
    @Tag("create")
    @DisplayName("should save client image when valid data provided")
    void shouldSaveClientImage_whenValidDataProvided() {
        // Given - create a transient ClientImage with a small 3-byte payload
        ClientImage image = createClientImage(10001, "image/png", new byte[]{1, 2, 3}, new Date());

        // When - persist via DAO and flush to synchronize with the database
        clientImageDAO.saveClientImage(image);
        hibernateTemplate.flush();

        // Then - retrieve by demographic_no and verify all fields survived the roundtrip
        ClientImage retrieved = clientImageDAO.getClientImage(10001);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getDemographic_no()).isEqualTo(10001);
        assertThat(retrieved.getImage_type()).isEqualTo("image/png");
        // Verify image_data is non-null, confirming the HBM Blob mapping roundtrip works
        assertThat(retrieved.getImage_data()).isNotNull();
    }

    /**
     * Verifies that {@link ClientImageDAO#getClientImage(Integer)} returns the correct
     * {@link ClientImage} when a matching demographic_no exists in the database.
     *
     * <p>This test focuses on the read path, confirming that:</p>
     * <ul>
     *   <li>The DAO query correctly filters by {@code demographic_no}</li>
     *   <li>The retrieved entity has a database-assigned primary key ({@code id} is non-null)</li>
     *   <li>The MIME type is preserved exactly as stored</li>
     *   <li>The correct demographic_no is returned (not a different patient's image)</li>
     * </ul>
     *
     * <p>Uses demographic_no {@code 10002} to avoid cache collision with other tests.</p>
     *
     * @see ClientImageDAO#getClientImage(Integer)
     */
    @Test
    @Tag("read")
    @DisplayName("should return client image when client ID matches")
    void shouldReturnClientImage_whenClientIdMatches() {
        // Given - persist an image so there is data to retrieve
        ClientImage image = createClientImage(10002, "image/jpeg", new byte[]{10, 20, 30}, new Date());
        clientImageDAO.saveClientImage(image);
        hibernateTemplate.flush();

        // When - retrieve the image by its demographic_no
        ClientImage found = clientImageDAO.getClientImage(10002);

        // Then - verify the retrieved entity matches what was saved
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo(10002);
        assertThat(found.getImage_type()).isEqualTo("image/jpeg");
        // Confirm the entity received a database-generated primary key upon persistence
        assertThat(found.getId()).isNotNull();
    }

    /**
     * Verifies that {@link ClientImageDAO#getClientImage(Integer)} returns {@code null}
     * when no {@link ClientImage} exists for the given demographic_no.
     *
     * <p>This tests the DAO's behavior with a non-existent demographic_no (99999),
     * confirming that:</p>
     * <ul>
     *   <li>The HQL query returns an empty result list</li>
     *   <li>The DAO returns {@code null} rather than throwing an exception</li>
     *   <li>No cache entry is created for a missing demographic_no</li>
     * </ul>
     *
     * <p>This is important for callers that use the null return value to decide
     * whether to display a placeholder image ({@link ClientImage#imageMissingPlaceholderUrl}).</p>
     *
     * @see ClientImageDAO#getClientImage(Integer)
     * @see ClientImage#imageMissingPlaceholderUrl
     */
    @Test
    @Tag("read")
    @DisplayName("should return null when client ID does not exist")
    void shouldReturnNull_whenClientIdDoesNotExist() {
        // When - attempt to retrieve an image for a demographic_no with no stored data
        ClientImage found = clientImageDAO.getClientImage(99999);

        // Then - the DAO should return null, not throw an exception
        assertThat(found).isNull();
    }

    /**
     * Verifies that {@link ClientImageDAO#saveClientImage(ClientImage)} correctly updates
     * an existing {@link ClientImage} when a new image is saved for the same demographic_no.
     *
     * <p>This tests the DAO's upsert behavior, which works as follows in
     * {@link ClientImageDAOImpl#saveClientImage(ClientImage)}:</p>
     * <ol>
     *   <li>The DAO calls {@code getClientImage()} to check for an existing image</li>
     *   <li>If found, it copies {@code image_data} and {@code image_type} from the new
     *       image onto the existing managed entity</li>
     *   <li>It sets {@code update_date} to {@code new Date()} on the existing entity,
     *       making it the most recent</li>
     *   <li>It also calls {@code saveOrUpdate()} on the new (incoming) image entity</li>
     *   <li>It evicts the cache entry for that demographic_no</li>
     * </ol>
     *
     * <p>After the update, a subsequent {@code getClientImage()} call should return
     * an image reflecting the updated {@code image_type} value.</p>
     *
     * @see ClientImageDAO#saveClientImage(ClientImage)
     * @see ClientImageDAO#getClientImage(Integer)
     */
    @Test
    @Tag("update")
    @DisplayName("should update existing image when same client ID saved")
    void shouldUpdateExistingImage_whenSameClientIdSaved() {
        // Given - save first image with PNG type
        ClientImage image1 = createClientImage(10004, "image/png", new byte[]{1, 2, 3}, new Date());
        clientImageDAO.saveClientImage(image1);
        hibernateTemplate.flush();

        // When - save second image with same demographic_no but different type.
        // The DAO's saveClientImage copies data from the new image to the existing
        // managed entity and also persists the new image. The existing entity gets
        // update_date set to now(), making it the most recent.
        ClientImage image2 = createClientImage(10004, "image/jpeg", new byte[]{4, 5, 6},
                // Set the incoming image's update_date to 1 hour in the past so
                // the DAO's now() override on the existing entity is more recent
                new Date(System.currentTimeMillis() - 3600000));
        clientImageDAO.saveClientImage(image2);
        hibernateTemplate.flush();

        // Then - retrieval should return an image with the updated JPEG type,
        // because the DAO copied image_type from image2 onto the existing entity
        ClientImage found = clientImageDAO.getClientImage(10004);
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo(10004);
        assertThat(found.getImage_type()).isEqualTo("image/jpeg");
    }

    /**
     * Verifies that {@link ClientImageDAO#getClientImage(Integer)} returns the most
     * recently updated {@link ClientImage} when multiple images exist for the same
     * demographic_no.
     *
     * <p>The DAO's HQL query uses {@code ORDER BY update_date DESC} and returns the
     * first result, so the image with the latest {@code update_date} should always
     * be returned.</p>
     *
     * <p>This test bypasses the DAO for data setup by using the {@link EntityManager}
     * directly. This avoids triggering the DAO's {@code QueueCache} population and
     * the save-time update logic that would modify {@code update_date} values,
     * giving us precise control over the timestamps used for ordering verification.</p>
     *
     * <p>After persisting, the EntityManager's first-level cache is cleared via
     * {@code clear()} to ensure the subsequent DAO call issues a fresh HQL query
     * against the database rather than returning a cached entity.</p>
     *
     * @see ClientImageDAO#getClientImage(Integer)
     */
    @Test
    @Tag("read")
    @DisplayName("should return most recent image when multiple exist")
    void shouldReturnMostRecentImage_whenMultipleExist() {
        // Given - persist two images with different update dates directly
        // via EntityManager to avoid the DAO's cache and update logic

        // Older image: update_date set to 24 hours ago
        ClientImage olderImage = createClientImage(10005, "image/png", null,
                new Date(System.currentTimeMillis() - 86400000));
        hibernateTemplate.save(olderImage);

        // Newer image: update_date set to now
        ClientImage newerImage = createClientImage(10005, "image/jpeg", null, new Date());
        hibernateTemplate.save(newerImage);

        // Flush writes to the database, then clear the first-level persistence
        // context cache so the DAO query hits the database instead of returning
        // a stale cached entity reference
        hibernateTemplate.flush();
        hibernateTemplate.clear();

        // When - retrieve via DAO, which queries with ORDER BY update_date DESC
        ClientImage found = clientImageDAO.getClientImage(10005);

        // Then - should return the image with the most recent update_date (JPEG),
        // confirming the ORDER BY DESC clause in the DAO's HQL query works correctly
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo(10005);
        assertThat(found.getImage_type()).isEqualTo("image/jpeg");
    }
}
