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
 * Extensions of Flying Saucer and OpenPDF, and the image helpers they share, for the HTML-to-PDF
 * module.
 *
 * <p>This is the layer closest to the rendering libraries. It holds the types that extend Flying
 * Saucer and OpenPDF classes, and the static image helpers that those extensions and the image
 * planning in {@code service} share. The image helpers are:</p>
 *
 * <ul>
 *   <li>{@link ImageHeader}: the format and declared size of an image, read from its header;</li>
 *   <li>{@link ImageDecoding}: header reads that never decode a pixel, the subsampling arithmetic,
 *       streamed subsampled reads and EXIF orientation, for a short list of vetted formats only (PNG,
 *       JPEG, GIF, BMP, WBMP and TIFF), read only by vetted readers (the JDK's, and the TwelveMonkeys
 *       TIFF reader); any other ImageIO reader is never asked about the input.</li>
 * </ul>
 *
 * <p>The extensions are {@code LocalOnlyUserAgent} (no network, files only from the allowed directories,
 * images vetted) and {@code BoundedOutputDevice}, which makes the bitmap of an SVG that Flying Saucer
 * cannot draw as vectors once, within a bound of pixels, and leaves out an SVG that Batik cannot
 * draw instead of failing the page.</p>
 *
 * <h2>Allowed dependencies</h2>
 *
 * <p>Nothing else in the module. No type from {@code dto}, {@code support} or {@code service} is
 * imported here (the {@code dto} package may be named in prose, never in code), so the dependency
 * graph stays acyclic: {@code service} and {@code support} use {@code engine}, never the reverse.
 * Outside the module the package may use the JDK, ImageIO with the TwelveMonkeys metadata classes,
 * Flying Saucer, OpenPDF and CARLOS's logging utility. It imports no {@code jakarta.servlet}, Struts,
 * DAO, manager or legacy facade type.</p>
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Nothing here is a Spring bean. Extensions of the rendering libraries are created per render
 * and never stored in a field of a shared object; the helpers are static and stateless, and value
 * types such as {@link ImageHeader} are immutable, so all of it is safe to call from concurrent
 * renders. Types are public only because {@code service} uses them; treat them as
 * module-internal.</p>
 *
 * <h2>PHI</h2>
 *
 * <p>The files and bytes handled here are patients' documents. The image helpers log nothing and
 * their exception messages never contain a path, a file name or file content, so a caller that
 * logs an exception class name only, as the module requires, cannot leak either.</p>
 *
 * @since 2026-10-29
 */
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;
