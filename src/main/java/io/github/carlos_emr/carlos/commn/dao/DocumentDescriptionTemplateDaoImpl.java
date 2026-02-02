//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2012- Centre de Medecine Integree
 * <p>
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
 * Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
 * as part of the OSCAR McMaster EMR System
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.DocumentDescriptionTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentDescriptionTemplateDaoImpl extends AbstractDaoImpl<DocumentDescriptionTemplate> implements DocumentDescriptionTemplateDao {

    public DocumentDescriptionTemplateDaoImpl() {
        super(DocumentDescriptionTemplate.class);
    }

    public List<DocumentDescriptionTemplate> findByDocTypeAndProviderNo(String docType, String providerNo) {
        Query query;
        if (providerNo == null) {
            query = entityManager.createQuery("select x from DocumentDescriptionTemplate x where x.docType=?1 and x.providerNo is NULL order by x.descriptionShortcut,x.id");
            query.setParameter(1, docType);
        } else {
            query = entityManager.createQuery("select x from DocumentDescriptionTemplate x where x.docType=?1 and x.providerNo=?2 order by x.descriptionShortcut,x.id");
            query.setParameter(1, docType);
            query.setParameter(2, providerNo);
        }

        return query.getResultList();
    }
}
