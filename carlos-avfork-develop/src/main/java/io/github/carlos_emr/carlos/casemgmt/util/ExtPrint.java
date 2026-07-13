/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.casemgmt.util;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrintPdf;

import org.openpdf.text.DocumentException;

/**
 * Strategy interface for pluggable print extensions within the case management
 * PDF printing subsystem. Implementations render additional content sections
 * (e.g. measurements, custom forms) into an in-progress PDF document.
 *
 * @see CaseManagementPrintPdf
 * @see io.github.carlos_emr.carlos.casemgmt.service.MeasurementPrint
 * @since 2010-12-10
 */
public interface ExtPrint {

    /**
     * Renders this extension's content into the given PDF print engine.
     *
     * @param engine  CaseManagementPrintPdf the active PDF generation engine
     * @param request HttpServletRequest the current HTTP request containing print parameters
     * @throws IOException       if an I/O error occurs during PDF generation
     * @throws DocumentException if an OpenPDF document error occurs
     */
    public void printExt(CaseManagementPrintPdf engine, HttpServletRequest request) throws IOException, DocumentException;
}
