/**
 * Copyright (c) 2026. CARLOS EMR Project Contributors. All Rights Reserved.
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
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;

/**
 * Row locks on the report rows of one lab chain, for filing a report's measurements (#6391).
 *
 * <p>Filing takes these locks FIRST in its transaction, before any plain read of the chain, so two
 * filings of one report (or a report and its correction) run one after the other and the second
 * reads what the first committed. On MariaDB's REPEATABLE READ a locking read does not open the
 * consistent-read snapshot, which is why the order matters: the plain reads that follow open their
 * snapshot after the lock is held.</p>
 *
 * <p>Every method joins the caller's transaction and is refused outside one: a lock taken in a
 * transaction of its own would be released before the caller used it. They are separate statements,
 * not one {@code OR}, so each is a plain equality on its own index (accession_index,
 * idx_hl7TextInfo_filler_order_num, labno_index); a predicate that cannot use an index can fall back
 * to a scan, and a locking scan under REPEATABLE READ locks every row it passes.</p>
 *
 * <p>Always lock in this order: accession, filler order number, the report's own row.</p>
 */
public interface Hl7TextInfoChainLockDao {

    /**
     * Locks the report rows of one accession number, lowest lab number first.
     *
     * @param accession the accession number; blank locks nothing
     * @return the locked rows
     */
    List<Hl7TextInfo> lockByAccession(String accession);

    /**
     * Locks the report rows of one filler order number, lowest lab number first. A glucose tolerance
     * series arrives under different accession numbers and one filler order number.
     *
     * @param fillerOrderNumber the filler order number; blank locks nothing
     * @return the locked rows
     */
    List<Hl7TextInfo> lockByFillerOrderNumber(String fillerOrderNumber);

    /**
     * Locks one report's own row, which a report with neither an accession nor a filler order number
     * would otherwise leave unlocked.
     *
     * @param labNumber the report
     * @return the locked row, or {@code null} when no such report is stored (it was removed)
     */
    Hl7TextInfo lockByLabNumber(int labNumber);
}
