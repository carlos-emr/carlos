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


package io.github.carlos_emr.carlos.service;

/**
 * Asynchronous mail sender that wraps a synchronous {@link MailSender} and delegates
 * send operations to a Spring {@link TaskExecutor} for non-blocking email delivery.
 *
 * <p>Each email send operation is wrapped in an {@code AsyncMailTask} runnable and
 * submitted to the task executor, allowing the calling thread to continue without
 * waiting for SMTP delivery to complete.</p>
 *
 * @since 2001-01-01
 * @deprecated Use the EmailManager service instead for sending emails.
 */

import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

/*
 * New emailing feature (EmailManager) is in production, utilizing JavaMailSender.
 * This method will be updated to use EmailManager for sending emails in the future.
 *
 * TODO: Update the deprecated code to use the EmailManager once the new emailing feature is fully implemented.
 */
@Deprecated
@Service(value = "asyncMailSender")
public class AsyncMailSender implements MailSender {

    @Resource(name = "mailSender")
    private MailSender mailSender;

    private TaskExecutor taskExecutor;


    @Autowired
    /**
     * Sets the Spring task executor used for asynchronous email delivery.
     *
     * @param taskExecutor TaskExecutor the task executor for background mail sending
     */
    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * Sends multiple mail messages asynchronously by delegating each to the task executor.
     *
     * @param mailMessages SimpleMailMessage[] the array of messages to send
     * @throws MailException if a mail sending error occurs
     */
    public void send(SimpleMailMessage[] mailMessages) throws MailException {
        for (SimpleMailMessage message : mailMessages) {
            send(message);
        }
    }

    /**
     * Sends a single mail message asynchronously by submitting it to the task executor.
     *
     * @param mailMessage SimpleMailMessage the message to send
     * @throws MailException if a mail sending error occurs
     */
    public void send(SimpleMailMessage mailMessage) throws MailException {
        taskExecutor.execute(new AsyncMailTask(mailMessage));
    }

    private class AsyncMailTask implements Runnable {

        private SimpleMailMessage message;

        private AsyncMailTask(SimpleMailMessage message) {
            this.message = message;
        }

        public void run() {
            mailSender.send(message);

        }
    }
}
