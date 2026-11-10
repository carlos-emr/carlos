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
 * HTML-to-PDF rendering for CARLOS EMR: turns one self-contained HTML document into a PDF inside
 * the JVM, with Flying Saucer on OpenPDF, running no external program and fetching nothing from
 * the network.
 *
 * <p>In MVC terms the module is a view renderer, like a JSP view: it turns a typed model into
 * output and never sees the HTTP request. Controllers (Struts {@code *2Action}s and the managers
 * they call) decide responses and user messages. The legacy facades ({@code ConvertToEdoc},
 * {@code Doc2PDF}) translate paths, build an {@code HtmlPdfRenderRequest} and map the result back
 * to their older contracts. This module renders.</p>
 *
 * <h2>Entry point</h2>
 *
 * <p>Code outside the module uses only {@code HtmlPdfRenderService} and the {@code dto} types: an
 * {@code HtmlPdfRenderRequest} in, an {@code HtmlPdfRenderResult} out. Failure is the last
 * resort. A render that has to give something up (an image, the author's styles, the layout)
 * still returns a PDF and records each step down as an {@code HtmlPdfRenderDegradation} on the
 * result; the service throws the checked {@code PDFGenerationException} only when nothing
 * printable can be produced.</p>
 *
 * <h2>Packages and allowed dependencies</h2>
 *
 * <ul>
 *   <li>{@code dto}: the request, result, source and degradation types. Depends on nothing else
 *       in the module.</li>
 *   <li>{@code engine}: Flying Saucer and OpenPDF extensions created per render (user agent,
 *       font resolver, bundled font set). Depends on nothing else in the module.</li>
 *   <li>{@code support}: static, dependency-free markup rules and printed text. Uses only
 *       {@code dto} and {@code engine} value types.</li>
 *   <li>{@code service}: the Spring beans. The only package that sees all the others; every
 *       class in it except {@code HtmlPdfRenderService} is package-private.</li>
 * </ul>
 *
 * <p>Nothing in the module imports {@code jakarta.servlet}, Struts, a DAO, a manager or a legacy
 * facade, and nothing below the facades knows an HTTP status code. {@code support} and
 * {@code engine} types are public only because {@code service} uses them; treat them as
 * module-internal. {@code HtmlPdfArchitectureUnitTest} enforces these rules by reading the source
 * files.</p>
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li>Beans are few, cohesive, stateless singletons with constructor injection and no I/O in
 *       constructors. Per-render objects (the DOM, the degradation set, the renderer, temporary
 *       directories) stay on the calling thread for one attempt and are never stored in a
 *       field.</li>
 *   <li>Degradations are data: each attempt passes one set to the steps that can degrade and the
 *       result carries the final set. No nulls, sentinel values or exceptions for control
 *       flow.</li>
 *   <li>Limits (render slots, waits, image memory, pixel caps, size thresholds) are
 *       {@code static final} constants in the class that owns them, each with its measured
 *       reason. They are not settings.</li>
 *   <li>The module does no database work and uses no {@code @Transactional}.</li>
 * </ul>
 *
 * <h2>PHI</h2>
 *
 * <p>A document's HTML, images, file names and paths can identify a patient. The module never
 * logs a path, file name, URL, document content or exception message from the rendering
 * libraries; its log lines carry the source, mode, phase, duration, enum names and exception
 * class names only. {@code toString()} of the boundary records prints counts, sizes and names,
 * and validation messages name the failed check, never the value. Local files are read only from
 * the request's allowed directories (plus the render's own private temporary directories), and
 * remote resources are never fetched.</p>
 *
 * @since 2026-10-29
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf;
