/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.webserv.rest.conversion;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PrescriptionTo1;
import org.springframework.stereotype.Component;

/**
 * Converts between domain Drug object and transfer Drug objects.
 * <p>
 * This class represents the transformation between a the SQL schema
 * and the data model that is presented to a client.
 */
@Component
public class PrescriptionConverterImpl extends AbstractConverter<Prescription, PrescriptionTo1>
        implements PrescriptionConverter {

    private static Logger logger = MiscUtils.getLogger();

    /**
     * Converts from a transfer object to a Drug domain object.
     *
     * @param loggedInInfo information regarding the current logged in user.
     * @param t            the transfer object to copy the data from
     * @return a Prescription domain object representing this data.
     * @throws ConversionException if conversion did not complete properly.
     */
    @Override
    public Prescription getAsDomainObject(LoggedInInfo loggedInInfo, PrescriptionTo1 t) {

        Prescription p = new Prescription();

        try {

            p.setProviderNo(t.getProviderNo().toString());
            p.setDemographicId(t.getDemographicNo());
            p.setDatePrescribed(t.getDatePrescribed());
            p.setDatePrinted(t.getDatePrinted());
            p.setTextView(t.getTextView());

        } catch (RuntimeException re) {

            logger.error(re.toString());
            throw new ConversionException();

        }

        return p;

    }

    /**
     * Converts from the Drug domain model object to a serializable Drug transfer
     * object.
     *
     * @param loggedInInfo information for the logged in user (unused).
     * @param p            the Prescription domain object to convert from.
     * @return a serializable transfer object that represents the Drug object
     * @throws ConversionException if the conversion fails.
     */
    @Override
    public PrescriptionTo1 getAsTransferObject(LoggedInInfo loggedInInfo, Prescription p) {

        PrescriptionTo1 t = new PrescriptionTo1();

        try {
            t.setScriptId(p.getId());
            t.setDemographicNo(p.getDemographicId());
            t.setProviderNo(Integer.parseInt(p.getProviderNo()));
            t.setDatePrescribed(p.getDatePrescribed());
            t.setDatePrinted(p.getDatePrinted());
            t.setTextView(p.getTextView());
            t.setReprintCount(p.getReprintCount());

        } catch (RuntimeException e) {

            logger.error(e.toString());
            throw new ConversionException();

        }

        return t;
    }

}
