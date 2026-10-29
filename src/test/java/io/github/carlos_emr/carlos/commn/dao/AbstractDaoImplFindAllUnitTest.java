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

import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import jakarta.persistence.metamodel.Type.PersistenceType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the deterministic ordering of {@link AbstractDaoImpl#findAll(Integer, Integer)}
 * and the identifier expansion in {@link EntityIdentifierPaths} (issue #5308). The database-backed
 * traversal is covered by {@code AbstractDaoFindAllOrderingIntegrationTest}.
 *
 * @since 2026-10-29
 */
@DisplayName("AbstractDaoImpl.findAll ordering")
@Tag("unit")
@Tag("fast")
@Tag("dao")
class AbstractDaoImplFindAllUnitTest {

    /** Stand-in model; the metamodel below is what describes its identifier. */
    static class Widget extends AbstractModel<Integer> {
        @Override
        public Integer getId() {
            return 1;
        }
    }

    static class WidgetDao extends AbstractDaoImpl<Widget> {
        WidgetDao(EntityManager entityManager) {
            super(Widget.class);
            this.entityManager = entityManager;
        }
    }

    private EntityManager entityManager;
    private Metamodel metamodel;
    private TypedQuery<Widget> query;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityManager = mock(EntityManager.class);
        metamodel = mock(Metamodel.class);
        query = mock(TypedQuery.class);
        when(entityManager.getMetamodel()).thenReturn(metamodel);
        when(entityManager.createQuery(anyString(), eq(Widget.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        EntityType<Widget> widget = singleIdEntity(Widget.class, basic("recordId"));
        doReturn(widget).when(metamodel).entity(Widget.class);
    }

    @Nested
    @DisplayName("findAll query")
    class FindAllQuery {

        @Test
        @DisplayName("should order by the identifier when no domain order is defined")
        void shouldOrderByIdentifier_whenNoDomainOrderDefined() {
            new WidgetDao(entityManager).findAll(null, null);

            verify(entityManager).createQuery("SELECT e FROM Widget e ORDER BY e.recordId ASC", Widget.class);
        }

        @Test
        @DisplayName("should put a subclass domain order before the identifier tie-breaker")
        void shouldPutDomainOrderBeforeIdentifier_whenSubclassOverrides() {
            WidgetDao dao = new WidgetDao(entityManager) {
                @Override
                protected String getFindAllDomainOrder(String alias) {
                    return " " + alias + ".name DESC ";
                }
            };

            dao.findAll(0, 10);

            verify(entityManager).createQuery("SELECT e FROM Widget e ORDER BY e.name DESC, e.recordId ASC", Widget.class);
        }

        @Test
        @DisplayName("should order by the identifier alone when the domain order is blank")
        void shouldOrderByIdentifierAlone_whenDomainOrderBlank() {
            WidgetDao dao = new WidgetDao(entityManager) {
                @Override
                protected String getFindAllDomainOrder(String alias) {
                    return "   ";
                }
            };

            assertThat(dao.findAllQuery()).isEqualTo("SELECT e FROM Widget e ORDER BY e.recordId ASC");
        }

        @Test
        @DisplayName("should resolve the identifier from the metamodel only once per DAO")
        void shouldResolveIdentifierOnce_acrossRepeatedCalls() {
            WidgetDao dao = new WidgetDao(entityManager);

            dao.findAll(0, 5);
            dao.findAll(5, 5);
            dao.findAll(10, 5);

            verify(metamodel, times(1)).entity(Widget.class);
            verify(entityManager, times(3)).createQuery("SELECT e FROM Widget e ORDER BY e.recordId ASC", Widget.class);
        }
    }

    @Nested
    @DisplayName("limit and offset")
    class LimitAndOffset {

        @Test
        @DisplayName("should default the page size to the maximum select size")
        void shouldDefaultPageSize_toMaxSelectSize() {
            new WidgetDao(entityManager).findAll(null, null);

            verify(query).setMaxResults(AbstractDao.MAX_LIST_RETURN_SIZE);
            verify(query, never()).setFirstResult(anyInt());
        }

        @Test
        @DisplayName("should refuse an oversized limit before any query is built")
        void shouldRefuseOversizedLimit_beforeBuildingQuery() {
            WidgetDao dao = new WidgetDao(entityManager);

            assertThatThrownBy(() -> dao.findAll(0, AbstractDao.MAX_LIST_RETURN_SIZE + 1))
                    .isInstanceOfSatisfying(MaxSelectLimitExceededException.class, e -> {
                        assertThat(e.getSelectLimit()).isEqualTo(AbstractDao.MAX_LIST_RETURN_SIZE);
                        assertThat(e.getSelectSize()).isEqualTo(AbstractDao.MAX_LIST_RETURN_SIZE + 1);
                    });
            verifyNoInteractions(entityManager, metamodel);
        }

        @Test
        @DisplayName("should keep a subclass's lower maximum select size")
        void shouldKeepLowerMaxSelectSize_whenSubclassOverrides() {
            WidgetDao dao = new WidgetDao(entityManager) {
                @Override
                protected int getMaxSelectSize() {
                    return 10;
                }
            };

            assertThatThrownBy(() -> dao.findAll(0, 11)).isInstanceOf(MaxSelectLimitExceededException.class);
            dao.findAll(0, null);

            verify(query).setMaxResults(10);
        }

        @Test
        @DisplayName("should apply only a positive offset")
        void shouldApplyOffset_onlyWhenPositive() {
            WidgetDao dao = new WidgetDao(entityManager);

            dao.findAll(0, 3);
            dao.findAll(-4, 3);
            verify(query, never()).setFirstResult(anyInt());

            dao.findAll(7, 3);
            verify(query).setFirstResult(7);
            verify(query, times(3)).setMaxResults(3);
        }
    }

    @Nested
    @DisplayName("identifier expansion")
    class IdentifierExpansion {

        @Test
        @DisplayName("should expand an embedded id into its components sorted by attribute name")
        void shouldExpandEmbeddedId_intoComponentsSortedByName() {
            EmbeddableType<?> key = embeddable(basic("zone"), basic("seq"));
            EntityType<?> entity = singleIdEntity(Object.class, attribute("id", key));
            doReturn(entity).when(metamodel).entity(Object.class);

            assertThat(EntityIdentifierPaths.resolve(metamodel, Object.class)).containsExactly("id.seq", "id.zone");
        }

        @Test
        @DisplayName("should expand IdClass attributes sorted by name with an association as its target id")
        void shouldExpandIdClassAttributes_withAssociationAsTargetId() {
            EntityType<?> target = singleIdEntity(Integer.class, basic("id"));
            EntityType<?> entity = mock(EntityType.class);
            when(entity.getPersistenceType()).thenReturn(PersistenceType.ENTITY);
            when(entity.hasSingleIdAttribute()).thenReturn(false);
            doReturn(Set.of(basic("labType"), attribute("eReferAttachment", target), basic("labId")))
                    .when(entity).getIdClassAttributes();
            doReturn(entity).when(metamodel).entity(Object.class);

            assertThat(EntityIdentifierPaths.resolve(metamodel, Object.class))
                    .containsExactly("eReferAttachment.id", "labId", "labType");
        }

        @Test
        @DisplayName("should fall back to the attribute flagged as id when getId rejects the reported type")
        void shouldFallBackToFlaggedIdAttribute_whenGetIdRejectsReportedType() {
            EntityType<?> entity = singleIdEntity(Object.class, basic("unused"));
            doThrow(new IllegalArgumentException("primitive id")).when(entity).getId(any());
            SingularAttribute<?, ?> name = basic("name");
            SingularAttribute<?, ?> pk = basic("pk");
            when(pk.isId()).thenReturn(true);
            doReturn(Set.of(name, pk)).when(entity).getSingularAttributes();
            doReturn(entity).when(metamodel).entity(Object.class);

            assertThat(EntityIdentifierPaths.resolve(metamodel, Object.class)).containsExactly("pk");
        }

        @Test
        @DisplayName("should stop with an error when an identifier refers back to its own entity")
        void shouldFailFast_whenIdentifierMappingIsCyclic() {
            EntityType<?> entity = mock(EntityType.class);
            when(entity.getPersistenceType()).thenReturn(PersistenceType.ENTITY);
            when(entity.hasSingleIdAttribute()).thenReturn(true);
            Type<?> idType = mock(Type.class);
            doReturn(Object.class).when(idType).getJavaType();
            doReturn(idType).when(entity).getIdType();
            doReturn(Object.class).when(entity).getJavaType();
            doReturn(attribute("parent", entity)).when(entity).getId(any());
            doReturn(entity).when(metamodel).entity(Object.class);

            assertThatThrownBy(() -> EntityIdentifierPaths.resolve(metamodel, Object.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cycle");
        }

        @Test
        @DisplayName("should reject a class the persistence unit does not map")
        void shouldRejectUnmappedClass_withIllegalArgumentException() {
            doThrow(new IllegalArgumentException("not an entity")).when(metamodel).entity(String.class);

            assertThatThrownBy(() -> EntityIdentifierPaths.resolve(metamodel, String.class))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static <X> EntityType<X> singleIdEntity(Class<X> javaType, SingularAttribute<?, ?> id) {
        @SuppressWarnings("unchecked")
        EntityType<X> entity = mock(EntityType.class);
        when(entity.getPersistenceType()).thenReturn(PersistenceType.ENTITY);
        when(entity.hasSingleIdAttribute()).thenReturn(true);
        Type<?> idType = mock(Type.class);
        doReturn(Object.class).when(idType).getJavaType();
        doReturn(idType).when(entity).getIdType();
        doReturn(javaType).when(entity).getJavaType();
        doReturn(id).when(entity).getId(any());
        return entity;
    }

    private static SingularAttribute<?, ?> basic(String name) {
        Type<?> type = mock(Type.class);
        when(type.getPersistenceType()).thenReturn(PersistenceType.BASIC);
        return attribute(name, type);
    }

    private static SingularAttribute<?, ?> attribute(String name, Type<?> type) {
        SingularAttribute<?, ?> attribute = mock(SingularAttribute.class);
        when(attribute.getName()).thenReturn(name);
        doReturn(type).when(attribute).getType();
        return attribute;
    }

    private static EmbeddableType<?> embeddable(SingularAttribute<?, ?>... components) {
        EmbeddableType<?> type = mock(EmbeddableType.class);
        when(type.getPersistenceType()).thenReturn(PersistenceType.EMBEDDABLE);
        doReturn(Set.of(components)).when(type).getSingularAttributes();
        return type;
    }
}
