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

import io.github.carlos_emr.carlos.commn.model.CdsImportAdmission;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

/** Data access for one protected CDS import table; orchestration belongs in services. */
@Repository
public class CdsImportAdmissionDao extends AbstractDaoImpl<CdsImportAdmission> {
    public CdsImportAdmissionDao() { super(CdsImportAdmission.class); }
    public CdsImportAdmission lock(String id) { return entityManager.find(CdsImportAdmission.class, id, LockModeType.PESSIMISTIC_WRITE); }
    public CdsImportAdmission readLock(String id) { return entityManager.find(CdsImportAdmission.class, id, LockModeType.PESSIMISTIC_READ); }
    public CdsImportAdmission get(String id) { return entityManager.find(CdsImportAdmission.class, id); }
    public void flushAndClear() { entityManager.flush(); entityManager.clear(); }
}
