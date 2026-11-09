/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.util.Map;
import static java.util.Map.entry;

/** Declared calendar semantics for legacy java.util.Date properties in the REST response graph.
 * Timestamp properties continue to declare JsonFormat.NUMBER. Uncontracted standalone Date values
 * retain the legacy module behavior; a property name alone never determines its semantics.
 */
final class RestDateSemantics {
    enum Kind { DATE, TIME }
    private static final String BASE = "io.github.carlos_emr.carlos.";
    private static final Map<String, Kind> DECLARED = Map.ofEntries(
            entry("webserv.rest.to.model.AllergyTo1.entryDate", Kind.DATE),
            entry("webserv.rest.to.model.AppointmentExtTo.appointmentDate", Kind.DATE),
            entry("webserv.rest.to.model.AppointmentExtTo.startTime", Kind.TIME),
            entry("webserv.rest.to.model.AppointmentTo1.appointmentDate", Kind.DATE),
            entry("webserv.rest.to.model.AppointmentTo1.startTime", Kind.TIME),
            entry("webserv.rest.to.model.AppointmentTo1.endTime", Kind.TIME),
            entry("webserv.rest.to.model.BillingDetailTo1.admissionDate", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationRequestExtTo1.dateCreated", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationRequestSearchResult.lastFollowUp", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationRequestSearchResult.referralDate", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationResponseSearchResult.lastFollowUp", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationResponseSearchResult.referralDate", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationResponseSearchResult.responseDate", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationResponseTo1.appointmentDate", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationResponseTo1.appointmentTime", Kind.TIME),
            entry("webserv.rest.to.model.ConsultationResponseTo1.followUpDate", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationResponseTo1.referralDate", Kind.DATE),
            entry("webserv.rest.to.model.ConsultationResponseTo1.responseDate", Kind.DATE),
            entry("webserv.rest.to.model.DemographicMergedTo1.lastUpdateDate", Kind.DATE),
            entry("webserv.rest.to.model.DemographicTo1.dateJoined", Kind.DATE),
            entry("webserv.rest.to.model.DemographicTo1.effDate", Kind.DATE),
            entry("webserv.rest.to.model.DemographicTo1.endDate", Kind.DATE),
            entry("webserv.rest.to.model.DemographicTo1.hcRenewDate", Kind.DATE),
            entry("webserv.rest.to.model.DemographicTo1.patientStatusDate", Kind.DATE),
            entry("webserv.rest.to.model.DemographicTo1.rosterDate", Kind.DATE),
            entry("webserv.rest.to.model.DemographicTo1.rosterTerminationDate", Kind.DATE),
            entry("webserv.rest.to.model.DocumentTo1.observationDate", Kind.DATE),
            entry("webserv.rest.to.model.DrugTo1.endDate", Kind.DATE),
            entry("webserv.rest.to.model.DrugTo1.rxDate", Kind.DATE),
            entry("webserv.rest.to.model.DrugTo1.writtenDate", Kind.DATE),
            entry("webserv.rest.to.model.EFormReportToolTo1.expiryDate", Kind.DATE),
            entry("webserv.rest.to.model.EFormTo1.formDate", Kind.DATE),
            entry("webserv.rest.to.model.EFormTo1.formTime", Kind.TIME),
            entry("webserv.rest.to.model.FormTo1.date", Kind.DATE),
            entry("webserv.rest.to.model.NoteExtTo1.dateValue", Kind.DATE),
            entry("webserv.rest.to.model.PrescriptionTo1.datePrescribed", Kind.DATE),
            entry("webserv.rest.to.model.PrescriptionTo1.datePrinted", Kind.DATE),
            entry("webserv.rest.to.model.PreventionTo1.nextDate", Kind.DATE),
            entry("webserv.rest.to.model.ProviderPeriodAppsTo.appointmentDate", Kind.DATE),
            entry("webserv.rest.to.model.ProviderTo1.dob", Kind.DATE),
            entry("webserv.transfer_objects.ProviderTransfer.dob", Kind.DATE),
            entry("commn.model.BillingONItem.serviceDate", Kind.DATE),
            entry("commn.model.Demographic.dateJoined", Kind.DATE),
            entry("commn.model.Demographic.effDate", Kind.DATE),
            entry("commn.model.Demographic.endDate", Kind.DATE),
            entry("commn.model.Demographic.hcRenewDate", Kind.DATE),
            entry("commn.model.Demographic.patientStatusDate", Kind.DATE),
            entry("commn.model.Demographic.rosterDate", Kind.DATE),
            entry("commn.model.Demographic.rosterTerminationDate", Kind.DATE),
            entry("commn.model.Provider.dob", Kind.DATE)
    );
    static Kind find(Class<?> owner, String property) {
        String name = owner.getName();
        return name.startsWith(BASE) ? DECLARED.get(name.substring(BASE.length()) + "." + property) : null;
    }
    private RestDateSemantics() { }
}
