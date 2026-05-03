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
 * Web tier for the Ontario billing module.
 *
 * <p>Houses Struts2 actions ({@code *2Action.java}) that handle HTTP
 * requests for ON billing pages. Each action:</p>
 *
 * <ol>
 *   <li>Enforces the page's privilege requirement
 *       ({@link io.github.carlos_emr.carlos.managers.SecurityInfoManager#hasPrivilege}).</li>
 *   <li>For mutation pages: enforces the POST-only contract via the
 *       request-method guard.</li>
 *   <li>Delegates to <em>exactly one</em> of:
 *       <ul>
 *         <li>{@code billings.ca.on.assembler.*Assembler} — for read pages
 *             that build a view model.</li>
 *         <li>{@code billings.ca.on.service.*Service} — for mutation pages
 *             that perform side effects.</li>
 *       </ul>
 *   </li>
 *   <li>Stashes the assembled view model on the request as
 *       {@code xModel} (e.g. {@code formModel}, {@code statusModel}) for
 *       the JSP to render.</li>
 *   <li>Returns a Struts result name (typically {@code SUCCESS}, sometimes
 *       a chain target).</li>
 * </ol>
 *
 * <h2>Naming</h2>
 *
 * <p>All actions end in {@code *2Action} per the BC-billing-established
 * migration convention. {@code View*2Action} usually means a read-only render
 * gate, but there are explicit legacy exceptions where the URL contract kept a
 * {@code View} prefix around a POST-gated mutation. {@code *Save2Action} /
 * {@code Update*2Action} names are always mutation gates (POST-only). The
 * bare-no-prefix {@code *2Action} (e.g. {@code BatchBill2Action}) is a
 * historical inconsistency.</p>
 *
 * <h2>What does <em>not</em> belong here</h2>
 *
 * <ul>
 *   <li>DAO calls — go through an assembler or service.</li>
 *   <li>Business-logic computation — same.</li>
 *   <li>HTML rendering — that's the JSP's job.</li>
 * </ul>
 *
 * @since 2026-04-26
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;
