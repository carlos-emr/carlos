/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link AbstractModel#equals(Object)} used to dereference both a {@code null} argument and a
 * {@code null} identifier, so {@code equals(null)} and even {@code unsaved.equals(unsaved)} threw
 * (#5303). These cases pin the null-safe, reflexive contract without changing how persisted
 * entities compare.
 */
@Tag("unit")
class AbstractModelEqualityUnitTest {

    /** Minimal entity with a fixed identifier. */
    private static final class Probe extends AbstractModel<Integer> {
        private final Integer id;

        Probe(Integer id) {
            this.id = id;
        }

        @Override
        public Integer getId() {
            return id;
        }
    }

    /** A second entity class whose identifiers overlap Probe's. */
    private static final class OtherProbe extends AbstractModel<Integer> {
        private final Integer id;

        OtherProbe(Integer id) {
            this.id = id;
        }

        @Override
        public Integer getId() {
            return id;
        }
    }

    @Test
    @DisplayName("should answer false, not throw, when compared with null")
    void shouldReturnFalse_whenComparedWithNull() {
        assertThat(new Probe(1).equals(null)).isFalse();
        assertThat(new Probe(null).equals(null)).isFalse();
    }

    @Test
    @DisplayName("should equal itself while it has no identifier")
    void shouldEqualItself_whenNotYetPersisted() {
        Probe unsaved = new Probe(null);

        assertThat(unsaved.equals(unsaved)).isTrue();
    }

    @Test
    @DisplayName("should not equal another unsaved instance")
    void shouldNotEqualAnotherInstance_whenBothAreUnsaved() {
        assertThat(new Probe(null).equals(new Probe(null))).isFalse();
    }

    @Test
    @DisplayName("should compare symmetrically between an unsaved and a persisted instance")
    void shouldBeSymmetric_betweenUnsavedAndPersisted() {
        Probe unsaved = new Probe(null);
        Probe saved = new Probe(7);

        assertThat(unsaved.equals(saved)).isFalse();
        assertThat(saved.equals(unsaved)).isFalse();
    }

    @Test
    @DisplayName("should keep identifier equality for persisted instances of the same class")
    void shouldCompareByIdentifier_forPersistedInstances() {
        assertThat(new Probe(5).equals(new Probe(5))).isTrue();
        assertThat(new Probe(5).hashCode()).isEqualTo(new Probe(5).hashCode());
        assertThat(new Probe(5).equals(new Probe(6))).isFalse();
    }

    @Test
    @DisplayName("should not equal an instance of another class with the same identifier")
    void shouldNotEqual_forOtherClassWithSameIdentifier() {
        assertThat(new Probe(5).equals(new OtherProbe(5))).isFalse();
        assertThat(new Probe(5).equals("5")).isFalse();
    }

    @Test
    @DisplayName("should let collections find and remove an unsaved entity")
    void shouldSupportCollectionMembership_forUnsavedEntity() {
        Probe unsaved = new Probe(null);
        List<Probe> list = new ArrayList<>(List.of(new Probe(3), unsaved));
        Set<Probe> set = new HashSet<>(Set.of(unsaved));

        assertThat(list.contains(unsaved)).isTrue();
        assertThat(list.indexOf(new Probe(3))).isZero();
        assertThat(set.contains(unsaved)).isTrue();
        assertThat(list.remove(unsaved)).isTrue();
        assertThat(list).hasSize(1);
    }
}
