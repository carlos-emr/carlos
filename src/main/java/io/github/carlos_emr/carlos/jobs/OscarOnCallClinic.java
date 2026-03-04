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
package io.github.carlos_emr.carlos.jobs;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.OnCallClinicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.jobs.OscarRunnable;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.awt.Color;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfWriter;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;

public class OscarOnCallClinic implements OscarRunnable {
    private Provider provider = null;
    private static String SCHEDULE_TEMPLATE = "P:OnCallClinic";
    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEEE MMMM d, yyyy");
    private static String DOCUMENTDIR = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

    @Override
    public void run() {
        MiscUtils.getLogger().info("Starting OSCAR ON CALL CLINIC Job");
        OnCallClinicDao onCallClinicDao = SpringUtils.getBean(OnCallClinicDao.class);
        Calendar yesterday = Calendar.getInstance();
        MiscUtils.getLogger().info("DATE " + yesterday.getTime());
        yesterday.add(Calendar.DATE, -1);
        yesterday.set(Calendar.HOUR_OF_DAY, 0);
        yesterday.set(Calendar.MINUTE, 0);
        yesterday.set(Calendar.SECOND, 0);
        yesterday.set(Calendar.MILLISECOND, 0);
        MiscUtils.getLogger().info("DATE " + yesterday.getTime());
        Date d = yesterday.getTime();

        if (onCallClinicDao.findByDate(d) != null) {
            MiscUtils.getLogger().info("YESTERDAY WAS ON CALL CLINIC");

            OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
            ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);
            ScheduleDateDao scheduleDateDao = SpringUtils.getBean(ScheduleDateDao.class);
            List<Object[]> results = appointmentDao.findAppointments(d, d);
            MiscUtils.getLogger().info("FOUND " + results.size() + " appointments");

            for (Object[] result : results) {
                Appointment appointment = (Appointment) result[0];
                if (appointment.getStatus().matches(".*C.*")) {
                    MiscUtils.getLogger().info("Skipping appointment as it is canceled");
                } else {
                    ScheduleDate scheduleDate = scheduleDateDao.findByProviderNoAndDate(appointment.getProviderNo(), appointment.getAppointmentDate());
                    if (scheduleDate != null && scheduleDate.getHour().equalsIgnoreCase(SCHEDULE_TEMPLATE)) {
                        Demographic demographic = (Demographic) result[1];
                        if (demographic.getProviderNo() != null && !demographic.getProviderNo().equals(appointment.getProviderNo())) {
                            ProviderData providerData = providerDataDao.find(appointment.getProviderNo());

                            String filename = "OSCAROnCallClinic" + new Date().getTime() + ".pdf";

                            if (appointment.getStatus().matches(".*N.*")) {
                                if (makeNoShowApptDocument(filename, appointment, demographic, providerData)) {
                                    SendDocument(filename, demographic);
                                }
                            } else {

                                if (makeGoodApptDocument(filename, appointment, demographic, providerData)) {
                                    SendDocument(filename, demographic);
                                }
                            }
                        }
                    } else {
                        MiscUtils.getLogger().info("Skipping appointment as it does not belong to on call clinic schedule");
                    }
                }
            }

        }
        MiscUtils.getLogger().info("Finished OSCAR ON CALL CLINIC Job");
    }


    private Boolean makeNoShowApptDocument(String filename, Appointment appointment, Demographic demographic, ProviderData providerData) {
        Document document = new Document();

        try (FileOutputStream fos = new FileOutputStream(DOCUMENTDIR + filename)) {
            PdfWriter.getInstance(document, fos);
            Rectangle pageSize = new Rectangle(PageSize.A5.getWidth(), PageSize.A5.getHeight());
            pageSize.setBackgroundColor(new Color(0xCC, 0xCC, 0xFF));
            document.setPageSize(pageSize);
            document.setMargins(36, 72, 108, 180);
            document.setMarginMirroringTopBottom(true);
            document.open();
            Font headerFont = new Font(Font.HELVETICA, 14);
            Chunk chunkHeader = new Chunk("OSCAR ON CALL CLINIC", headerFont);
            chunkHeader.setUnderline(2f, -2f);
            Paragraph header = new Paragraph(chunkHeader);
            header.setAlignment(Element.ALIGN_CENTER);
            header.setExtraParagraphSpace(24f);
            document.add(header);
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);

            Font bodyFont = new Font(Font.TIMES_ROMAN, 12);
            Chunk chunkAttn = new Chunk("ATTN: " + demographic.getProvider().getFormattedName(), bodyFont);
            Paragraph attnParagraph = new Paragraph(chunkAttn);
            attnParagraph.setAlignment(Element.ALIGN_LEFT);
            attnParagraph.setExtraParagraphSpace(24f);
            document.add(attnParagraph);
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);

            Font patientFont = new Font(Font.HELVETICA, 12, Font.ITALIC, Color.BLUE);
            Chunk patientChunk = new Chunk(demographic.getFormattedName(), patientFont);
            Paragraph body = new Paragraph();
            Chunk body1 = new Chunk("Your patient ", bodyFont);
            Chunk body2 = new Chunk(" did NOT show for an appointment on " + simpleDateFormat.format(appointment.getAppointmentDate()) +
                    ".  The appointment was with " + providerData.getFirstName() + " " + providerData.getLastName());

            body.add(body1);
            body.add(patientChunk);
            body.add(body2);
            body.setAlignment(Element.ALIGN_LEFT);
            document.add(body);

        } catch (FileNotFoundException e) {
            MiscUtils.getLogger().error("ERROR", e);
            return false;

        } catch (DocumentException | IOException e) {
            MiscUtils.getLogger().error("ERROR", e);
            return false;
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return true;

    }

    private Boolean makeGoodApptDocument(String filename, Appointment appointment, Demographic demographic, ProviderData providerData) {
        Document document = new Document();

        try (FileOutputStream fos = new FileOutputStream(DOCUMENTDIR + filename)) {
            PdfWriter.getInstance(document, fos);
            Rectangle pageSize = new Rectangle(PageSize.A5.getWidth(), PageSize.A5.getHeight());
            pageSize.setBackgroundColor(new Color(0xCC, 0xCC, 0xFF));
            document.setPageSize(pageSize);
            document.setMargins(36, 72, 108, 180);
            document.setMarginMirroringTopBottom(true);
            document.open();
            Font headerFont = new Font(Font.HELVETICA, 14);
            Chunk chunkHeader = new Chunk("OSCAR ON CALL CLINIC", headerFont);
            chunkHeader.setUnderline(2f, -2f);
            Paragraph header = new Paragraph(chunkHeader);
            header.setAlignment(Element.ALIGN_CENTER);
            header.setExtraParagraphSpace(24f);
            document.add(header);
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);
            String reason = appointment.getReason() == null || "".equals(appointment.getReason()) ? "" : " for \"" + appointment.getReason() + "\"";
            Font bodyFont = new Font(Font.TIMES_ROMAN, 12);
            Chunk chunkAttn = new Chunk("ATTN: " + demographic.getProvider().getFormattedName(), bodyFont);
            Paragraph attnParagraph = new Paragraph(chunkAttn);
            attnParagraph.setAlignment(Element.ALIGN_LEFT);
            attnParagraph.setExtraParagraphSpace(24f);
            document.add(attnParagraph);
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);

            Font patientFont = new Font(Font.HELVETICA, 12, Font.ITALIC, Color.BLUE);
            Chunk patientChunk = new Chunk(demographic.getFormattedName(), patientFont);
            Paragraph body = new Paragraph();
            Chunk body1 = new Chunk("Your patient ", bodyFont);
            Chunk body2 = new Chunk(" was seen on " + simpleDateFormat.format(appointment.getAppointmentDate()) + " by " +
                    providerData.getFirstName() + " " + providerData.getLastName() + reason, bodyFont);

            body.add(body1);
            body.add(patientChunk);
            body.add(body2);
            body.setAlignment(Element.ALIGN_LEFT);
            document.add(body);

        } catch (FileNotFoundException e) {
            MiscUtils.getLogger().error("ERROR", e);
            return false;

        } catch (DocumentException | IOException e) {
            MiscUtils.getLogger().error("ERROR", e);
            return false;
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return true;
    }

    private void SendDocument(String fileName, Demographic demographic) {
        String user = "System";
        String mrp = demographic.getProviderNo();
        EDoc newDoc = new EDoc("", "", fileName, "", user, user, "", 'A',
                UtilDateUtilities.getToday("yyyy-MM-dd"), "", "", "demographic", demographic.getDemographicNo().toString(), 0);
        newDoc.setDocPublic("0");

        // if the document was added in the context of a program
        ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);

        ProgramProvider pp = programManager.getCurrentProgramInDomain(null, provider.getProviderNo());
        if (pp != null && pp.getProgramId() != null) {
            newDoc.setProgramId(pp.getProgramId().intValue());
        }

        newDoc.setFileName(fileName);
        newDoc.setContentType("application/pdf");
        newDoc.setType("on-call clinic");
        newDoc.setDescription("On-Call Clinic");

        newDoc.setNumberOfPages(1);
        String doc_no = EDocUtil.addDocumentSQL(newDoc);

        ProviderInboxRoutingDao providerInboxRoutingDao = SpringUtils.getBean(ProviderInboxRoutingDao.class);
        providerInboxRoutingDao.addToProviderInbox(mrp, Integer.parseInt(doc_no), "DOC");


        PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);

        PatientLabRouting patientLabRouting = new PatientLabRouting();
        patientLabRouting.setDemographicNo(demographic.getDemographicNo());
        patientLabRouting.setLabNo(Integer.parseInt(doc_no));
        patientLabRouting.setLabType("DOC");
        patientLabRoutingDao.persist(patientLabRouting);


        MiscUtils.getLogger().info("Sent Document");
    }


    @Override
    public void setLoggedInProvider(Provider provider) {
        this.provider = provider;

    }

    @Override
    public void setLoggedInSecurity(Security security) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setConfig(String string) {
    }

}
