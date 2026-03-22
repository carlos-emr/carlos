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
 * Data access object for {@link TeleplanS00} entities.
 * Provides persistence operations for Teleplan S00 (remittance claim line) records
 * in the BC MSP billing system, including lookups by billing number, office number,
 * practitioner, and BG (balance forward) detection.
 *
 * @since 2026-03-17
 */
@Repository
public class TeleplanS00Dao extends AbstractDaoImpl<TeleplanS00> {

    /**
     * Constructs a new {@code TeleplanS00Dao} with the {@link TeleplanS00} entity class.
     */
    public TeleplanS00Dao() {
        super(TeleplanS00.class);
    }

    /**
     * Retrieves all S00 records.
     *
     * @return List of all {@link TeleplanS00} records
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanS00> findAll() {
        Query query = createQuery("x", null);
        return query.getResultList();
    }

    /**
     * Finds S00 records by MSP control number.
     *
     * @param mspCtlNo String the MSP control number
     * @return List of matching {@link TeleplanS00} records
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanS00> findByBillingNo(String mspCtlNo) {
        Query q = createQuery("t", "t.mspCtlNo = :no");
        q.setParameter("no", mspCtlNo);
        return q.getResultList();
    }

    /**
     * Finds S00 records by a single office number. Delegates to {@link #findByOfficeNumbers(List)}.
     *
     * @param officeNumber String the office number to search for
     * @return List of matching {@link TeleplanS00} records
     */
    public List<TeleplanS00> findByOfficeNumber(String officeNumber) {
        List<String> numbers = new ArrayList<String>();
        numbers.add(officeNumber);
        return findByOfficeNumbers(numbers);
    }

    /**
     * Finds S00 records matching any of the specified office numbers.
     *
     * @param officeNumbers List of String office numbers to search for; returns empty list if empty
     * @return List of matching {@link TeleplanS00} records
     */
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
     * Finds all S00 records that have a "BG" (balance forward) explanation code in any of the
     * seven explanation fields.
     *
     * @return List of {@link TeleplanS00} records with BG explanation codes
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanS00> findBgs() {
        Query q = createQuery("t", "t.exp1 = :s OR t.exp2 = :s OR t.exp3 = :s OR t.exp4 = :s OR t.exp5 = :s OR t.exp6 = :s OR t.exp7 = :s");
        q.setParameter("s", "BG");
        return q.getResultList();
    }

    /**
     * Finds distinct practitioners and their provider names for a given S21 remittance header.
     *
     * @param s21Id Integer the parent S21 record ID
     * @return List of Object arrays containing practitioner number, last name, and first name
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> search_taprovider(Integer s21Id) {
        Query q = entityManager.createQuery("select r.practitionerNo, p.LastName,p.FirstName from TeleplanS00 r, Provider p where p.OhipNo=r.practitionerNo and r.s21Id=?1 group by r.practitionerNo");
        q.setParameter(1, s21Id);
        return q.getResultList();
    }

    /**
     * Searches S00 records by parent S21 ID, excluding a specified type, and filtering by practitioner number.
     * Results are ordered by ID.
     *
     * @param s21Id Integer the parent S21 record ID
     * @param type String the S00 type to exclude
     * @param practitionerNo String the practitioner number pattern (supports LIKE wildcards)
     * @return List of matching {@link TeleplanS00} records
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanS00> search_taS00(Integer s21Id, String type, String practitionerNo) {
        Query q = entityManager.createQuery("select t from TeleplanS00 t where t.s21Id=?1 and t.s00Type<>?2 and t.practitionerNo like ?3 order by t.id");
        q.setParameter(1, s21Id);
        q.setParameter(2, type);
        q.setParameter(3, practitionerNo);
        return q.getResultList();
    }

    /**
     * Searches S01 records by parent S21 ID, excluding a specified type, and filtering by practitioner number.
     * Results are ordered by ID. Uses the same query structure as {@link #search_taS00}.
     *
     * @param s21Id Integer the parent S21 record ID
     * @param type String the S00 type to exclude
     * @param practitionerNo String the practitioner number pattern (supports LIKE wildcards)
     * @return List of matching {@link TeleplanS00} records
     */
    @SuppressWarnings("unchecked")
    public List<TeleplanS00> search_taS01(Integer s21Id, String type, String practitionerNo) {
        Query q = entityManager.createQuery("select t from TeleplanS00 t where t.s21Id=?1 and t.s00Type<>?2 and t.practitionerNo like ?3 order by t.id");
        q.setParameter(1, s21Id);
        q.setParameter(2, type);
        q.setParameter(3, practitionerNo);
        return q.getResultList();
    }
}
