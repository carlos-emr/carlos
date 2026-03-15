/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.test.builders;

import io.github.carlos_emr.carlos.commn.model.Drug;

import java.util.Date;

/**
 * Test data builder for {@link Drug} entities.
 *
 * <p>Provides deterministic, clinically realistic defaults for prescription drug test data.
 * All default values satisfy NOT NULL constraints and VARCHAR length limits from the
 * database schema.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Drug drug = DrugTestBuilder.aDrug().build();
 * Drug aspirin = DrugTestBuilder.aDrug()
 *     .withBrandName("Aspirin")
 *     .withGenericName("ASA")
 *     .withDemographicId(1)
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class DrugTestBuilder {

    private Integer id;
    private String providerNo = "999990";
    private Integer demographicId = 1;
    private Date rxDate = new Date(1704067200000L); // 2024-01-01
    private Date endDate = new Date(1706745600000L); // 2024-02-01
    private Date writtenDate = new Date(1704067200000L);
    private String brandName = "Amoxicillin";
    private String genericName = "Amoxicillin";
    private String gcnSeqNo = "0";
    private String customName;
    private float takeMin = 1;
    private float takeMax = 2;
    private String freqCode = "BID";
    private String duration = "28";
    private String durUnit = "D";
    private String quantity = "56";
    private Integer repeat = 3;
    private boolean noSubs = false;
    private boolean prn = false;
    private String special = "Take 1-2 tablets by mouth twice daily";
    private String route = "PO";
    private String drugForm = "TAB";
    private String method = "Take";
    private String atc = "J01CA04";
    private String regionalIdentifier = "00123456";
    private boolean archived = false;
    private Date archivedDate;
    private String archivedReason;

    private DrugTestBuilder() {
    }

    public static DrugTestBuilder aDrug() {
        return new DrugTestBuilder();
    }

    public DrugTestBuilder withId(Integer id) {
        this.id = id;
        return this;
    }

    public DrugTestBuilder withProviderNo(String providerNo) {
        this.providerNo = providerNo;
        return this;
    }

    public DrugTestBuilder withDemographicId(Integer demographicId) {
        this.demographicId = demographicId;
        return this;
    }

    public DrugTestBuilder withRxDate(Date rxDate) {
        this.rxDate = rxDate;
        return this;
    }

    public DrugTestBuilder withEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    public DrugTestBuilder withBrandName(String brandName) {
        this.brandName = brandName;
        return this;
    }

    public DrugTestBuilder withGenericName(String genericName) {
        this.genericName = genericName;
        return this;
    }

    public DrugTestBuilder withTakeMin(float takeMin) {
        this.takeMin = takeMin;
        return this;
    }

    public DrugTestBuilder withTakeMax(float takeMax) {
        this.takeMax = takeMax;
        return this;
    }

    public DrugTestBuilder withFreqCode(String freqCode) {
        this.freqCode = freqCode;
        return this;
    }

    public DrugTestBuilder withDuration(String duration) {
        this.duration = duration;
        return this;
    }

    public DrugTestBuilder withDurUnit(String durUnit) {
        this.durUnit = durUnit;
        return this;
    }

    public DrugTestBuilder withQuantity(String quantity) {
        this.quantity = quantity;
        return this;
    }

    public DrugTestBuilder withRepeat(Integer repeat) {
        this.repeat = repeat;
        return this;
    }

    public DrugTestBuilder withPrn(boolean prn) {
        this.prn = prn;
        return this;
    }

    public DrugTestBuilder withSpecial(String special) {
        this.special = special;
        return this;
    }

    public DrugTestBuilder withRoute(String route) {
        this.route = route;
        return this;
    }

    public DrugTestBuilder withDrugForm(String drugForm) {
        this.drugForm = drugForm;
        return this;
    }

    public DrugTestBuilder withMethod(String method) {
        this.method = method;
        return this;
    }

    public DrugTestBuilder withAtc(String atc) {
        this.atc = atc;
        return this;
    }

    public DrugTestBuilder withRegionalIdentifier(String regionalIdentifier) {
        this.regionalIdentifier = regionalIdentifier;
        return this;
    }

    public DrugTestBuilder withArchived(boolean archived) {
        this.archived = archived;
        return this;
    }

    public DrugTestBuilder withArchivedDate(Date archivedDate) {
        this.archivedDate = archivedDate;
        return this;
    }

    public DrugTestBuilder withArchivedReason(String archivedReason) {
        this.archivedReason = archivedReason;
        return this;
    }

    /**
     * Creates an archived drug with standard archive metadata.
     *
     * @return this builder
     */
    public DrugTestBuilder archived() {
        this.archived = true;
        this.archivedDate = new Date(1704067200000L);
        this.archivedReason = "discontinued";
        return this;
    }

    public Drug build() {
        Drug drug = new Drug();
        if (id != null) {
            setIdViaReflection(drug, id);
        }
        drug.setProviderNo(providerNo);
        drug.setDemographicId(demographicId);
        drug.setRxDate(rxDate);
        drug.setEndDate(endDate);
        drug.setWrittenDate(writtenDate);
        drug.setBrandName(brandName);
        drug.setGenericName(genericName);
        drug.setGcnSeqNo(gcnSeqNo);
        drug.setCustomName(customName);
        drug.setTakeMin(takeMin);
        drug.setTakeMax(takeMax);
        drug.setFreqCode(freqCode);
        drug.setDuration(duration);
        drug.setDurUnit(durUnit);
        drug.setQuantity(quantity);
        drug.setRepeat(repeat);
        drug.setNoSubs(noSubs);
        drug.setPrn(prn);
        drug.setSpecial(special);
        drug.setRoute(route);
        drug.setDrugForm(drugForm);
        drug.setMethod(method);
        drug.setAtc(atc);
        drug.setRegionalIdentifier(regionalIdentifier);
        drug.setArchived(archived);
        drug.setArchivedDate(archivedDate);
        drug.setArchivedReason(archivedReason);
        return drug;
    }

    private void setIdViaReflection(Drug drug, Integer id) {
        try {
            java.lang.reflect.Field field = Drug.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(drug, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set drug ID", e);
        }
    }
}
