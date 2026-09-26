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
 *
 * Ported from the openo-beta/Open-O tickler attachment component
 * (PR #2491, Sebastian Ibanez) and adapted for CARLOS.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.TicklerDocs;

/**
 * Data access for {@link TicklerDocs}, the tickler attachment store.
 *
 * <p>Every finder excludes soft-deleted rows ({@code deleted IS NULL}). The reverse
 * finders ({@link #findByDocument}, {@link #findByLab}) answer "which ticklers reference
 * this item" for the document viewer, the lab viewer and the REST converter.</p>
 *
 * @since 2026-09-26
 */
public interface TicklerDocsDao extends AbstractDao<TicklerDocs> {

    /**
     * Finds one tickler's live attachments of a given type for a given item.
     *
     * @param ticklerId Integer tickler identifier
     * @param documentNo Integer attached item identifier
     * @param docType String one of the {@link TicklerDocs} {@code DOCTYPE_*} codes
     * @return List&lt;TicklerDocs&gt; matching rows, empty when none
     */
    List<TicklerDocs> findByTicklerIdDocNoDocType(Integer ticklerId, Integer documentNo, String docType);

    /**
     * Finds one tickler's live attachments of a given type.
     *
     * @param ticklerId Integer tickler identifier
     * @param docType String one of the {@link TicklerDocs} {@code DOCTYPE_*} codes
     * @return List&lt;TicklerDocs&gt; matching rows, empty when none
     */
    List<TicklerDocs> findByTicklerIdDocType(Integer ticklerId, String docType);

    /**
     * Finds all of one tickler's live attachments, every type, oldest first.
     *
     * @param ticklerId Integer tickler identifier
     * @return List&lt;TicklerDocs&gt; matching rows, empty when none
     */
    List<TicklerDocs> findByTicklerId(Integer ticklerId);

    /**
     * Batch finder for a page of ticklers. One query regardless of page size, so the
     * tickler list never falls into an N+1 pattern.
     *
     * @param ticklerIds List&lt;Integer&gt; tickler identifiers; null or empty returns empty
     * @return List&lt;TicklerDocs&gt; live attachments across the given ticklers
     */
    List<TicklerDocs> findByTicklerIds(List<Integer> ticklerIds);

    /**
     * Reverse finder: live attachments of a non-lab item, for "ticklers for this document".
     *
     * @param documentNo Integer attached item identifier
     * @param docType String one of the {@link TicklerDocs} {@code DOCTYPE_*} codes
     * @return List&lt;TicklerDocs&gt; matching rows ordered by id, empty when none
     */
    List<TicklerDocs> findByDocument(Integer documentNo, String docType);

    /**
     * Reverse finder for labs. Lab numbers are only unique within a lab source, so the
     * lab type is part of the key.
     *
     * @param labNo Integer lab (segment) identifier
     * @param labType String lab source code, e.g. {@code HL7}
     * @return List&lt;TicklerDocs&gt; matching rows ordered by id, empty when none
     */
    List<TicklerDocs> findByLab(Integer labNo, String labType);
}
