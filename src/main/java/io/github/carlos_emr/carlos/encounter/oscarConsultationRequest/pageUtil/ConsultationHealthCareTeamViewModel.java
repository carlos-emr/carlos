/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The consultants a new consultation request offers from the patient's health care team, when
 * {@code ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS} is on, and what the form tells the user
 * about the members it could not offer (issue #6202).
 *
 * <p>A member is offered when the save would accept it: the same rule as
 * {@link ConsultationConsultantSelection#resolve}, which turns away a deleted, inactive or
 * non-consenting link and any link that is not an external professional contact or a directory
 * specialist. Internal providers are therefore never offered; they carry no address or fax of
 * their own (the details placeholder reads "internal"). A deleted contact or directory specialist
 * is not offered either: {@code DemographicContactCreator} reports the directory row's deleted
 * flag on the details, and leaves no details at all when the target row is gone.
 *
 * <p>A college, MSP or billing number (the contact's {@code cpso} field, or the specialist's
 * referral number) is <em>not</em> required. Until #6202 the form silently dropped every member
 * without one, so a dietitian, a BC specialist known only by an MSP number held elsewhere, or a
 * contact entered without a number could not be chosen, although the save and both printed
 * letters ({@code ProfessionalSpecialist.healthCareTeamConsultant}) already accept them. Those
 * members are now listed and reported in {@link #getWithoutReferralNumber()}, so the form can say
 * that their letter will print without a number.
 *
 * <p>Instances are immutable view state for {@code ConsultationFormRequest.jsp}, built once per
 * render by {@link #from}; the accessors follow bean naming so JSP EL can read them.
 *
 * @since 2026-11-26
 */
public final class ConsultationHealthCareTeamViewModel {

    private final List<DemographicContact> consultants;
    private final Set<ConsultationServices> services;
    private final Set<Integer> withoutReferralNumber;
    private final int withoutConsentCount;

    private ConsultationHealthCareTeamViewModel(List<DemographicContact> consultants,
            Set<ConsultationServices> services, Set<Integer> withoutReferralNumber, int withoutConsentCount) {
        this.consultants = Collections.unmodifiableList(consultants);
        this.services = Collections.unmodifiableSet(services);
        this.withoutReferralNumber = Collections.unmodifiableSet(withoutReferralNumber);
        this.withoutConsentCount = withoutConsentCount;
    }

    /**
     * Sorts the patient's health care team into the members the form offers and the counts it
     * reports.
     *
     * @param team the team with details attached ({@code DemographicManager.getHealthCareTeam});
     *             {@code null} is treated as empty, and {@code null} entries are skipped
     * @param serviceByDescription the consultation service whose description equals a member's
     *             role, or {@code null} when none does. The team stores roles from the
     *             {@code ContactSpecialty} table while the form posts {@code consultationServices}
     *             ids, so this lookup is the only bridge between the two.
     * @return the choices; never {@code null}
     */
    public static ConsultationHealthCareTeamViewModel from(List<DemographicContact> team,
            Function<String, ConsultationServices> serviceByDescription) {
        List<DemographicContact> consultants = new ArrayList<>();
        Set<ConsultationServices> services = new LinkedHashSet<>();
        Set<Integer> withoutNumber = new LinkedHashSet<>();
        int withoutConsent = 0;
        if (team != null) {
            for (DemographicContact member : team) {
                if (!isOfferableLink(member)) continue;
                if (!member.isConsentToContact()) {
                    // The one exclusion the user can fix from the form's own "edit Health Care
                    // Team" link, so it is counted and explained rather than dropped silently.
                    withoutConsent++;
                    continue;
                }
                consultants.add(member);
                if (!hasReferralNumber(member) && member.getId() != null) {
                    withoutNumber.add(member.getId());
                }
                ConsultationServices service = serviceByDescription.apply(member.getRole());
                if (service != null) services.add(service);
            }
        }
        return new ConsultationHealthCareTeamViewModel(consultants, services, withoutNumber, withoutConsent);
    }

    /**
     * Whether the member has a college, MSP or billing number on file. For a directory specialist
     * the details carry its referral number in the {@code cpso} field
     * ({@code DemographicContactCreator.buildContact}).
     *
     * @param member a team member with details attached
     * @return {@code true} when the number is present and not blank
     */
    static boolean hasReferralNumber(DemographicContact member) {
        return member.getDetails() instanceof ProfessionalContact professional
                && StringUtils.isNotBlank(professional.getCpso());
    }

    /**
     * Everything the save checks except consent: a live, active professional link to an external
     * contact or a directory specialist whose details are present and not deleted.
     */
    private static boolean isOfferableLink(DemographicContact member) {
        if (member == null || member.isDeleted() || !member.isActive()) return false;
        if (!DemographicContact.CATEGORY_PROFESSIONAL.equals(member.getCategory())) return false;
        if (member.getType() != DemographicContact.TYPE_CONTACT
                && member.getType() != DemographicContact.TYPE_PROFESSIONALSPECIALIST) return false;
        return member.getDetails() instanceof ProfessionalContact professional && !professional.isDeleted();
    }

    /** @return the members the consultant list offers, in team order */
    public List<DemographicContact> getConsultants() {
        return consultants;
    }

    /** @return the consultation services the offered members' roles map to */
    public Set<ConsultationServices> getServices() {
        return services;
    }

    /**
     * @return the {@code DemographicContact} ids of offered members with no college, MSP or
     *         billing number on file; their referral letter prints without one
     */
    public Set<Integer> getWithoutReferralNumber() {
        return withoutReferralNumber;
    }

    /**
     * @return how many otherwise offerable members are left out because consent to contact is not
     *         recorded for them
     */
    public int getWithoutConsentCount() {
        return withoutConsentCount;
    }
}
