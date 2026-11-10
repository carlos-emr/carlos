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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.dto;

/**
 * Which kind of document a render is for.
 *
 * <p>The source is written to the render's log line so an operator can tell which workflow
 * degrades. It carries no identifier and does not change how a document is rendered.</p>
 *
 * @since 2026-10-29
 */
public enum HtmlPdfRenderSource {

    /** A form or eForm printed by {@code ConvertToEdoc}. */
    FORM,

    /** A Hospital Report Manager (HRM) report that arrived as HTML. */
    HRM,

    /** The message body of an outgoing email, printed so it can be attached. */
    EMAIL,

    /** A Messenger message printed by {@code Doc2PDF}. */
    MESSENGER
}
