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

package ca.openosp.openo.providers.bean;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.MyGroupDao;
import ca.openosp.openo.commn.model.MyGroup;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.SpringUtils;

public class ProviderNameBeanHandler {

    Vector<ProviderNameBean> providerNameVector = new Vector<ProviderNameBean>();
    Vector<ProviderNameBean> doctorNameVector = new Vector<ProviderNameBean>();
    Vector<ProviderNameBean> thisGroupProviderVector = new Vector<ProviderNameBean>();

    public ProviderNameBeanHandler() {
        init();
    }

    public boolean init() {
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        for (Provider p : dao.getProviders()) {
            ProviderNameBean pNameBean = new ProviderNameBean(p.getFormattedName(), p.getProviderNo());
            providerNameVector.add(pNameBean);
            if (p.getProviderType().equalsIgnoreCase("doctor")) {
                doctorNameVector.add(pNameBean);
            }
        }
        return true;
    }

    public Collection<ProviderNameBean> getProviderNameVector() {
        return providerNameVector;
    }

    public Collection<ProviderNameBean> getDoctorNameVector() {
        return doctorNameVector;
    }

    @SuppressWarnings("unchecked")
    public void setThisGroupProviderVector(String groupNo) {
        MyGroupDao dao = SpringUtils.getBean(MyGroupDao.class);
        List<MyGroup> groups = dao.getGroupByGroupNo(groupNo);
        Collections.sort(groups, Comparator.comparing(MyGroup::getFirstName));
        for (MyGroup g : groups) {
            ProviderNameBean pNameBean = new ProviderNameBean(g.getLastName() + ", " + g.getFirstName(), g.getId().getProviderNo());
            thisGroupProviderVector.add(pNameBean);
        }
    }

    public Collection<ProviderNameBean> getThisGroupProviderVector() {
        return thisGroupProviderVector;
    }
}
