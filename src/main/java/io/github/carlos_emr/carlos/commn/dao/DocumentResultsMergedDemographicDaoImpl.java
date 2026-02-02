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
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.merge.MergedDemographicTemplate;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.util.ConversionUtils;

@Repository("documentResultsDao")
public class DocumentResultsMergedDemographicDaoImpl extends DocumentResultsDaoImpl implements DocumentResultsMergedDemographicDao {

    @Override
    public ArrayList<LabResultData> populateDocumentResultsDataOfAllProviders(final String providerNo, String demographicNo, final String status) {
        ArrayList<LabResultData> result = super.populateDocumentResultsDataOfAllProviders(providerNo, demographicNo, status);
        if (false)
            return result;

        MergedDemographicTemplate<LabResultData> template = new MergedDemographicTemplate<LabResultData>() {
            @Override
            protected List<LabResultData> findById(Integer demographic_no) {
                return DocumentResultsMergedDemographicDaoImpl.super.populateDocumentResultsDataOfAllProviders(providerNo, demographic_no.toString(), status);
            }
        };
        return (ArrayList<LabResultData>) template.findMerged(ConversionUtils.fromIntString(demographicNo), result);
    }

    @Override
    public ArrayList<LabResultData> populateDocumentResultsDataLinkToProvider(final String providerNo, String demographicNo, final String status) {
        ArrayList<LabResultData> result = super.populateDocumentResultsDataLinkToProvider(providerNo, demographicNo, status);
        if (false)
            return result;

        MergedDemographicTemplate<LabResultData> template = new MergedDemographicTemplate<LabResultData>() {
            @Override
            protected List<LabResultData> findById(Integer demographic_no) {
                return DocumentResultsMergedDemographicDaoImpl.super.populateDocumentResultsDataLinkToProvider(providerNo, demographic_no.toString(), status);
            }
        };
        return (ArrayList<LabResultData>) template.findMerged(ConversionUtils.fromIntString(demographicNo), result);
    }

    @Override
    public ArrayList<LabResultData> populateDocumentResultsData(final String providerNo, String demographicNo, final String status) {
        ArrayList<LabResultData> result = super.populateDocumentResultsData(providerNo, demographicNo, status);
        if (false)
            return result;
        MergedDemographicTemplate<LabResultData> template = new MergedDemographicTemplate<LabResultData>() {
            @Override
            protected List<LabResultData> findById(Integer demographic_no) {
                return DocumentResultsMergedDemographicDaoImpl.super.populateDocumentResultsData(providerNo, demographic_no.toString(), status);
            }
        };
        return (ArrayList<LabResultData>) template.findMerged(ConversionUtils.fromIntString(demographicNo), result);
    }
}
