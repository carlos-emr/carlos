package io.github.carlos_emr.carlos.sms.validator;

import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import io.github.carlos_emr.carlos.sms.support.SmsSegments;
import org.springframework.stereotype.Service;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SmsSendValidator {
    public Result validate(SmsSendCommand command) {
        List<String> messages = new ArrayList<>();
        if (command == null) {
            return new Result(List.of("SMS send request is required."));
        }

        // A system test is synthetic. It is approved on the system-test switch alone, without reading any
        // consent record, so it must not name a patient or an appointment: that would let it reach a real
        // patient who has not consented, and file the message on their record.
        boolean systemTest = command.messagePurpose() == SmsMessagePurpose.SYSTEM_TEST;
        Integer demographicNo = command.demographicNo();
        if (systemTest) {
            if (demographicNo != null || command.appointmentNo() != null) {
                messages.add("A system test message cannot name a patient or an appointment.");
            }
        } else if (demographicNo == null || demographicNo <= 0) {
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

    // Counts are segment units, not characters (an extension character takes two, most emoji two or more),
    // so the message says "spaces" and names the cause when the two differ. It never echoes the body itself.
    private static String tooLongMessage(SmsSegments.Count count, String body) {
        String message = "SMS message body is too long for one text message (uses " + count.units() + " of "
                + count.singleSegmentLimit() + " spaces";
        if (count.encoding() == SmsSegments.Encoding.UCS_2) {
            // é, è, à and ù fit the 160 limit, so name letters that do not rather than "accented" ones.
            message += "; some characters, such as ê, ô, ç, curly quotes or emoji, lower the limit from 160 to 70";
            if (hasMultiSpaceCharacter(body)) {
                message += ", and some characters, such as emoji, use two or more spaces each";
            }
        } else if (count.units() > body.length()) {
            message += "; some characters, such as € { } [ ] ~ | ^ \\, take two spaces";
        }
        return message + ").";
    }

    // True when one character as the reader sees it takes several UTF-16 units: 🙂, 👍🏽 and ❤️ take 2, 4 and 2.
    // A CR LF line break, as browsers send it, is also one such character but is not what the hint is about.
    private static boolean hasMultiSpaceCharacter(String body) {
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(body);
        for (int start = characters.first(), end = characters.next(); end != BreakIterator.DONE;
                start = end, end = characters.next()) {
            if (end - start > 1 && !body.startsWith("\r\n", start)) {
                return true;
            }
        }
        return false;
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
