/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.model;

import io.github.carlos_emr.carlos.model.BaseObject;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies transient reflexivity and the existing persisted identifier contract. */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementEntityEqualityUnitTest extends CarlosUnitTestBase {
    enum EntityType {
        NOTE, ISSUE;

        BaseObject create(Long id) {
            if (this == NOTE) {
                var note = new CaseManagementNote();
                note.setId(id);
                return note;
            }
            var issue = new CaseManagementIssue();
            issue.setId(id);
            return issue;
        }
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void shouldCompareAnUnsavedEntityEqual_toItself(EntityType type) {
        var entity = type.create(null);
        // Assert the actual override: an object assertion may short-circuit reference identity.
        assertThat(entity.equals(entity)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void shouldFindAndRemoveTheSameUnsavedEntity_fromAnArrayList(EntityType type) {
        var entity = type.create(null);
        var values = new ArrayList<BaseObject>(List.of(entity));
        assertThat(values.contains(entity)).isTrue();
        assertThat(values.remove(entity)).isTrue();
        assertThat(values).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void shouldKeepDistinctUnsavedEntitiesUnequal_withNullIdentifiers(EntityType type) {
        var first = type.create(null);
        var second = type.create(null);
        assertThat(first.equals(second)).isFalse();
        assertThat(second.equals(first)).isFalse();
        assertThat(first.equals(type.create(7L))).isFalse();
        assertThat(type.create(7L).equals(first)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void shouldRejectNullAndOtherTypes_forTransientAndPersistedEntities(EntityType type) {
        for (var entity : List.of(type.create(null), type.create(7L))) {
            assertThat(entity.equals(null)).isFalse();
            assertThat(entity.equals("7")).isFalse();
            assertThat(entity.equals((type == EntityType.NOTE ? EntityType.ISSUE : EntityType.NOTE).create(7L))).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void shouldPreserveIdentifierEqualityAndHashCodes_forPersistedEntities(EntityType type) {
        var first = type.create(7L);
        var sameId = type.create(7L);
        var otherId = type.create(8L);
        assertThat(first.equals(first)).isTrue();
        assertThat(first.equals(sameId)).isTrue();
        assertThat(sameId.equals(first)).isTrue();
        assertThat(first.hashCode()).isEqualTo(sameId.hashCode());
        assertThat(first.equals(otherId)).isFalse();
        assertThat(otherId.equals(first)).isFalse();
    }
}
