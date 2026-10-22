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
package io.github.carlos_emr.carlos.chartspace;

/**
 * Load outcome of a ChartSpace block, shared by every block.
 *
 * <p>{@link #NO_ACCESS} is a state of its own, never folded into
 * {@link #EMPTY}: a clinician must be able to tell "nothing recorded" from
 * "not allowed to see".</p>
 *
 * @since 2026-10-08
 */
public enum BlockStatus {
    /** The block has data to show. */
    OK,
    /** The patient has no records for this block. */
    EMPTY,
    /** The user lacks the privilege for this block's data. */
    NO_ACCESS
}
