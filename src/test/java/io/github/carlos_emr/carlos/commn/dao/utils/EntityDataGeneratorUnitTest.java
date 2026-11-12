/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao.utils;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how {@link EntityDataGenerator} fills hand-set ids (issue #6168).
 *
 * <p>An {@code @Id} without {@code @GeneratedValue} keeps the value the generator sets. A draw of
 * 0.0 used to give id 0, which made DAO tests that check for a positive saved id fail about once
 * in 10,000 runs. These tests force the lowest and highest draws.</p>
 *
 * @since 2026-11-12
 */
@Tag("unit")
@DisplayName("EntityDataGenerator hand-set ids")
class EntityDataGeneratorUnitTest {

    private static final double LOWEST_DRAW = 0.0;
    private static final double HIGHEST_DRAW = Math.nextDown(1.0);

    @Test
    @DisplayName("should give a hand-set Integer id 1 when the draw is 0.0")
    void shouldGiveIdOne_whenDrawIsZero() throws Exception {
        ManualIntegerIdEntity entity = new ManualIntegerIdEntity();

        EntityDataGenerator.generateTestDataForModelClass(entity, () -> LOWEST_DRAW);

        assertThat(entity.id).isEqualTo(1);
        assertThat(entity.count).isZero();
    }

    @Test
    @DisplayName("should give a hand-set Long id 1 when the draw is 0.0")
    void shouldGiveLongIdOne_whenDrawIsZero() throws Exception {
        ManualLongIdEntity entity = new ManualLongIdEntity();

        EntityDataGenerator.generateTestDataForModelClass(entity, () -> LOWEST_DRAW);

        assertThat(entity.id).isEqualTo(1L);
        assertThat(entity.total).isZero();
    }

    @Test
    @DisplayName("should give a getter-annotated id 1 when the draw is 0.0")
    void shouldGiveGetterIdOne_whenDrawIsZero() throws Exception {
        GetterIdEntity entity = new GetterIdEntity();

        EntityDataGenerator.generateTestDataForModelClass(entity, () -> LOWEST_DRAW);

        assertThat(entity.getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("should keep hand-set ids at 9999 or below when the draw is just under 1.0")
    void shouldCapIdAt9999_whenDrawIsJustUnderOne() throws Exception {
        ManualIntegerIdEntity entity = new ManualIntegerIdEntity();

        EntityDataGenerator.generateTestDataForModelClass(entity, () -> HIGHEST_DRAW);

        assertThat(entity.id).isEqualTo(9999);
        assertThat(entity.count).isEqualTo(9999);
    }

    @Test
    @DisplayName("should leave a database-generated id unset")
    void shouldLeaveIdUnset_whenIdIsGenerated() throws Exception {
        GeneratedIdEntity entity = new GeneratedIdEntity();

        EntityDataGenerator.generateTestDataForModelClass(entity, () -> LOWEST_DRAW);

        assertThat(entity.id).isNull();
        assertThat(entity.count).isZero();
    }

    @Test
    @DisplayName("should map draws to ids from 1 to 9999")
    void shouldMapDrawsToIdRange_forLowestAndHighestDraws() {
        assertThat(EntityDataGenerator.manualIdFrom(LOWEST_DRAW)).isEqualTo(1);
        assertThat(EntityDataGenerator.manualIdFrom(HIGHEST_DRAW)).isEqualTo(9999);
    }

    @Test
    @DisplayName("should give a hand-set id from 1 to 9999 through the public entry point")
    void shouldGivePositiveId_withDefaultRandomSource() throws Exception {
        ManualIntegerIdEntity entity = new ManualIntegerIdEntity();

        EntityDataGenerator.generateTestDataForModelClass(entity);

        assertThat(entity.id).isBetween(1, 9999);
    }

    /** Hand-set Integer id plus a plain int field. */
    static class ManualIntegerIdEntity {
        @Id
        private Integer id;
        private int count;
    }

    /** Hand-set Long id plus a plain long field. */
    static class ManualLongIdEntity {
        @Id
        private Long id;
        private long total;
    }

    /** Property-access entity: the id annotation is on the getter. */
    static class GetterIdEntity {
        private Integer id;

        @Id
        public Integer getId() {
            return id;
        }
    }

    /** Database-generated id plus a plain int field. */
    static class GeneratedIdEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;
        private int count;
    }
}
