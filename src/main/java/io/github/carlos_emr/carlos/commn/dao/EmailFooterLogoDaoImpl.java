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

import java.util.List;

import jakarta.persistence.LockModeType;

import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA access to the clinic's email footer logo rows (issue #3981).
 *
 * @since 2026-10-08
 */
@Repository
public class EmailFooterLogoDaoImpl extends AbstractDaoImpl<EmailFooterLogo> implements EmailFooterLogoDao {

    private static final String CURRENT = "select l from EmailFooterLogo l where l.removedAt is null order by l.id desc";

    public EmailFooterLogoDaoImpl() {
        super(EmailFooterLogo.class);
    }

    @Override
    @Transactional(readOnly = true)
    public EmailFooterLogo findCurrent() {
        List<EmailFooterLogo> rows = entityManager.createQuery(CURRENT, EmailFooterLogo.class)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<EmailFooterLogo> lockCurrent() {
        return entityManager.createQuery(CURRENT, EmailFooterLogo.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }
}
