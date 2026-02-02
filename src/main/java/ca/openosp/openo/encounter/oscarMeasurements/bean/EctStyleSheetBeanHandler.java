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

package ca.openosp.openo.encounter.oscarMeasurements.bean;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import ca.openosp.openo.commn.dao.MeasurementCSSLocationDao;
import ca.openosp.openo.commn.model.MeasurementCSSLocation;
import ca.openosp.openo.utility.SpringUtils;

public class EctStyleSheetBeanHandler {

    Vector<EctStyleSheetBean> styleSheetNameVector = new Vector<EctStyleSheetBean>();

    public EctStyleSheetBeanHandler() {
        init();
    }

    @SuppressWarnings("unchecked")
    public boolean init() {
        MeasurementCSSLocationDao dao = SpringUtils.getBean(MeasurementCSSLocationDao.class);
        List<MeasurementCSSLocation> ms = dao.findAll();
        Collections.sort(ms, Comparator.comparing(MeasurementCSSLocation::getId));
        for (MeasurementCSSLocation l : ms) {
            EctStyleSheetBean location = new EctStyleSheetBean(l.getLocation(), l.getId());
            styleSheetNameVector.add(location);
        }
        return true;
    }

    public Collection<EctStyleSheetBean> getStyleSheetNameVector() {
        return styleSheetNameVector;
    }
}
