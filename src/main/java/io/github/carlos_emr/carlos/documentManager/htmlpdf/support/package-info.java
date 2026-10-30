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
 * The markup rules and printed text of the HTML-to-PDF module, as static utilities.
 *
 * <p>Each class is named for the thing it knows about (a domain noun, not a role) and has only
 * static methods and a private constructor:</p>
 *
 * <ul>
 *   <li>{@link io.github.carlos_emr.carlos.documentManager.htmlpdf.support.XhtmlCleanup}: the rules
 *       that make an HTML document valid, printable XHTML (scripts and comments, invisible
 *       characters, table spans, fixed positioning, image alternative text, serialisation);</li>
 *   <li>the form-control replacements, right-to-left shaping, the fallback-font runs, the injected
 *       stylesheet and the printed note and box texts, which later steps of the pipeline add.</li>
 * </ul>
 *
 * <h2>Allowed dependencies</h2>
 *
 * <p>The JDK, Jsoup, ICU4J and CARLOS's logging utility, plus the value types of {@code dto} and
 * {@code engine} (for example the degradation enum and the bundled font set). Never {@code service}:
 * the Spring beans use these utilities, not the reverse. No servlet, Struts, DAO, manager or legacy
 * facade type is imported here.</p>
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li>A utility changes the Jsoup {@code Document} it is given and nothing else: no file, network
 *       or clock access, no static state, so it is safe to call from concurrent renders on different
 *       documents.</li>
 *   <li>The same document always gives the same result, whatever the server's locale, time zone or
 *       installed fonts.</li>
 *   <li>Nothing here logs document content. Counts are the most a log line may carry.</li>
 * </ul>
 *
 * @since 2026-10-29
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;
