/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises deletion after a photo has entered the shared read cache. */
class ClientImageDeleteUnitTest extends CarlosUnitTestBase {
    @Test
    @DisplayName("should invalidate only the selected cached photo without removing a detached entity")
    void shouldInvalidateSelectedCache_whenDeletingPhoto() {
        EntityManager em = mock(EntityManager.class);
        Query select = mock(Query.class);
        Query delete = mock(Query.class);
        when(em.createQuery(startsWith("from ClientImage"))).thenReturn(select);
        when(em.createQuery("delete from ClientImage i where i.demographic_no = ?1")).thenReturn(delete);
        when(delete.setParameter(1, 987654301)).thenReturn(delete);
        when(delete.executeUpdate()).thenReturn(2);
        ClientImage selected = image(987654301);
        ClientImage other = image(987654302);
        when(select.getResultList()).thenReturn(List.of(selected), List.of(other), List.of());
        ClientImageDAOImpl dao = new ClientImageDAOImpl() {
            @Override protected EntityManager entityManager() { return em; }
        };

        assertThat(dao.getClientImage(987654301)).isSameAs(selected);
        assertThat(dao.getClientImage(987654302)).isSameAs(other);
        dao.deleteClientImage(987654301);

        assertThat(dao.getClientImage(987654302)).isSameAs(other);
        assertThat(dao.getClientImage(987654301)).isNull();
        verify(delete).setParameter(1, 987654301);
        verify(delete).executeUpdate();
        verify(em, never()).remove(any());
        verify(select, times(3)).getResultList();
        // Evict the second owned cache entry so later tests cannot inherit it.
        when(delete.setParameter(1, 987654302)).thenReturn(delete);
        dao.deleteClientImage(987654302);
    }

    private static ClientImage image(int patient) {
        ClientImage image = new ClientImage();
        image.setDemographic_no(patient);
        image.setImage_data(new byte[] {1, 2, 3});
        image.setImage_type("jpeg");
        return image;
    }
}
