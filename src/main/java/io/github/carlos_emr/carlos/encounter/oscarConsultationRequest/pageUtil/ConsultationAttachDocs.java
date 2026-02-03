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
 */

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Handles logic of attaching documents to a specified consultation
 */
@Deprecated
public class ConsultationAttachDocs extends ConsultationAttach {

    private static final String DOCTYPE = ConsultDocs.DOCTYPE_DOC;

    public ConsultationAttachDocs(String provNo, String demo, String req, String[] d) {
        super(provNo, demo, req, d);
    }

    public void attach(LoggedInInfo loggedInInfo) {
        super.attach(loggedInInfo, DOCTYPE);
    }
}