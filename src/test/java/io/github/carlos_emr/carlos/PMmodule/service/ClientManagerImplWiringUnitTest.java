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
package io.github.carlos_emr.carlos.PMmodule.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.commn.dao.JointAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.JointAdmissionDaoImpl;
import io.github.carlos_emr.carlos.commn.model.JointAdmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The client manager reaches its joint-admission DAO. applicationContext.xml declares the
 * {@code clientManager} bean {@code autowire="byName"}, and the DAO is a component scanned under
 * its default name, {@code jointAdmissionDaoImpl}, so a by-name match never fills the
 * {@code jointAdmissionDao} property: every PMM client view and every queue admission with
 * dependents failed with a NullPointerException. The setter is wired by type instead.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("pmmodule")
@DisplayName("ClientManagerImpl joint-admission DAO wiring")
class ClientManagerImplWiringUnitTest {

    @Test
    void shouldWireTheJointAdmissionDaoByType_sinceItsBeanNameDoesNotMatchTheProperty() throws Exception {
        String scannedName = JointAdmissionDaoImpl.class.getAnnotation(Repository.class).value();
        assertThat(scannedName).as("the DAO takes its default bean name").isEmpty();

        assertThat(ClientManagerImpl.class.getMethod("setJointAdmissionDao", JointAdmissionDao.class)
                .isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    void shouldReadDependentsThroughTheWiredDao_whenTheViewListsThem() {
        JointAdmissionDao dao = mock(JointAdmissionDao.class);
        List<JointAdmission> dependents = List.of(new JointAdmission());
        when(dao.getSpouseAndDependents(42)).thenReturn(dependents);
        ClientManagerImpl manager = new ClientManagerImpl();
        manager.setJointAdmissionDao(dao);

        assertThat(manager.getDependents(42)).isSameAs(dependents);
        verify(dao).getSpouseAndDependents(42);
    }
}
