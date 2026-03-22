/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Billingreferral;

/**
 * DAO interface for healthcare billing operations.
 *
 * @since 2001
 */

public interface BillingreferralDao extends AbstractDao<Billingreferral> {
    /**
     * Get By Referral No.
     *
     * @param referral_no String the referral_no
     * @return Billingreferral
     */
    public Billingreferral getByReferralNo(String referral_no);

    /**
     * Get By Id.
     *
     * @param id int the id
     * @return Billingreferral
     */
    public Billingreferral getById(int id);

    /**
     * Get Billingreferrals.
     * @return List<Billingreferral>
     */
    public List<Billingreferral> getBillingreferrals();

    /**
     * Get Billingreferral.
     *
     * @param referral_no String the referral_no
     * @return List<Billingreferral>
     */
    public List<Billingreferral> getBillingreferral(String referral_no);

    /**
     * Get Billingreferral.
     *
     * @param last_name String the last_name
     * @param first_name String the first_name
     * @return List<Billingreferral>
     */
    public List<Billingreferral> getBillingreferral(String last_name, String first_name);

    /**
     * Get Billingreferral By Last Name.
     *
     * @param last_name String the last_name
     * @return List<Billingreferral>
     */
    public List<Billingreferral> getBillingreferralByLastName(String last_name);

    /**
     * Get Billingreferral By Specialty.
     *
     * @param specialty String the specialty
     * @return List<Billingreferral>
     */
    public List<Billingreferral> getBillingreferralBySpecialty(String specialty);

    /**
     * Search Referral Code.
     *
     * @param codeName String the codeName
     * @param codeName1 String the codeName1
     * @param codeName2 String the codeName2
     * @param desc String the desc
     * @param fDesc String the fDesc
     * @param desc1 String the desc1
     * @param fDesc1 String the fDesc1
     * @param desc2 String the desc2
     * @param fDesc2 String the fDesc2
     * @return List<Billingreferral>
     */
    public List<Billingreferral> searchReferralCode(String codeName, String codeName1, String codeName2, String desc, String fDesc, String desc1, String fDesc1, String desc2, String fDesc2);

    /**
     * Update Billingreferral.
     *
     * @param obj Billingreferral the obj
     */
    public void updateBillingreferral(Billingreferral obj);

    /**
     * Get Referral Doc Name.
     *
     * @param referral_no String the referral_no
     * @return String
     */
    public String getReferralDocName(String referral_no);
}
