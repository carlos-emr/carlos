/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Billingreferral;
import org.springframework.stereotype.Repository;

/**
 * @author Toby
 */
@Deprecated
@Repository
public class BillingreferralDaoImpl extends AbstractDaoImpl<Billingreferral> implements BillingreferralDao {

    public BillingreferralDaoImpl() {
        super(Billingreferral.class);
    }

    @Override
    public Billingreferral getByReferralNo(String referral_no) {
        String sql = "select br From Billingreferral br WHERE br.referralNo=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, referral_no);

        @SuppressWarnings("unchecked")
        List<Billingreferral> brs = query.getResultList();
        if (!brs.isEmpty())
            return brs.get(0);
        return null;
    }

    @Override
    public Billingreferral getById(int id) {
        return find(id);
    }

    @Override
    public List<Billingreferral> getBillingreferrals() {
        String sql = "SELECT br From Billingreferral br ORDER BY  br.lastName, br.firstName";
        Query query = entityManager.createQuery(sql);

        @SuppressWarnings("unchecked")
        List<Billingreferral> brs = query.getResultList();

        return brs;
    }

    @Override
    public List<Billingreferral> getBillingreferral(String referral_no) {
        String sql = "SELECT br From Billingreferral br WHERE br.referralNo=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, referral_no);

        @SuppressWarnings("unchecked")
        List<Billingreferral> cList = query.getResultList();

        if (cList != null && cList.size() > 0) {
            return cList;
        } else {
            return null;
        }
    }

    @Override
    public List<Billingreferral> getBillingreferral(String last_name, String first_name) {
        String sql = "SELECT br From Billingreferral br WHERE br.lastName like ?1 and br.firstName like ?2 order by br.lastName";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "%" + last_name + "%");
        query.setParameter(2, "%" + first_name + "%");

        @SuppressWarnings("unchecked")
        List<Billingreferral> cList = query.getResultList();

        if (cList != null && cList.size() > 0) {
            return cList;
        } else {
            return null;
        }
    }

    @Override
    public List<Billingreferral> getBillingreferralByLastName(String last_name) {
        String sql = "SELECT br From Billingreferral br WHERE br.lastName like ?1 order by br.lastName";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "%" + last_name + "%");

        @SuppressWarnings("unchecked")
        List<Billingreferral> cList = query.getResultList();

        if (cList != null && cList.size() > 0) {
            return cList;
        } else {
            return null;
        }
    }


    @Override
    public List<Billingreferral> getBillingreferralBySpecialty(String specialty) {
        String sql = "SELECT br From Billingreferral br WHERE br.specialty like ?1 order by br.lastName";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "%" + specialty + "%");

        @SuppressWarnings("unchecked")
        List<Billingreferral> cList = query.getResultList();

        if (cList != null && cList.size() > 0) {
            return cList;
        } else {
            return null;
        }
    }

    /*
     * Don't blame me for this one, converted from SQL.
     */
    @Override
    public List<Billingreferral> searchReferralCode(String codeName, String codeName1, String codeName2, String desc, String fDesc, String desc1, String fDesc1, String desc2, String fDesc2) {
        String sql = "SELECT b FROM Billingreferral b WHERE b.referralNo LIKE ?1 or b.referralNo LIKE ?2 or b.referralNo LIKE ?3"
                + " or (b.lastName LIKE ?4 and b.firstName LIKE ?5)"
                + " or (b.lastName LIKE ?6 and b.firstName LIKE ?7)"
                + " or (b.lastName LIKE ?8 and b.firstName LIKE ?9)";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, codeName);
        query.setParameter(2, codeName1);
        query.setParameter(3, codeName2);
        query.setParameter(4, desc);
        query.setParameter(5, fDesc);
        query.setParameter(6, desc1);
        query.setParameter(7, fDesc1);
        query.setParameter(8, desc2);
        query.setParameter(9, fDesc2);


        @SuppressWarnings("unchecked")
        List<Billingreferral> cList = query.getResultList();

        return cList;
    }

    @Override
    public void updateBillingreferral(Billingreferral obj) {
        if (obj.getBillingreferralNo() == null || obj.getBillingreferralNo().intValue() == 0) {
            persist(obj);
        } else {
            merge(obj);
        }
    }

    @Override
    public String getReferralDocName(String referral_no) {
        Billingreferral br = this.getByReferralNo(referral_no);

        if (br != null)
            return br.getLastName() + ", " + br.getFirstName();

        return "";
    }
}
