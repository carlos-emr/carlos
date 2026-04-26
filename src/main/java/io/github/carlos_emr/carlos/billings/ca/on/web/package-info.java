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
 * <p>All actions end in {@code *2Action} per the migration convention
 * established in PR #1632. {@code View*2Action} = read-only render gates.
 * {@code *Save2Action} / {@code Update*2Action} = mutation gates (POST-only).
 * The bare-no-prefix {@code *2Action} (e.g. {@code BatchBill2Action}) is a
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
