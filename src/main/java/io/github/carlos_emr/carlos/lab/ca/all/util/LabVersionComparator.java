package io.github.carlos_emr.carlos.lab.ca.all.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.codec.digest.DigestUtils;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.ExcellerisOntarioHandler.OrderStatus;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.ExcellerisOntarioHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;

/**
 * Comparator for lab versions.  
 * Currently, this comparator can only be used for Excelleris Ontario Labs.
 */
public class LabVersionComparator {
    private static Hl7TextMessageDao hl7TextMessageDao = SpringUtils.getBean(Hl7TextMessageDao.class);
    private Map<String, MessageHandler> handlerMap;
    private List<Hl7TextMessage> hl7TextMessages;
    private List<String> labVersionIds;

    // Private constructor to enforce usage of parameterized constructor
    private LabVersionComparator() {}
    
    /**
     * Constructor to initialize the comparator with lab version IDs.
     *
     * @param labVersionIds List of lab version IDs to process.
     */
    public LabVersionComparator(List<String> labVersionIds) {
        this.labVersionIds = labVersionIds; // Lab version IDs are ordered sequentially from older to newer.
        this.hl7TextMessages = getLabs(labVersionIds); // Fetch HL7 messages for the given lab version IDs
        this.handlerMap = getHandlers(hl7TextMessages); // Create a map of handlers for the fetched HL7 messages
    }

    /**
     * Checks if the current lab data is a duplicate by comparing its MD5 hash with previous versions.
     *
     * @param currentSegmentID The ID of the current lab.
     * @return The index (as a String) of the first duplicate lab version, or null if no duplicate is found.
     */
    public String isLabDuplicate(String currentSegmentID) {
        // Get the HL7 body for the current lab and remove the first line
        String currentLabBody = ((ExcellerisOntarioHandler) handlerMap.get(currentSegmentID)).getHl7Body();
        String currentLabHash = DigestUtils.sha256Hex(removeFirstLine(currentLabBody));

        int currentLabIndex = labVersionIds.indexOf(currentSegmentID);

        // Compare the current lab's hash with all previous versions
        OptionalInt optionalIndex = IntStream.range(0, currentLabIndex)
                .filter(i -> {
                    // Get the HL7 body for the previous lab version and remove the first line
                    String labBody = ((ExcellerisOntarioHandler) handlerMap.get(labVersionIds.get(i))).getHl7Body();
                    String labHash = DigestUtils.sha256Hex(removeFirstLine(labBody));
                    return labHash.equals(currentLabHash);
                })
                .findFirst();
        
        // Convert index to 1-based position or Return null if no duplicate is found
        return optionalIndex.isPresent() ? String.valueOf(optionalIndex.getAsInt() + 1) : null;
    }

    /**
     * Finds missing tests in the current lab compared to all previous versions.
     *
     * @param currentSegmentID The ID of the current lab.
     * @return A map containing missing tests and their statuses.
     */
    public Map<String, OrderStatus> findMissingTests(String currentSegmentID, boolean addCanceledLabs) {
        // Get the test status map for the current lab
        Map<String, OrderStatus> currentLabTestStatusMap = getLabTestStatusMap((ExcellerisOntarioHandler) handlerMap.get(currentSegmentID));

        // Aggregate test status maps from all previous lab versions
        Map<String, OrderStatus> overallLabTestStatusMap = labVersionIds.subList(0, labVersionIds.indexOf(currentSegmentID)).stream()
            .map(id -> getLabTestStatusMap((ExcellerisOntarioHandler) handlerMap.get(id)))
            .reduce(new HashMap<>(), (overallMap, labMap) -> {
                labMap.forEach(overallMap::put); // Merge each lab's test status map into the overall map
                return overallMap;
            });

        return findMissingTests(currentLabTestStatusMap, overallLabTestStatusMap, addCanceledLabs);
    }

    /**
     * Fetches HL7 text messages for the given lab version IDs.
     *
     * @param labVersionIds List of lab version IDs.
     * @return A list of HL7 text messages corresponding to the IDs.
     */
    private List<Hl7TextMessage> getLabs(List<String> labVersionIds) {
        // Convert labVersionIds to integers and fetch HL7 messages
        return hl7TextMessageDao.findByIds(labVersionIds.stream()
                .map(Integer::parseInt)
                .collect(Collectors.toList()));
    }

    /**
     * Creates a map of message handlers for the given HL7 text messages.
     *
     * @param hl7TextMessages List of HL7 text messages.
     * @return A map of message handlers keyed by their IDs.
     */
    private Map<String, MessageHandler> getHandlers(List<Hl7TextMessage> hl7TextMessages) {
        return hl7TextMessages.stream()
            .collect(Collectors.toMap(
                hl7TextMessage -> String.valueOf(hl7TextMessage.getId()), 
                Factory::getHandler
            ));
    }


    /**
     * Creates a map of test names and their statuses for the given handler.
     *
     * @param excellerisOntarioHandler The handler for which to create the test status map.
     * @return A map of test names(OBR 4.2) and their statuses.
     */
    private Map<String, OrderStatus> getLabTestStatusMap(ExcellerisOntarioHandler excellerisOntarioHandler) {
        return IntStream.range(0, excellerisOntarioHandler.getOBRCount())
                .boxed()
                .collect(Collectors.toMap(
                    i -> excellerisOntarioHandler.getOBRName(i) + " (" + excellerisOntarioHandler.getOBRIdentifier(i) + ")", 
                    i -> excellerisOntarioHandler.getOrderStatusEnum(i)
                            .orElseThrow(() -> new IllegalArgumentException("Order Status not found!")),
                    (existingValue, newValue) -> newValue // Resolve duplicates by keeping the new value
                ));
    }

    /**
     * Finds missing tests and deleted tests by comparing two test status maps.
     *
     * @param currentLabTestStatusMap   The test status map for the current lab.
     * @param overallLabTestStatusMap   The aggregated test status map for all previous labs.
     * @return A map containing missing tests and deleted tests.
     */
    private Map<String, OrderStatus> findMissingTests(Map<String, OrderStatus> currentLabTestStatusMap, Map<String, OrderStatus> overallLabTestStatusMap, boolean addCanceledLabs) {
        //Map<String, OrderStatus> missingEntries = new HashMap<>();

        // Step 1: Find tests missing in currentLabTestStatusMap
        Map<String, OrderStatus> missingEntries = overallLabTestStatusMap.entrySet().stream()
            .filter(entry -> !currentLabTestStatusMap.containsKey(entry.getKey()))
            .collect(Collectors.toMap(
                Map.Entry::getKey, 
                Map.Entry::getValue,
                (existingValue, newValue) -> newValue // Resolve duplicates by keeping the new value
            ));

        if (addCanceledLabs) {
            // Step 2: Find tests in currentLabTestStatusMap with OrderStatus.DELETED
            currentLabTestStatusMap.entrySet().stream()
                .filter(entry -> entry.getValue() == OrderStatus.DELETED)
                .forEach(entry -> missingEntries.put(entry.getKey(), entry.getValue()));
        }

        return missingEntries;
    }

    /**
     * Removes the first line from a multi-line string.
     *
     * @param input The input string (e.g., HL7 body).
     * @return The input string with the first line removed.
     */
    private String removeFirstLine(String input) {
        if (input == null || input.isEmpty()) {
            return input; // Return the input as-is if it's null or empty
        }

        // Split the string into lines and skip the first line
        String[] lines = input.split("\n", 2); // Split into at most 2 parts
        return lines.length > 1 ? lines[1] : ""; // Return the second part (or an empty string if no second part exists)
    }
}