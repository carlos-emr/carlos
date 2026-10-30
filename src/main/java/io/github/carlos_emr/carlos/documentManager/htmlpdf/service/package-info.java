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
 * The Spring beans of the HTML-to-PDF module: the render pipeline and the services it is built
 * from.
 *
 * <p>This is the only package that sees every other package of the module ({@code dto},
 * {@code support} and {@code engine}). Only {@code HtmlPdfRenderService}, the module's entry point,
 * is public; every other class here is package-private, so nothing outside the module can reach
 * into the pipeline's steps.</p>
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li>Beans are singletons with constructor injection. They hold no per-render state: the
 *       DOM, the degradation set, the renderer and the temporary directories of one render live on
 *       the calling thread for one attempt. The only state a bean keeps is immutable and shared,
 *       such as the verified {@code BundledFontSet} that {@code BundledFontFileService}
 *       publishes.</li>
 *   <li>No constructor does I/O. A bean reads the disk, the class path or the clock when it is
 *       first used.</li>
 *   <li>No servlet, Struts, DAO, manager or legacy facade type is imported here, and no bean is
 *       {@code @Transactional}. The module renders; controllers decide responses.</li>
 *   <li>A bean that has to give something up returns the degradation as data and renders what it
 *       can; only the entry point throws, and only when nothing printable is left.</li>
 * </ul>
 *
 * <h2>PHI</h2>
 *
 * <p>Nothing here logs a path, a file name, a URL, a document's content or an exception message
 * that came from rendering it; logs carry counts, step names and exception class names.</p>
 *
 * @since 2026-10-29
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;
