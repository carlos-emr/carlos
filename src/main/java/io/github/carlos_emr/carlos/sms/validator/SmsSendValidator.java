package io.github.carlos_emr.carlos.sms.validator;

import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import io.github.carlos_emr.carlos.sms.support.SmsSegments;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SmsSendValidator {
    public Result validate(SmsSendCommand command) {
        List<String> messages = new ArrayList<>();
        if (command == null) {
            return new Result(List.of("SMS send request is required."));
        }

        // A system test is synthetic and has no patient; any demographic number given must still be valid.
        boolean patientRequired = command.messagePurpose() != SmsMessagePurpose.SYSTEM_TEST;
        Integer demographicNo = command.demographicNo();
        if ((patientRequired && demographicNo == null) || (demographicNo != null && demographicNo <= 0)) {
            messages.add("A valid patient demographic number is required.");
        }

        if (SmsPhoneNumbers.normalizeToE164(command.recipientPhoneNumber()).isEmpty()) {
            messages.add("A valid recipient phone number is required.");
        }

        if (command.recipientPhoneType() == null) {
            messages.add("SMS recipient phone type is required.");
        }

        String body = command.body();
        if (body == null || body.trim().isEmpty()) {
            messages.add("SMS message body is required.");
        } else {
            // One segment per send: VoIP.ms rejects SMS over 160 characters, and a longer body would be
            // split and billed as several messages by other providers.
            SmsSegments.Count count = SmsSegments.count(body);
            if (count.segments() > 1) {
                messages.add(tooLongMessage(count, body));
            }
        }

        return new Result(messages);
    }

    // Counts are segment units, not characters (an extension character or emoji takes two), so the
    // message says "spaces" and names the cause when the two differ. It never echoes the body itself.
    private static String tooLongMessage(SmsSegments.Count count, String body) {
        String message = "SMS message body is too long for one text message (uses " + count.units() + " of "
                + count.singleSegmentLimit() + " spaces";
        if (count.encoding() == SmsSegments.Encoding.UCS_2) {
            message += "; accented or special characters lower the limit from 160 to 70";
        } else if (count.units() > body.length()) {
            message += "; € { } [ ] ~ | ^ \\ each take two";
        }
        return message + ").";
    }

    public record Result(List<String> messages) {
        public Result {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public boolean valid() {
            return messages.isEmpty();
        }
    }
}
