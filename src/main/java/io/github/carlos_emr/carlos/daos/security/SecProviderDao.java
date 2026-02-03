/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.daos.security;

import java.util.List;

import io.github.carlos_emr.carlos.model.security.SecProvider;

/**
 * @author JZhang
 */

public interface SecProviderDao {

    public static final String LAST_NAME = "lastName";
    public static final String FIRST_NAME = "firstName";
    public static final String PROVIDER_TYPE = "providerType";
    public static final String SPECIALTY = "specialty";
    public static final String TEAM = "team";
    public static final String SEX = "sex";
    public static final String ADDRESS = "address";
    public static final String PHONE = "phone";
    public static final String WORK_PHONE = "workPhone";
    public static final String OHIP_NO = "ohipNo";
    public static final String RMA_NO = "rmaNo";
    public static final String BILLING_NO = "billingNo";
    public static final String HSO_NO = "hsoNo";
    public static final String STATUS = "status";
    public static final String COMMENTS = "comments";
    public static final String PROVIDER_ACTIVITY = "providerActivity";

    public void save(SecProvider transientInstance);

    public void saveOrUpdate(SecProvider transientInstance);

    public void delete(SecProvider persistentInstance);

    public SecProvider findById(java.lang.String id);

    public SecProvider findById(java.lang.String id, String status);

    public List findByExample(SecProviderDao instance);

    public List findByProperty(String propertyName, Object value);

    public List findByLastName(Object lastName);

    public List findByFirstName(Object firstName);

    public List findByProviderType(Object providerType);

    public List findBySpecialty(Object specialty);

    public List findByTeam(Object team);

    public List findBySex(Object sex);

    public List findByAddress(Object address);

    public List findByPhone(Object phone);

    public List findByWorkPhone(Object workPhone);

    public List findByOhipNo(Object ohipNo);

    public List findByRmaNo(Object rmaNo);

    public List findByBillingNo(Object billingNo);

    public List findByHsoNo(Object hsoNo);

    public List findByStatus(Object status);

    public List findByComments(Object comments);

    public List findByProviderActivity(Object providerActivity);

    public List findAll();

    public SecProviderDao merge(SecProviderDao detachedInstance);

    public void attachDirty(SecProviderDao instance);

    public void attachClean(SecProviderDao instance);
}
