/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS00;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * JPA Data Access Object for BC Teleplan S00 records.
 * <p>
 * The S00 record in Teleplan represents an active billing claim transaction.
 * This DAO provides methods to query these records by control numbers, 
 * office numbers, and specific error or status indicators (e.g., 'BG' flags).
 */
@Repository
public class TeleplanS00Dao extends AbstractDaoImpl<TeleplanS00> {

    public TeleplanS00Dao() {
        super(TeleplanS00.class);
    }

    @SuppressWarnings("unchecked")
    public List<TeleplanS00> findAll() {
        Query query = createQuery("x", null);
        return query.getResultList();
    }

    /**
     * Finds S00 records by their MSP control number, which uniquely identifies
     * a specific billing submission to the province.
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanS00> findByBillingNo(String mspCtlNo) {
        Query q = createQuery("t", "t.mspCtlNo = :no");
        q.setParameter("no", mspCtlNo);
        return q.getResultList();
    }

    public List<TeleplanS00> findByOfficeNumber(String officeNumber) {
        List<String> numbers = new ArrayList<String>();
        numbers.add(officeNumber);
        return findByOfficeNumbers(numbers);
    }

    @SuppressWarnings("unchecked")
    public List<TeleplanS00> findByOfficeNumbers(List<String> officeNumbers) {
        if (officeNumbers.isEmpty()) {
            return new ArrayList<TeleplanS00>();
        }

        Query q = createQuery("t", "t.officeNo IN (:no)");
        q.setParameter("no", officeNumbers);
        return q.getResultList();
    }

    /**
     * Finds records where any of the 7 Teleplan explanatory code fields equals {@code "BG"}.
     * <p>
     * A {@code BG} explanatory code indicates the claim was settled at an amount
     * different from what was originally billed.
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanS00> findBgs() {
        // Teleplan allows up to 7 explanatory codes per transaction; check all of them for 'BG'
        Query q = createQuery("t", "t.exp1 = :s OR t.exp2 = :s OR t.exp3 = :s OR t.exp4 = :s OR t.exp5 = :s OR t.exp6 = :s OR t.exp7 = :s");
        q.setParameter("s", "BG");
        return q.getResultList();
    }

    /**
     * Queries for practitioners linked to a specific Teleplan S21 remittance batch header ID.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> search_taprovider(Integer s21Id) {
        // Joins with the Provider table using the OhipNo field (often dual-purposed for provincial billing numbers)
        Query q = entityManager.createQuery("select r.practitionerNo, p.LastName,p.FirstName from TeleplanS00 r, Provider p where p.OhipNo=r.practitionerNo and r.s21Id=?1 group by r.practitionerNo");
        q.setParameter(1, s21Id);
        return q.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<TeleplanS00> search_taS00(Integer s21Id, String type, String practitionerNo) {
        Query q = entityManager.createQuery("select t from TeleplanS00 t where t.s21Id=?1 and t.s00Type<>?2 and t.practitionerNo like ?3 order by t.id");
        q.setParameter(1, s21Id);
        q.setParameter(2, type);
        q.setParameter(3, practitionerNo);
        return q.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<TeleplanS00> search_taS01(Integer s21Id, String type, String practitionerNo) {
        Query q = entityManager.createQuery("select t from TeleplanS00 t where t.s21Id=?1 and t.s00Type<>?2 and t.practitionerNo like ?3 order by t.id");
        q.setParameter(1, s21Id);
        q.setParameter(2, type);
        q.setParameter(3, practitionerNo);
        return q.getResultList();
    }
}
