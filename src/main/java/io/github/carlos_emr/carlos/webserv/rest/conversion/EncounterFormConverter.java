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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EncounterFormTo1;
import org.springframework.beans.BeanUtils;

public class EncounterFormConverter extends AbstractConverter<EncounterForm, EncounterFormTo1> {


    @Override
    public EncounterForm getAsDomainObject(LoggedInInfo loggedInInfo, EncounterFormTo1 t) throws ConversionException {
        EncounterForm d = new EncounterForm();
        BeanUtils.copyProperties(t, d);
        return d;
    }

    @Override
    public EncounterFormTo1 getAsTransferObject(LoggedInInfo loggedInInfo, EncounterForm d) throws ConversionException {
        EncounterFormTo1 t = new EncounterFormTo1();
        BeanUtils.copyProperties(d, t);
        return t;
    }

}
