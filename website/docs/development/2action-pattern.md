# The 2Action Pattern

OpenO EMR uses a unique migration pattern called "2Action" for transitioning from legacy Struts1 to modern Struts2 actions. This pattern allows for gradual modernization while maintaining backward compatibility.

## Pattern Overview

The "2Action" pattern involves creating new Struts2 action classes with the suffix "2Action" that coexist with their legacy counterparts. This approach allows for:

- Incremental migration of complex workflows
- A/B testing between old and new implementations
- Reduced risk during the modernization process

## Implementation Categories

### 1. Simple Execute Actions

These are basic actions that primarily handle form processing or simple navigation:

```java
public class MyFeature2Action extends ActionSupport {
    public String execute() {
        // Modern Struts2 implementation
        return SUCCESS;
    }
}
```

### 2. Method-Based Actions

Actions that support multiple operations through method annotation:

```java
public class DataManagement2Action extends ActionSupport {
    
    @Action("saveData")
    public String saveData() {
        // Save operation
        return SUCCESS;
    }
    
    @Action("loadData") 
    public String loadData() {
        // Load operation
        return SUCCESS;
    }
}
```

### 3. Inheritance-Based Actions

Complex actions that extend existing functionality while maintaining compatibility:

```java
public class PatientManagement2Action extends BasePatientAction {
    
    @Override
    public String execute() {
        // Enhanced patient management logic
        String result = super.execute();
        
        // Additional 2Action specific enhancements
        addModernValidation();
        
        return result;
    }
    
    private void addModernValidation() {
        // Modern validation logic
    }
}
```

## Migration Strategy

1. **Identify Legacy Action**: Locate the original Struts1 action
2. **Create 2Action Class**: Create new class with "2Action" suffix
3. **Implement Core Logic**: Port essential functionality to Struts2 patterns
4. **Add Configuration**: Update struts.xml with new action mappings
5. **Test Both Versions**: Ensure compatibility during transition
6. **Gradual Rollout**: Switch traffic incrementally to new implementation

## Configuration Example

```xml
<!-- Legacy Struts1 action -->
<action path="/patientManagement"
        type="org.oscarehr.legacy.PatientManagementAction">
    <forward name="success" path="/patient/view.jsp"/>
</action>

<!-- Modern 2Action implementation -->
<!-- Included new package namespace in the class attribute to use for migration -->
<action name="patientManagement2" 
        class="ca.openosp.openo.patient.PatientManagement2Action">
    <result name="success">/patient/modern-view.jsp</result>
</action>
```

## Best Practices

- Maintain consistent naming: `OriginalName2Action`
- Preserve original URL patterns when possible
- Document differences between legacy and 2Action implementations
- Use dependency injection where appropriate
- Implement proper error handling and validation

## Examples in Codebase

Some notable 2Action implementations in OpenO EMR:

- `CaseloadContent2Action`: Patient caseload management
- `EctDisplayMeasurements2Action`: Clinical measurements display
- `AppointmentType2Action`: Appointment type management
- `ProgramManager2Action`: Program administration

This pattern has allowed OpenO EMR to modernize gradually while maintaining stability and feature parity during the transition period.