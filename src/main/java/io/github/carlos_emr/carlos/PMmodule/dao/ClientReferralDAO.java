//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;

public interface ClientReferralDAO {

    public List<ClientReferral> getReferrals();

    public List<ClientReferral> getReferrals(Long clientId);

    public List<ClientReferral> getReferralsByFacility(Long clientId, Integer facilityId);

    // [ 1842692 ] RFQ Feature - temp change for pmm referral history report
    // - suggestion: to add a new field to the table client_referral (Referring program/agency)
    public List<ClientReferral> displayResult(List<ClientReferral> lResult);

    public List<ClientReferral> getActiveReferrals(Long clientId, Integer facilityId);

    public List<ClientReferral> getActiveReferralsByClientAndProgram(Long clientId, Long programId);

    public ClientReferral getClientReferral(Long id);

    public void saveClientReferral(ClientReferral referral);

    public List<ClientReferral> search(ClientReferral referral);

    public List<ClientReferral> getClientReferralsByProgram(int programId);

}
 