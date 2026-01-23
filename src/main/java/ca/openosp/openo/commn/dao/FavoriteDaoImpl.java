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
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.dao.FavoriteDao;
import ca.openosp.openo.commn.model.Favorite;
import org.springframework.stereotype.Repository;

import javax.persistence.Query;
import java.util.List;

/**
 * Data Access Object implementation for managing Favorite prescription entries.
 *
 * <p>This DAO provides persistence operations for healthcare provider favorite prescriptions,
 * allowing providers to save commonly prescribed medications, dosages, and instructions
 * for quick access during prescription writing workflows.</p>
 *
 * <p>Favorites include complete prescription details such as drug names, dosages, frequencies,
 * duration, special instructions, and custom settings. This improves efficiency and consistency
 * in prescription workflows.</p>
 *
 * @since 2024-07-29
 */
@Repository
public class FavoriteDaoImpl extends AbstractDaoImpl<Favorite> implements FavoriteDao {

    /**
     * Constructs a new FavoriteDaoImpl.
     *
     * <p>Initializes the DAO with the Favorite entity class for JPA operations.</p>
     */
    public FavoriteDaoImpl() {
        super(Favorite.class);
    }

    /**
     * Retrieves all favorite prescriptions for a specific healthcare provider.
     *
     * <p>Returns favorites ordered alphabetically by name for easy lookup and selection.</p>
     *
     * @param providerNo String the unique provider identifier
     * @return List&lt;Favorite&gt; list of favorite prescriptions ordered by name
     */
    @SuppressWarnings("unchecked")
    public List<Favorite> findByProviderNo(String providerNo) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " f WHERE f.providerNo = :providerNo ORDER BY f.name");
        query.setParameter("providerNo", providerNo);
        return query.getResultList();
    }

    /**
     * Finds an exact matching favorite prescription based on all prescription attributes.
     *
     * <p>This method performs an exact match query using all prescription fields to determine
     * if a favorite with identical characteristics already exists. This prevents duplicate
     * favorites and allows precise lookup of existing entries.</p>
     *
     * <p>The matching criteria includes drug identification (brand name, GCN sequence number,
     * generic name, custom name), dosage information (take min/max, frequency, duration),
     * quantity and refill settings, and special instructions.</p>
     *
     * @param providerNo String the unique provider identifier
     * @param favoriteName String the user-assigned name for this favorite
     * @param bn String the brand name of the medication
     * @param gcn_SEQNO String the Generic Code Number sequence identifier
     * @param customName String custom medication name if not using standard drug reference
     * @param takeMin float minimum dosage amount
     * @param takeMax float maximum dosage amount
     * @param frequencyCode String medication frequency code (e.g., "BID", "TID")
     * @param duration String duration of prescription (numeric value)
     * @param durationUnit String unit for duration (e.g., "days", "weeks", "months")
     * @param quantity String quantity to dispense
     * @param repeat int number of refills allowed
     * @param nosubsInt boolean true if no substitutions allowed, false otherwise
     * @param prnInt boolean true if medication is PRN (as needed), false for scheduled
     * @param parsedSpecial String special instructions or notes
     * @param gn String generic name of the medication
     * @param unitName String dosage unit name (e.g., "mg", "mL", "tablet")
     * @param customInstr boolean true if using custom instructions, false for standard
     * @return Favorite the matching favorite entry, or null if no exact match found
     */
    @Override
	public Favorite findByEverything(String providerNo, String favoriteName, String bn, String gcn_SEQNO, String customName, float takeMin, float takeMax, String frequencyCode, String duration, String durationUnit, String quantity, int repeat, boolean nosubsInt, boolean prnInt, String parsedSpecial, String gn, String unitName, boolean customInstr) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " f WHERE f.providerNo = :providerNo AND f.name = :favoritename AND f.bn = :brandName AND f.gcnSeqno = :gcnSeqNo AND f.customName = :customName AND f.takeMin = :takemin AND f.takeMax = :takemax AND f.frequencyCode = :freqcode AND f.duration = :duration "
                + "AND f.durationUnit = :durunit AND f.quantity = :quantity AND f.repeat = :repeat AND f.nosubs = :nosubs AND f.prn = :prn AND f.special = :special AND f.gn = :gn AND f.unitName = :unitName AND " + "f.customInstructions = :customInstructions");

        query.setParameter("providerNo", providerNo);
        query.setParameter("favoritename", favoriteName);
        query.setParameter("brandName", bn);
		query.setParameter("gcnSeqNo", gcn_SEQNO);
        query.setParameter("customName", customName);
        query.setParameter("takemin", takeMin);
        query.setParameter("takemax", takeMax);
        query.setParameter("freqcode", frequencyCode);
        query.setParameter("duration", duration);
        query.setParameter("durunit", durationUnit);
        query.setParameter("quantity", quantity);
        query.setParameter("repeat", repeat);
        query.setParameter("nosubs", nosubsInt);
        query.setParameter("prn", prnInt);
        query.setParameter("special", parsedSpecial);
        query.setParameter("gn", gn);
        query.setParameter("unitName", unitName);
        query.setParameter("customInstructions", customInstr);

        return getSingleResultOrNull(query);
    }
}
