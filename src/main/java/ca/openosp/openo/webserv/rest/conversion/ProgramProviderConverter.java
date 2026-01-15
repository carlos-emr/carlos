//CHECKSTYLE:OFF
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
package ca.openosp.openo.webserv.rest.conversion;

import ca.openosp.openo.PMmodule.dao.ProgramDao;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.commn.dao.SecRoleDao;
import ca.openosp.openo.commn.model.SecRole;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.webserv.rest.to.model.ProgramProviderTo1;
import org.springframework.stereotype.Component;

@Component
/*
 * I see a component-scan for this in applicationContextREST.xml but it's not being autowired..can't figure it out
 * right now..but this is a problem for all these I think.
 */
public class ProgramProviderConverter extends AbstractConverter<ProgramProvider, ProgramProviderTo1> {

    //@Autowired
    private ProgramDao programDao;

    //@Autowired
    private SecRoleDao secRoleDao;

    @Override
    public ProgramProvider getAsDomainObject(LoggedInInfo loggedInInfo, ProgramProviderTo1 t) throws ConversionException {
        throw new RuntimeException("not yet implemented");
    }

    @Override
    public ProgramProviderTo1 getAsTransferObject(LoggedInInfo loggedInInfo, ProgramProvider a) throws ConversionException {
        if (programDao == null) {
            programDao = SpringUtils.getBean(ProgramDao.class);
        }
        if (secRoleDao == null) {
            secRoleDao = SpringUtils.getBean(SecRoleDao.class);
        }
        ProgramProviderTo1 t = new ProgramProviderTo1();
        t.setId(a.getId().intValue());
        t.setProgramId(a.getProgramId().intValue());
        t.setProgram(new ProgramConverter().getAsTransferObject(loggedInInfo, programDao.getProgram(t.getProgramId())));
        t.setProviderNo(a.getProviderNo());
        t.setRoleId(a.getRoleId().intValue());

        SecRole secRole = secRoleDao.find(t.getRoleId());
        t.setRoleName(secRole != null ? secRole.getName() : "N/A");

        return t;
    }

}
