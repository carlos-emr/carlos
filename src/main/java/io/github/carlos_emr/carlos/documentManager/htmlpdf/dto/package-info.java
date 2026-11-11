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
 * Boundary types of the HTML-to-PDF module: what callers send to {@code HtmlPdfRenderService} and
 * what they get back.
 *
 * <ul>
 *   <li>{@link HtmlPdfRenderRequest}: the validated input (HTML, base directory, allowed
 *       directories, source);</li>
 *   <li>{@link HtmlPdfRenderResult}: the PDF bytes and the degradations the render took;</li>
 *   <li>{@link HtmlPdfRenderSource}: which kind of document it is, for the log line;</li>
 *   <li>{@link HtmlPdfRenderDegradation}: each reportable step down, with the level it is
 *       logged at.</li>
 * </ul>
 *
 * <p>This package depends on nothing else in the module: no {@code service}, {@code support} or
 * {@code engine} type appears in any signature here. The one type from outside the JDK is
 * Log4j's {@code Level}, the return type of {@link HtmlPdfRenderDegradation#logLevel()}. It keeps
 * each degradation's severity beside its definition instead of in a second enum the service would
 * have to map again.</p>
 *
 * <p>Both records are immutable. Their constructors validate (an invalid request is a programming
 * error: {@code NullPointerException} or {@code IllegalArgumentException}) and copy every mutable
 * input: list, array and set. Their {@code toString()} prints counts, sizes and enum names only,
 * never HTML, PDF bytes or paths, so logging one cannot write PHI.</p>
 *
 * @since 2026-10-29
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.dto;
