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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Base class for defining conversion between domain model objects and transfer objects
 *
 * @param <D> Domain object type
 * @param <T> Transfer object type
 */
// TODO define TO interface and bound T by that
public abstract class AbstractConverter<D, T> {


    public abstract D getAsDomainObject(LoggedInInfo loggedInInfo, T t) throws ConversionException;


    public abstract T getAsTransferObject(LoggedInInfo loggedInInfo, D d) throws ConversionException;


    public List<D> getAllAsDomainObjects(LoggedInInfo loggedInInfo, List<T> ts) throws ConversionException {
        List<D> result = new ArrayList<D>();
        for (T t : ts) {
            result.add(getAsDomainObject(loggedInInfo, t));
        }
        return result;
    }


    public List<T> getAllAsTransferObjects(LoggedInInfo loggedInInfo, List<D> ds) throws ConversionException {
        List<T> result = new ArrayList<T>();
        for (D d : ds) {
            result.add(getAsTransferObject(loggedInInfo, d));
        }
        return result;
    }

}
