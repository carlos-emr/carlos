/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Integration tests for the document result builders of {@link DocumentResultsDao}.
 *
 * <p>The three builders return the same {@link LabResultData} rows the Inbox views render, and
 * every view shows a document row's {@code description}. The builders never set it, so any row
 * they built had none (#5685). The Inbox Hub list itself is built by {@link InboxResultsDao};
 * {@code InboxResultsDaoIntegrationTest} covers its description column.</p>
 *
 * <p>The test uses the production {@code documentResultsDao} bean,
 * {@link DocumentResultsMergedDemographicDaoImpl}, which adds merged patients' rows to what the
 * builders return.</p>
 *
 * @since 2026-11-05
 * @see DocumentResultsDaoImpl
 */
@DisplayName("DocumentResultsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("inbox")
@Tag("read")
@Transactional
public class DocumentResultsDaoIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "999002";

    @Autowired
    private DocumentResultsDao documentResultsDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private int routeDocument(String doctype, String docdesc) {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date observed = cal.getTime();

        Document doc = new Document();
        doc.setDoctype(doctype);
        doc.setDocdesc(docdesc);
        doc.setDocfilename("test.pdf");
        doc.setDoccreator(PROVIDER_NO);
        doc.setResponsible(PROVIDER_NO);
        doc.setStatus('A');
        doc.setContenttype("application/pdf");
        doc.setPublic1(0);
        doc.setNumberofpages(1);
        doc.setObservationdate(observed);
        doc.setUpdatedatetime(observed);
        doc.setContentdatetime(observed);
        entityManager.persist(doc);

        ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
        routing.setProviderNo(PROVIDER_NO);
        routing.setLabNo(doc.getDocumentNo());
        routing.setLabType("DOC");
        routing.setStatus("N");
        routing.setTimestamp(observed);
        entityManager.persist(routing);
        entityManager.flush();
        return doc.getDocumentNo();
    }

    private List<LabResultData> build(String builder) {
        return switch (builder) {
            case "ofAllProviders" -> documentResultsDao.populateDocumentResultsDataOfAllProviders(PROVIDER_NO, null, "N");
            case "linkToProvider" -> documentResultsDao.populateDocumentResultsDataLinkToProvider(PROVIDER_NO, null, "N");
            case "providerOrUnassigned" -> documentResultsDao.populateDocumentResultsData(PROVIDER_NO, null, "N");
            default -> throw new IllegalArgumentException(builder);
        };
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ofAllProviders", "linkToProvider", "providerOrUnassigned"})
    @DisplayName("should carry each document's saved description, and none for a blank one")
    void shouldCarrySavedDescription_inEveryDocumentResultBuilder(String builder) {
        int described = routeDocument("lab", "FAKE discharge summary");
        int undescribed = routeDocument("consult", "");
        Set<String> owned = Set.of(String.valueOf(described), String.valueOf(undescribed));

        List<LabResultData> result = build(builder);

        assertThat(result).filteredOn(row -> owned.contains(row.segmentID))
                .extracting(row -> row.segmentID, row -> row.description)
                .containsExactlyInAnyOrder(
                        tuple(String.valueOf(described), "FAKE discharge summary"),
                        tuple(String.valueOf(undescribed), null));
    }
}
