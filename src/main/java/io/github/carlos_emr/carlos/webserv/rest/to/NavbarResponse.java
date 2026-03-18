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
package io.github.carlos_emr.carlos.webserv.rest.to;

import java.io.Serializable;
import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSeeAlso;

import io.github.carlos_emr.carlos.webserv.rest.to.model.NavBarMenuTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProgramProviderTo1;

@XmlRootElement
@XmlSeeAlso(ProgramProviderTo1.class)
public class NavbarResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private ProgramProviderTo1 currentProgram;

    private List<ProgramProviderTo1> programDomain;

    private NavBarMenuTo1 menus;

    public ProgramProviderTo1 getCurrentProgram() {
        return currentProgram;
    }

    public void setCurrentProgram(ProgramProviderTo1 currentProgram) {
        this.currentProgram = currentProgram;
    }

    @XmlElement(name = "program", type = ProgramProviderTo1.class)
    @XmlElementWrapper(name = "programDomain")
    public List<ProgramProviderTo1> getProgramDomain() {
        return programDomain;
    }

    public void setProgramDomain(List<ProgramProviderTo1> programDomain) {
        this.programDomain = programDomain;
    }

    public NavBarMenuTo1 getMenus() {
        return menus;
    }

    public void setMenus(NavBarMenuTo1 menus) {
        this.menus = menus;
    }

}
