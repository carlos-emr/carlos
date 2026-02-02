//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.ScratchPad;
import org.springframework.stereotype.Repository;

import javax.persistence.Query;
import java.util.List;

@Repository
public class ScratchPadDaoImpl extends AbstractDaoImpl<ScratchPad> implements ScratchPadDao {

    public ScratchPadDaoImpl() {
        super(ScratchPad.class);
    }

    @Override
    public boolean isScratchFilled(String providerNo) {
        String sSQL = "SELECT s FROM ScratchPad s WHERE s.providerNo = ?1 AND s.status = true order by s.id";
        Query query = entityManager.createQuery(sSQL);
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<ScratchPad> results = query.getResultList();
        if (results.size() > 0 && results.get(0).getText().trim().length() > 0) {
            return true;
        }
        return false;
    }

    @Override
    public ScratchPad findByProviderNo(String providerNo) {
        Query query = createQuery("sp", "sp.providerNo = :providerNo AND sp.status=true order by sp.id DESC");
        query.setMaxResults(1);
        query.setParameter("providerNo", providerNo);
        return getSingleResultOrNull(query);
    }

    @Override
    public List<ScratchPad> findAllDatesByProviderNo(String providerNo) {
        String sql = "SELECT sp FROM ScratchPad sp " +
                "WHERE sp.providerNo = :providerNo " +
                "  AND sp.status = true " +
                "  AND ( " +
                "    DATE(sp.dateTime) = CURDATE() " +
                "    OR ( " +
                "      DATE(sp.dateTime) < CURDATE() " +
                "      AND NOT EXISTS ( " +
                "        SELECT 1 " +
                "        FROM ScratchPad sp2 " +
                "        WHERE sp2.providerNo = sp.providerNo " +
                "          AND sp2.status = true " +
                "          AND DATE(sp2.dateTime) = DATE(sp.dateTime) " +
                "          AND DATE(sp2.dateTime) < CURDATE() " +
                "          AND ( " +
                "            sp2.dateTime > sp.dateTime " +
                "            OR (sp2.dateTime = sp.dateTime AND sp2.id > sp.id) " +
                "          ) " +
                "      ) " +
                "    ) " +
                "  ) " +
                "ORDER BY sp.dateTime DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter("providerNo", providerNo);
        return query.getResultList();
    }
}
