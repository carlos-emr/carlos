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


package io.github.carlos_emr.carlos.prescript.data;

import java.util.Vector;

import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author Jay Gallagher
 */
public class RxInteractionWorker extends Thread {
    RxInteractionData interactionData = null;
    Vector atcCodes = null;

    public RxInteractionWorker() {
    }

    public RxInteractionWorker(RxInteractionData rxInt, Vector v) {
        atcCodes = v;
        interactionData = rxInt;
    }

    public void run() {
        MiscUtils.getLogger().debug("STARTING THREAD");

        long start = System.currentTimeMillis();

        RxDrugData.Interaction[] interactions = null;
        try {
            if (atcCodes != null && interactionData != null) {
                RxDrugData drugData = new RxDrugData();
                interactions = drugData.getInteractions(atcCodes);
                if (interactions != null) {
                    interactionData.addToHash(atcCodes, interactions);
                    interactionData.removeFromWorking(atcCodes);
                } else {
                    MiscUtils.getLogger().debug("What to do");
                    MiscUtils.getLogger().debug("atc codes " + atcCodes);
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            DbConnectionFilter.releaseAllThreadDbResources();
        }
        long end = System.currentTimeMillis() - start;
        MiscUtils.getLogger().debug("THREAD ENDING " + end);
    }

}
