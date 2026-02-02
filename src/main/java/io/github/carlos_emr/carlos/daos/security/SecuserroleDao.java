//CHECKSTYLE:OFF
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
 */

package io.github.carlos_emr.carlos.daos.security;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.web.formbean.StaffForm;

import io.github.carlos_emr.carlos.model.security.Secuserrole;

/**
 * A data access object (DAO) providing persistence and search support for
 * Secuserrole entities. Transaction control of the save(), update() and
 * delete() operations can directly support Spring container-managed
 * transactions or they can be augmented to handle user-managed Spring
 * transactions. Each of these methods provides additional information for how
 * to configure it for the desired type of transaction control.
 *
 * @author MyEclipse Persistence Tools
 * @see Secuserrole
 */

public interface SecuserroleDao {
    public static final String PROVIDER_NO = "providerNo";
    public static final String ROLE_NAME = "roleName";
    public static final String ORGCD = "orgcd";
    public static final String ACTIVEYN = "activeyn";

    public void saveAll(List list);

    public void save(Secuserrole transientInstance);

    public void updateRoleName(Integer id, String roleName);

    public void delete(Secuserrole persistentInstance);

    public int deleteByOrgcd(String orgcd);

    public int deleteByProviderNo(String providerNo);

    public int deleteById(Integer id);

    public int update(Secuserrole instance);

    public Secuserrole findById(java.lang.Integer id);

    public List findByExample(Secuserrole instance);

    public List findByProperty(String propertyName, Object value);

    public List findByProviderNo(Object providerNo);

    public List findByRoleName(Object roleName);

    public List findByOrgcd(Object orgcd, boolean activeOnly);

    public List searchByCriteria(StaffForm staffForm);

    public List findByActiveyn(Object activeyn);

    public List findAll();

    public Secuserrole merge(Secuserrole detachedInstance);

    public void attachDirty(Secuserrole instance);

    public void attachClean(Secuserrole instance);
}
