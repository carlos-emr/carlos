# Decision Support Guideline XSD Schema

## Overview
The `dsGuideline.xsd` file is an XML Schema Definition that validates the structure of decision support guidelines used by the CARLOS EMR system. These guidelines are used to provide clinical decision support based on various patient conditions.

## Location History
- **Originally**: `/src/main/resources/k2a/dsGuideline.xsd` - Was incorrectly placed in a K2A-specific directory
- **Current**: `/src/main/resources/dsGuideline.xsd` - Moved to resources root as part of K2A removal (August 2025)

## Purpose
This schema is used by the `DSServiceMyDrugref` service to validate XML guideline documents retrieved from the MyDrugref service. The validation ensures that guideline XML documents conform to the expected structure before they are processed and stored in the system.

## Schema Structure
The schema defines a guideline structure with the following components:

### Condition Types
- **age**: Age-based conditions for guidelines
- **sex**: Gender-specific conditions
- **dxcodes**: Diagnosis code conditions (ICD codes)
- **drugs**: Drug/medication-related conditions
- **notes**: Clinical note-based conditions

### Consequence Types
- **warning**: Clinical warnings to display
- **strength**: Strength recommendations
- **patientConsent**: Patient consent requirements

## Usage
The schema is referenced in:
- `org.oscarehr.decisionSupport.service.DSServiceMyDrugref.java` - Method `isValidGuidelineXml()`

## Example Guideline XML
```xml
<guideline>
    <conditions>
        <condition type="age">
            <value>65</value>
            <operator>greater_than</operator>
        </condition>
        <condition type="drugs">
            <value>warfarin</value>
        </condition>
    </conditions>
    <consequences>
        <consequence type="warning">
            <value>Monitor INR closely</value>
        </consequence>
    </consequences>
</guideline>
```

## Note
This schema is NOT specific to K2A integration. It's a general-purpose schema for decision support guidelines that happens to have been stored in the k2a directory historically. The schema is used for validating guidelines from MyDrugref and potentially other decision support sources.