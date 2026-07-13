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

import io.github.carlos_emr.carlos.commn.model.Allergy;

import java.util.Date;

/**
 * Test data builder for {@link Allergy} entities.
 *
 * <p>Provides deterministic, clinically realistic defaults for allergy test data.
 * Supports both drug and non-drug allergies with severity and reaction metadata.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Allergy allergy = AllergyTestBuilder.anAllergy().build();
 * Allergy penicillin = AllergyTestBuilder.anAllergy()
 *     .withDescription("Penicillin")
 *     .withSeverityOfReaction("2")
 *     .withReaction("Anaphylaxis")
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class AllergyTestBuilder {

    private Integer demographicNo = 1;
    private Date entryDate = new Date(1704067200000L); // 2024-01-01
    private String description = "Penicillin";
    private String reaction = "Rash";
    private boolean archived = false;
    private Boolean nonDrug = false;
    private Integer hiclSeqno = 0;
    private Integer hicSeqno = 0;
    private Integer agcsp = 0;
    private Integer agccs = 0;
    private Integer typeCode = 13;
    private String drugrefId = "";
    private Date startDate = new Date(1704067200000L); // 2024-01-01
    private String ageOfOnset;
    private String severityOfReaction = "1";
    private String onsetOfReaction = "1";
    private String regionalIdentifier;
    private String atc;
    private String lifeStage;
    private int position = 0;

    private AllergyTestBuilder() {
    }

    /**
     * Creates a new builder with clinically realistic defaults for a drug allergy.
     *
     * @return a new builder instance
     */
    public static AllergyTestBuilder anAllergy() {
        return new AllergyTestBuilder();
    }

    public AllergyTestBuilder withDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
        return this;
    }

    public AllergyTestBuilder withEntryDate(Date entryDate) {
        this.entryDate = entryDate;
        return this;
    }

    public AllergyTestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public AllergyTestBuilder withReaction(String reaction) {
        this.reaction = reaction;
        return this;
    }

    public AllergyTestBuilder withArchived(boolean archived) {
        this.archived = archived;
        return this;
    }

    public AllergyTestBuilder withNonDrug(Boolean nonDrug) {
        this.nonDrug = nonDrug;
        return this;
    }

    public AllergyTestBuilder withTypeCode(Integer typeCode) {
        this.typeCode = typeCode;
        return this;
    }

    public AllergyTestBuilder withSeverityOfReaction(String severityOfReaction) {
        this.severityOfReaction = severityOfReaction;
        return this;
    }

    public AllergyTestBuilder withOnsetOfReaction(String onsetOfReaction) {
        this.onsetOfReaction = onsetOfReaction;
        return this;
    }

    public AllergyTestBuilder withRegionalIdentifier(String regionalIdentifier) {
        this.regionalIdentifier = regionalIdentifier;
        return this;
    }

    public AllergyTestBuilder withAtc(String atc) {
        this.atc = atc;
        return this;
    }

    public AllergyTestBuilder withStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    public AllergyTestBuilder withPosition(int position) {
        this.position = position;
        return this;
    }

    /**
     * Creates an archived (inactive) allergy.
     *
     * @return this builder
     */
    public AllergyTestBuilder archived() {
        this.archived = true;
        return this;
    }

    /**
     * Creates a non-drug allergy (e.g., environmental, food).
     *
     * @return this builder
     */
    public AllergyTestBuilder nonDrug() {
        this.nonDrug = true;
        this.description = "Latex";
        this.reaction = "Contact dermatitis";
        return this;
    }

    public Allergy build() {
        Allergy a = new Allergy();
        a.setDemographicNo(demographicNo);
        a.setEntryDate(entryDate);
        a.setDescription(description);
        a.setReaction(reaction);
        a.setArchived(archived);
        a.setNonDrug(nonDrug);
        a.setHiclSeqno(hiclSeqno);
        a.setHicSeqno(hicSeqno);
        a.setAgcsp(agcsp);
        a.setAgccs(agccs);
        a.setTypeCode(typeCode);
        a.setDrugrefId(drugrefId);
        a.setStartDate(startDate);
        a.setAgeOfOnset(ageOfOnset);
        a.setSeverityOfReaction(severityOfReaction);
        a.setOnsetOfReaction(onsetOfReaction);
        a.setRegionalIdentifier(regionalIdentifier);
        a.setAtc(atc);
        a.setLifeStage(lifeStage);
        a.setPosition(position);
        return a;
    }
}
