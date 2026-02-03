/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.RemoteIntegratedDataCopy;


public interface RemoteIntegratedDataCopyDao extends AbstractDao<RemoteIntegratedDataCopy> {
    void archiveDataCopyExceptThisOne(RemoteIntegratedDataCopy remoteIntegratedDataCopy);

    RemoteIntegratedDataCopy findByDemoType(Integer facilityId, Integer demographicNo, String dataType);

    RemoteIntegratedDataCopy findByType(Integer facilityId, String dataType);

    RemoteIntegratedDataCopy findByDemoTypeSignature(Integer facilityId, Integer demographicNo, String dataType, String signature);

    RemoteIntegratedDataCopy save(Integer demographicNo, Object obj, String providerNo, Integer facilityId) throws Exception;

    RemoteIntegratedDataCopy save(Integer demographicNo, Object obj, String providerNo, Integer facilityId, String type) throws Exception;

    <T> T getObjectFrom(Class<T> clazz, RemoteIntegratedDataCopy rid) throws Exception;
}
