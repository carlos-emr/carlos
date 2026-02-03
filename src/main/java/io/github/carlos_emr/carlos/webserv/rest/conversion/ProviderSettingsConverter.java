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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.managers.model.ProviderSettings;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProviderSettingsTo1;
import org.springframework.beans.BeanUtils;

public class ProviderSettingsConverter extends AbstractConverter<ProviderSettings, ProviderSettingsTo1> {

    @Override
    public ProviderSettings getAsDomainObject(LoggedInInfo loggedInInfo, ProviderSettingsTo1 t) throws ConversionException {
        ProviderSettings d = new ProviderSettings();
        BeanUtils.copyProperties(t, d);
        return d;
    }

    @Override
    public ProviderSettingsTo1 getAsTransferObject(LoggedInInfo loggedInInfo, ProviderSettings d) throws ConversionException {
        ProviderSettingsTo1 t = new ProviderSettingsTo1();
        BeanUtils.copyProperties(d, t);
        return t;
    }
}
