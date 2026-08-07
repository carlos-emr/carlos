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
/**
 * View-model assembler tier for the Ontario billing module.
 *
 * <p>This package builds {@code *ViewModel} instances from request parameters
 * and DAO lookups. The web tier ({@code billings.ca.on.web}) calls into
 * exactly one assembler per page; the assembler does the read-side work and
 * returns an immutable view model that the JSP renders via EL.</p>
 *
 * <h2>Naming contract</h2>
 *
 * Three suffix families are reserved with strict semantics. Pick the one that
 * matches the component's role:
 *
 * <dl>
 *   <dt>{@code *ViewModelAssembler}</dt>
 *   <dd>Top-level orchestrator. Builds <em>one</em> {@code *ViewModel} from
 *       primitive inputs (request, model, DAOs). Pure read; no side effects.
 *       Constructed by web-tier actions; public surface is constructor
 *       injection plus an {@code assemble(...)} method.</dd>
 *
 *   <dt>{@code *Composer} / {@code *Loader} / {@code *Resolver}</dt>
 *   <dd>Focused read-side step for a cohesive slice of a view model. Mutates a
 *       supplied {@code Builder}, returns a partial result, or resolves one
 *       value. Owned by the assemblers that consume it; never instantiated
 *       from {@code web/}. Example:
 *       {@link BillingOnFormSiteContextComposer} remains separate because
 *       site/multisite/RMA/clinic-number context is a complex sub-structure
 *       with dedicated tests.</dd>
 * </dl>
 *
 * <p>For side effects (DAO writes, file I/O, audit, mutation), see the
 * sibling {@code billings.ca.on.service} package — those are
 * {@code *Service} classes, not assemblers.</p>
 *
 * <h2>Anti-pattern guard rails</h2>
 *
 * <ul>
 *   <li>Don't extract a trivial one-off helper into a
 *       {@code *Composer}/{@code *Loader}/{@code *Resolver}. A single-caller
 *       extraction is acceptable only when the role names a cohesive
 *       read-side slice and its tests make the top-level assembler easier to
 *       maintain.</li>
 *   <li>Side-effect logic does not belong in this package. If a class
 *       writes to the database, writes a file, or sends a notification,
 *       it's a {@code *Service} and lives next door.</li>
 * </ul>
 *
 * @since 2026-04-26
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;
