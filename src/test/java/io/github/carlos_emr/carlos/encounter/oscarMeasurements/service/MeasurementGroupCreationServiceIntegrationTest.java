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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.service;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService.Outcome;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link MeasurementGroupCreationService} against the H2 schema: a group
 * is stored with its members, a second group of the same name is refused, and a failed member
 * insert leaves no group behind (issue #3335).
 *
 * <p>The rollback test suspends the {@code CarlosTestBase} test transaction
 * ({@code Propagation.NOT_SUPPORTED}) so the service's own transaction really commits or rolls
 * back; inside a joined transaction the two would be indistinguishable. Its rows are removed in
 * a committed transaction afterwards. See {@code FaxManagerImplTransactionIntegrationTest}.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("MeasurementGroupCreationService integration tests")
@Tag("integration")
@Tag("measurement")
@Tag("create")
class MeasurementGroupCreationServiceIntegrationTest extends CarlosTestBase {

    @Autowired
    private MeasurementGroupCreationService service;

    @Autowired
    private MeasurementGroupStyleDao styleDao;

    @Autowired
    private MeasurementGroupDao groupDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("should store the style row and one member row per chosen type")
    void shouldStoreTheGroupWithItsMembers_whenTypesAreChosen() {
        String group = "FAKE IT created group";

        assertThat(service.create(group, 3, List.of("FAKE Weight", "FAKE Height"))).isEqualTo(Outcome.CREATED);

        List<MeasurementGroupStyle> styles = styleDao.findByGroupName(group);
        assertThat(styles).hasSize(1);
        assertThat(styles.get(0).getCssId()).isEqualTo(3);
        assertThat(groupDao.findByName(group)).extracting(MeasurementGroup::getTypeDisplayName)
                .containsExactlyInAnyOrder("FAKE Weight", "FAKE Height");
    }

    @Test
    @DisplayName("should refuse a second group with the same name and leave the first unchanged")
    void shouldRefuseTheSecondGroup_whenTheNameIsTaken() {
        String group = "FAKE IT duplicate group";
        service.create(group, 0, List.of("FAKE Weight"));

        assertThat(service.create(group, 0, List.of("FAKE Height"))).isEqualTo(Outcome.DUPLICATE_NAME);

        assertThat(styleDao.findByGroupName(group)).hasSize(1);
        assertThat(groupDao.findByName(group)).extracting(MeasurementGroup::getTypeDisplayName)
                .containsExactly("FAKE Weight");
    }

    @Test
    @DisplayName("should write nothing when no type is chosen")
    void shouldWriteNothing_whenNoTypeIsChosen() {
        String group = "FAKE IT empty group";

        assertThat(service.create(group, 0, List.of())).isEqualTo(Outcome.NO_TYPES);

        assertThat(styleDao.findByGroupName(group)).isEmpty();
        assertThat(service.groupExists(group)).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should roll back the style row when a member insert fails")
    void shouldLeaveNoGroup_whenAMemberInsertFails() {
        String group = "FAKE IT rolled back group";
        // typeDisplayName is VARCHAR(255): the second member's insert fails after the style row
        // and the first member were written in the same transaction.
        List<String> types = List.of("FAKE Weight", "x".repeat(300));
        TransactionTemplate committed = new TransactionTemplate(transactionManager);
        try {
            assertThatThrownBy(() -> service.create(group, 0, types)).isInstanceOf(RuntimeException.class);

            List<MeasurementGroupStyle> styles = committed.execute(status -> styleDao.findByGroupName(group));
            List<MeasurementGroup> members = committed.execute(status -> groupDao.findByName(group));
            assertThat(styles).isEmpty();
            assertThat(members).isEmpty();
        } finally {
            committed.executeWithoutResult(status -> {
                styleDao.findByGroupName(group).forEach(row -> styleDao.remove(row.getId()));
                groupDao.findByName(group).forEach(row -> groupDao.remove(row.getId()));
            });
        }
    }
}
