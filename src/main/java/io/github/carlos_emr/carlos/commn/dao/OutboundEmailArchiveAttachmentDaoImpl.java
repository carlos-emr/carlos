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

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchiveAttachment;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA DAO implementation for outbound email archive attachment metadata.
 */
@Repository
public class OutboundEmailArchiveAttachmentDaoImpl extends AbstractDaoImpl<OutboundEmailArchiveAttachment> implements OutboundEmailArchiveAttachmentDao {

    private static final String PHYSICAL_DELETE_DISABLED_MESSAGE =
            "Outbound email archive attachments must be retained with their parent archive";

    public OutboundEmailArchiveAttachmentDaoImpl() {
        super(OutboundEmailArchiveAttachment.class);
    }

    @Override
    public void remove(AbstractModel<?> o) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public boolean remove(Object id) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public void batchRemove(List<OutboundEmailArchiveAttachment> oList) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }

    @Override
    public void batchRemove(List<OutboundEmailArchiveAttachment> oList, int batchSize) {
        throw new UnsupportedOperationException(PHYSICAL_DELETE_DISABLED_MESSAGE);
    }
}
