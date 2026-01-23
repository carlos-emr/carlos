//CHECKSTYLE:OFF
/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2026.
 */

package ca.openosp.openo.managers;

import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.utility.LoggedInInfo;

import java.io.IOException;

/**
 * Service interface for PDF markup and annotation operations.
 * Provides functionality to flatten Fabric.js annotations into PDF documents
 * and save them as new documents in the patient's chart.
 *
 * @since 2026-01-23
 */
public interface PdfMarkupManager {

    /**
     * Creates an annotated copy of an existing PDF document by flattening
     * Fabric.js canvas annotations into the PDF.
     *
     * @param info LoggedInInfo containing the logged-in user information for audit and security
     * @param originalDocNo Integer the document ID of the original PDF to annotate
     * @param demographicNo Integer the patient demographic number to link the new document to
     * @param annotationsJson String JSON representation of Fabric.js annotations per page
     * @return Document the newly created Document entity containing the annotated PDF
     * @throws IOException if PDF processing or file operations fail
     * @throws SecurityException if the user does not have write access to documents
     * @throws IllegalArgumentException if the original document is not found or invalid
     */
    Document createAnnotatedCopy(LoggedInInfo info, Integer originalDocNo,
                                  Integer demographicNo, String annotationsJson) throws IOException;
}
