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
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.io.Serializable;

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Request transfer object for the validateHC endpoint.
 *
 * <p>Carries Health Insurance Number (HIN) and version code in the POST body
 * so that PHI never appears in the URL, server access logs, or browser history.</p>
 *
 * @since 2026-04-02
 */
@XmlRootElement
public class ValidateHCRequestTo1 implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Health Insurance Number (HIN) — PHI, must not be sent via URL. */
    private String hin;

    /** Version code associated with the health card. */
    private String ver;

    public String getHin() {
        return hin;
    }

    public void setHin(String hin) {
        this.hin = hin;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }
}
