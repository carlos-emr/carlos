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
 * <p>Each public class is named for the thing it knows about (a domain noun, not a role) and has
 * only static methods and a private constructor:</p>
 *
 * <ul>
 *   <li>{@code XhtmlCleanup}: the rules that make an HTML document valid, printable XHTML (scripts
 *       and comments, invisible characters, table spans, fixed positioning, image alternative
 *       text, serialisation);</li>
 *   <li>{@code PrintFormControls}: form controls become static text and marks;</li>
 *   <li>{@code RtlTextShaping}: best-effort shaping and reordering of right-to-left text;</li>
 *   <li>{@code FallbackFontRuns}: spans that name a fallback font for the characters DejaVu Sans
 *       lacks;</li>
 *   <li>{@code PrintStylesheet}: the style sheet injected into every document, and the
 *       {@code @page} rule of the second layout pass;</li>
 *   <li>{@code LegacyBodyMargins}: the body's {@code leftmargin} and {@code topmargin} attributes,
 *       which WebKit read and Flying Saucer ignores, as a rule for that style sheet;</li>
 *   <li>{@code PrintMessages}: the document notes and the box label printed into a document, read
 *       from the {@code oscarResources} bundle;</li>
 *   <li>{@code SystemColors}: the CSS2 system colours Word writes ({@code windowtext}), which Flying
 *       Saucer drops a whole declaration for, become the colours of the default Windows theme;</li>
 *   <li>{@code SafeModeMarkup} and {@code PlainTextDocument}: what safe mode (no author styles, no
 *       inline graphics) and text-only mode (the text in reading order) make of a document;</li>
 *   <li>{@code HiddenContent}: the elements the markup hid ({@code hidden}, {@code display: none},
 *       {@code visibility: hidden}), removed before safe and text-only mode drop the styles that hide them;</li>
 *   <li>{@code EngineExtensions}: Flying Saucer's own {@code -fs-} CSS properties, removed from the
 *       CSS a document brings (a span property would otherwise reach the layout unclamped);</li>
 *   <li>{@code ImageBox}, {@code SvgReferences} and {@code SvgViewBox}: the "[image unavailable]" box, the
 *       removal of the outside references of an SVG (and of an inline picture that declares more pixels
 *       than Batik should decode), and the size of an inline SVG that gives only a viewBox.</li>
 * </ul>
 *
 * <p>Two package-private classes serve them: {@code InlineStyle} (a scanner for the inline
 * {@code style} attribute) and {@code HtmlAttributeNumbers} (HTML integer attributes read as a
 * browser reads them).</p>
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
 *   <li>A rule changes the Jsoup {@code Document} it is given and nothing else ({@code PrintStylesheet},
 *       {@code PrintMessages} and {@code LegacyBodyMargins} return text). There is no file, network or clock access beyond
 *       reading the English message bundle from the class path, and no mutable static state, so the
 *       rules are safe to call from concurrent renders on different documents.</li>
 *   <li>The same document always gives the same result, whatever the server's locale, time zone or
 *       installed fonts.</li>
 *   <li>Nothing here logs document content. Counts are the most a log line may carry.</li>
 * </ul>
 *
 * @since 2026-10-29
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;
