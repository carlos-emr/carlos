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

import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.State;
import java.util.List;
import java.util.function.Consumer;

/**
 * Persistence for {@link PatientPortalInviteDelivery}.
 *
 * <p>Every write commits in its own transaction, so each lifecycle step is durable before the
 * network call that follows it.
 *
 * @since 2026-09-22
 */
public interface PatientPortalInviteDeliveryDao extends AbstractDao<PatientPortalInviteDelivery> {

    /** Persists a new attempt and commits it. */
    PatientPortalInviteDelivery claim(PatientPortalInviteDelivery delivery);

    /**
     * Moves an attempt from {@code expected} to {@code next}, applying {@code change} in the same
     * transaction, under a row lock.
     *
     * @return the updated row, or {@code null} when the row is missing or no longer in {@code expected}
     */
    PatientPortalInviteDelivery advance(Long id, State expected, State next,
            Consumer<PatientPortalInviteDelivery> change);

    /** @return the most recent attempts for a patient, newest first */
    List<PatientPortalInviteDelivery> findRecentByDemographic(int demographicNo, int limit);

    /** @return every attempt for a patient that has not finished, oldest first */
    List<PatientPortalInviteDelivery> findUnfinishedByDemographic(int demographicNo);
}
