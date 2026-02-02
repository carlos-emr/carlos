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

package io.github.carlos_emr.carlos.dxresearch.bean;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DxDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class dxCodeSearchBeanHandler {

    List<dxCodeSearchBean> dxCodeSearchBeanVector = new ArrayList<dxCodeSearchBean>();

    public dxCodeSearchBeanHandler(String codeType, String[] keywords) {
        init(codeType, keywords);
    }

    public boolean init(String codingSystem, String[] keywords) {
        DxDao dao = SpringUtils.getBean(DxDao.class);

        for (Object[] o : dao.findCodingSystemDescription(codingSystem, keywords)) {
            String cs = String.valueOf(o[0]);
            String desc = String.valueOf(o[1]);
            dxCodeSearchBean bean = new dxCodeSearchBean(desc, cs);
            for (int i = 0; i < keywords.length; i++) {
                if (keywords[i].equals(cs)) bean.setExactMatch("checked");
            }

            dxCodeSearchBeanVector.add(bean);
        }
        return true;
    }

    public List<dxCodeSearchBean> getDxCodeSearchBeanVector() {
        return dxCodeSearchBeanVector;
    }
}
