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

package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.util.List;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Package-private utility for bulk message operations shared by
 * {@link MsgDisplayMessages2Action} and {@link MsgReDisplayMessages2Action}.
 *
 * <p>Validates each message ID via {@link ConversionUtils#fromLongString(String)},
 * skips invalid IDs (those that parse to 0 or negative), catches per-message failures, and
 * surfaces the failure count to the JSP layer via the {@code updateFailureCount}
 * request attribute.</p>
 *
 * @since 2026-02-20
 */
final class MsgBulkOperationHelper {

    private static final Logger logger = MiscUtils.getLogger();
    static final String ATTR_UPDATE_FAILURE_COUNT = "updateFailureCount";

    private MsgBulkOperationHelper() {
    }

    /**
     * Applies the given action to each valid message in the array.
     *
     * <p>For each message ID string:</p>
     * <ol>
     *   <li>Converts to {@code Long} via {@link ConversionUtils#fromLongString(String)}</li>
     *   <li>Skips the ID and increments failure count if the parsed value is {@code <= 0}</li>
     *   <li>Looks up all {@link MessageList} rows for the provider/message combination</li>
     *   <li>Applies the consumer action and merges the entity</li>
     *   <li>Catches {@link RuntimeException} per message, logging the ID</li>
     * </ol>
     *
     * <p>If any messages fail, sets {@code request.setAttribute("updateFailureCount", count)}.</p>
     *
     * @param request    HttpServletRequest the current request for setting failure attributes
     * @param providerNo String the provider number whose messages to update
     * @param messageIds String[] array of message ID strings to process
     * @param action     Consumer that applies the desired mutation to each {@link MessageList}
     */
    static void updateSelectedMessages(HttpServletRequest request, String providerNo,
                                       String[] messageIds, Consumer<MessageList> action) {
        if (messageIds == null || messageIds.length == 0) {
            return;
        }
        MessageListDao dao = SpringUtils.getBean(MessageListDao.class);
        int failureCount = 0;

        for (String messageId : messageIds) {
            Long parsedId = ConversionUtils.fromLongString(messageId);
            if (parsedId <= 0L) {
                failureCount++;
                logger.warn("Skipping invalid message ID: {}", messageId);
                continue;
            }
            try {
                List<MessageList> msgs = dao.findByProviderNoAndMessageNo(providerNo, parsedId);
                for (MessageList msg : msgs) {
                    action.accept(msg);
                    dao.merge(msg);
                }
            } catch (RuntimeException e) {
                failureCount++;
                logger.error("Failed to update message ID={}", messageId, e);
            }
        }

        if (failureCount > 0) {
            request.setAttribute(ATTR_UPDATE_FAILURE_COUNT, failureCount);
            logger.warn("{} message(s) failed to update", failureCount);
        }
    }
}
