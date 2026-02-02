//CHECKSTYLE:OFF
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

import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Clinic;
import org.springframework.stereotype.Repository;

/**
 * @author Jason Gallagher
 */
@Repository
public class ClinicDAOImpl extends AbstractDaoImpl<Clinic> implements ClinicDAO {

    public ClinicDAOImpl() {
        super(Clinic.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Clinic> findAll() {
        Query query = entityManager.createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x");
        List<Clinic> results = query.getResultList();
        return results;
    }

    @Override
    public Clinic getClinic() {
        Query query = entityManager.createQuery("select c from Clinic c");
        @SuppressWarnings("unchecked")
        List<Clinic> codeList = query.getResultList();
        if (codeList.size() > 0) {
            return codeList.get(0);
        }
        return null;
    }

    @Override
    public void save(Clinic clinic) {
        if (clinic.getId() != null && clinic.getId().intValue() > 0) {
            merge(clinic);
        } else {
            persist(clinic);
        }
    }

}
