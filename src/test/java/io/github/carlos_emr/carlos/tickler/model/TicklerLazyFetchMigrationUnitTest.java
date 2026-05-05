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
package io.github.carlos_emr.carlos.tickler.model;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.TicklerManagerImpl;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the Tickler relationship lazy-fetch migration.
 *
 * @since 2026-05-05
 */
@DisplayName("Tickler lazy fetch migration")
@Tag("unit")
@Tag("fast")
class TicklerLazyFetchMigrationUnitTest {

    @Test
    @DisplayName("should use lazy fetch for all Tickler relationships")
    void shouldUseLazyFetch_forTicklerRelationships() {
        Stream.of("ticklerCategory", "demographic", "provider", "assignee", "program")
                .forEach(fieldName -> assertThat(manyToOneFetchType(fieldName)).isEqualTo(FetchType.LAZY));

        assertThat(oneToManyFetchType("comments")).isEqualTo(FetchType.LAZY);
    }

    @Test
    @DisplayName("should declare transactional boundary for relationship mutators")
    void shouldDeclareTransactionalBoundary_forRelationshipMutators() throws NoSuchMethodException {
        Method updateTickler = TicklerManagerImpl.class.getMethod(
                "updateTickler",
                io.github.carlos_emr.carlos.utility.LoggedInInfo.class,
                Tickler.class);
        Method reassign = TicklerManagerImpl.class.getMethod(
                "reassign",
                io.github.carlos_emr.carlos.utility.LoggedInInfo.class,
                Integer.class,
                String.class,
                String.class);

        assertThat(updateTickler.getAnnotation(Transactional.class)).isNotNull();
        assertThat(reassign.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("should resolve provider names without entity traversal for chart printing")
    void shouldResolveProviderNamesWithoutEntityTraversal_forChartPrinting() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/casemgmt/print/OscarChartPrinter.java"),
                StandardCharsets.UTF_8);

        assertThat(source).doesNotContain("tickler.getProvider().getFormattedName()");
        assertThat(source).doesNotContain("tickler.getAssignee().getFormattedName()");
        assertThat(source).contains("getProviderName(tickler.getCreator())");
        assertThat(source).contains("getProviderName(tickler.getTaskAssignedTo())");
    }

    @Test
    @DisplayName("should join fetch detail graph for Tickler find")
    void shouldJoinFetchDetailGraph_forTicklerFind() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/commn/dao/TicklerDaoImpl.java"),
                StandardCharsets.UTF_8);

        assertThat(source).contains("left join fetch t.comments");
        assertThat(source).contains("left join fetch t.demographic");
        assertThat(source).contains("left join fetch t.provider");
        assertThat(source).contains("left join fetch t.assignee");
    }

    private FetchType manyToOneFetchType(String fieldName) {
        return getField(fieldName).getAnnotation(ManyToOne.class).fetch();
    }

    private FetchType oneToManyFetchType(String fieldName) {
        return getField(fieldName).getAnnotation(OneToMany.class).fetch();
    }

    private Field getField(String fieldName) {
        try {
            return Tickler.class.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Expected Tickler field to exist: " + fieldName, e);
        }
    }
}
