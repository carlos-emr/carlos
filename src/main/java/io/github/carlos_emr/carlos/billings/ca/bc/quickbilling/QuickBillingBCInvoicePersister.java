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

package io.github.carlos_emr.carlos.billings.ca.bc.quickbilling;

import java.util.Date;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingHistory;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingNotes;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits each quick billing invoice, its claim lines and audit records as one unit. */
@Service
public class QuickBillingBCInvoicePersister {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    // The legacy BillingmasterDAO saves each entity in a separate REQUIRES_NEW transaction. Using
    // one persistence context here keeps a failed claim/history/note write safe to retry as an invoice.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInvoice(Billing header, List<Billingmaster> lines, String creator, String message) {
        entityManager.persist(header);
        for (Billingmaster line : lines) {
            line.setBillingNo(header.getId());
            entityManager.persist(line);

            BillingHistory history = new BillingHistory();
            history.setBillingMasterNo(line.getBillingmasterNo());
            history.setStatus(line.getBillingstatus());
            history.setCreationDate(new Date());
            history.setPractitionerNo(header.getProviderNo());
            history.setBillingType(header.getBillingtype());
            // These are new claims; they have no Teleplan sequence or payments yet.
            history.setSeqNum("");
            history.setAmount(line.getBillAmount());
            history.setAmountReceived("0.0");
            history.setPaymentTypeId(line.getPaymentMethod());
            entityManager.persist(history);

            BillingNotes note = new BillingNotes();
            note.setBillingmasterNo(line.getBillingmasterNo());
            note.setProviderNo(creator);
            note.setCreatedate(new Date());
            // JPA binds the note as data; SQL escaping would persist extra backslashes before quotes.
            note.setNote(Misc.removeNewLine(message));
            note.setNoteType(BillingNotes.DEFAULT_NOTE_TYPE);
            entityManager.persist(note);
        }
        entityManager.flush();
    }
}
