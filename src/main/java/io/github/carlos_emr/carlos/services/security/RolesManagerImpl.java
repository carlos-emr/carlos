/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.services.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDao;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.model.security.Secrole;

@Transactional
public class RolesManagerImpl implements RolesManager {

    private SecroleDao secroleDao;
    private SecobjprivilegeDao secobjprivilegeDao;

    public void setSecroleDao(SecroleDao dao) {
        this.secroleDao = dao;
    }

    public List<Secrole> getRoles() {
        return secroleDao.getRoles();
    }

    public Secrole getRole(String id) {
        return secroleDao.getRole(Integer.parseInt(id));
    }

    public Secrole getRole(int id) {
        return secroleDao.getRole(id);
    }

    public Secrole getRoleByRolename(String roleName) {
        return secroleDao.getRoleByName(roleName);
    }

    public void save(Secrole secrole) {
        secroleDao.save(secrole);
    }

    public void saveFunction(Secobjprivilege secobjprivilege) {
        secobjprivilegeDao.save(secobjprivilege);
    }

    public void saveFunctions(Secrole secrole, List newLst, String roleName) {
        if (secrole != null) secroleDao.save(secrole);
        List existLst = secobjprivilegeDao.getFunctions(roleName);

        ArrayList lstForDelete = new ArrayList();
        for (int i = 0; i < existLst.size(); i++) {
            boolean keepIt = false;
            Secobjprivilege sur1 = (Secobjprivilege) existLst.get(i);
            for (int j = 0; j < newLst.size(); j++) {
                Secobjprivilege sur2 = (Secobjprivilege) newLst.get(j);
                if (compare(sur1, sur2)) {
                    keepIt = true;
                    break;
                }
            }
            if (!keepIt) lstForDelete.add(sur1);
        }
        for (int i = 0; i < lstForDelete.size(); i++) {
            secobjprivilegeDao.delete((Secobjprivilege) lstForDelete.get(i));
        }

        secobjprivilegeDao.saveAll(newLst);
    }

    public boolean compare(Secobjprivilege sur1, Secobjprivilege sur2) {
        boolean isSame = false;
        if (sur1.getObjectname_code().equals(sur2.getObjectname_code()) &&
                sur1.getPrivilege_code().equals(sur2.getPrivilege_code()) &&
                sur1.getRoleusergroup().equals(sur2.getRoleusergroup()))
            isSame = true;
        return isSame;
    }

    public List getFunctions(String roleName) {
        return secobjprivilegeDao.getFunctions(roleName);
    }

    public String getFunctionDesc(String function_code) {
        return secobjprivilegeDao.getFunctionDesc(function_code);
    }

    public String getAccessDesc(String accessType_code) {
        return secobjprivilegeDao.getAccessDesc(accessType_code);
    }

    public void setSecobjprivilegeDao(SecobjprivilegeDao secobjprivilegeDao) {
        this.secobjprivilegeDao = secobjprivilegeDao;
    }

}
