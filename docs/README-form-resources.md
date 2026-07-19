# CARLOS Form Resources Directory Documentation

This document describes the `/oscar/form/` directory which contains resources for CARLOS's comprehensive medical forms system, supporting various Canadian provincial health forms and clinical assessments. The system dynamically loads these resources at runtime based on form requirements.

## Directory Structure

### `/prop/` - Form Print Configuration and Templates
Contains PDF templates and print configuration files for form generation. Files are dynamically loaded by `FrmPDFServlet.java` when forms are printed.

#### Print Configuration Files (`*PrintCfg*.txt`, `*printCfg*.txt`)
Text files containing coordinate mappings for overlaying form data onto PDF templates. Each line typically contains field coordinates and formatting instructions.

**Naming Convention:** `[formName]PrintCfg[Page/Version].txt`
- Example: `bcar1PrintCfgPg1_2012.txt` = BC Antenatal Record, Page 1, 2012 version

#### British Columbia Forms

**BC Antenatal Records (BCAR)**
- `bcar1*.pdf`, `bcar2*.pdf` - Base PDF templates for BC Antenatal Record forms
- `bcar*PrintCfg*.txt` - Print configurations for different pages and versions (2007, 2012, current)
- `bcar*PrintGraphCfg*.txt` - Configuration for graph rendering on forms
- `bcarAll*.pdf`, `bcarARs*.pdf`, `bcarRisk*.pdf` - Combined and specialized versions

**BC Newborn Records**
- `bcnewborn*.pdf`, `bcNewBorn2008*.pdf` - Newborn record templates
- `bcnb*PrintCfg*.txt`, `bcNB2008PrintCfg*.txt` - Print configurations
- `bcbirthsummary*.pdf` - Birth summary forms

**BC Health Forms**
- `bchp*.pdf`, `bchpPrintCfg*.txt` - BC Health Passport forms
- `bclb*.pdf`, `bclbPrintCfg*.txt` - BC Lab forms

#### Ontario Forms

**Ontario Antenatal Records (ONAR)**
- `onar1*.pdf`, `onar2*.pdf` - Ontario Antenatal Record templates
- `onar*PrintCfg*.txt` - Standard print configurations
- `onar*enhanced*.pdf`, `onar*enhancedPrintCfg*.txt` - Enhanced versions with additional fields
  - Enhanced versions include: RF (Risk Factors), SV (Special Visits), US (Ultrasound)

#### Rourke Baby Records
Multiple versions supporting evolving pediatric standards:
- **2006 Version**: `rourke2006*.pdf`, `rourke2006printCfg*.txt`
- **2009 Version**: `rourke2009*.pdf`, `rourke2009printCfg*.txt`
- **Growth Charts**: `GrowthChartRourke[Year][Gender]*.pdf/txt`
  - Separate configurations for Boys/Girls
  - Graphic configurations for chart rendering
  - Multiple pages for different age ranges

#### Growth Charts
Standard CDC/WHO growth charts:
- `growthChart[Boy|Girl]*.pdf` - Stature/weight charts
- `growth[Boy|Girl]Head0_36*.pdf` - Head circumference (0-36 months)
- `growth[Boy|Girl]Length0_36*.pdf` - Length charts (0-36 months)
- `growthChart*BMI*.pdf` - BMI charts
- `*Graphic*.txt` - Configuration for plotting data points on charts

#### Mental Health Forms
- `mentalHealthForm[1|14|42].pdf` - Different mental health assessment forms
- `mentalHealthForm*Print*.txt` - Multi-page print configurations
- `bpmh_template*.pdf` - Best Possible Medication History templates

#### Lab Requisitions
- `labReqForm*.pdf` - Lab requisition forms (2007, 2010, current)
- `labReqPrint*.txt` - Print configurations
- `newReqLab.pdf` - Updated lab requisition template

#### Specialized Forms
- `PolicyForm*.pdf`, `PolicyFormPrint.txt` - Policy and consent forms
- `PositionHazardForm.pdf`, `PositionHazardPrint*.txt` - Workplace hazard assessments
- `OvulationForm*.pdf`, `ovulationPrintCfg*.txt` - Ovulation tracking
- `CounselingNote*.pdf`, `CounselingNotePrint.txt` - Counseling documentation
- `intakeHx.pdf`, `intakeHxPrintCfg*.txt` - Intake history (11 pages)
- `patientEncounterWorksheet*.pdf` - Clinical encounter documentation
- `invoice*.pdf`, `invoice.txt` - Billing/invoice templates
- `EPDS_TWEAK.pdf` - Edinburgh Postnatal Depression Scale/TWEAK screening

#### Prescription Forms
- `oscarRxPrintCfgPg1.txt` - Prescription printing configuration
- `oscarRxRePrintCfgPg1.txt` - Prescription reprint configuration

#### Utility Files
- `a6blank.pdf` - Blank A6 paper template
- `fundal_graph.png` - Fundal height graph image

### `/bcar2020/` - BC Antenatal Record 2020 Version
Modern form using Jasper Reports for PDF generation instead of text-based configurations.
- `BCAR2020_pg[1-6].jrxml` - Jasper Reports XML templates
- `BCAR2020_pg[1-5].pdf` - PDF preview/reference files
- `BCAR2020_pg[1-6].png` - Form preview images

### `/rourke2017/` - Rourke Baby Record 2017 Version
- `page[1-4].jrxml` - Jasper Reports templates for each page
- `page[1-4].png` - Form preview images
- `check.png`, `cross.png` - UI elements for form checkboxes

### `/rourke2020/` - Rourke Baby Record 2020 Version
Latest Rourke implementation with same structure as 2017:
- `page[1-4].jrxml` - Jasper Reports templates
- `page[1-4].png` - Form preview images
- `check.png`, `cross.png` - Checkbox graphics

### `/dataFiles/` - Mental Health Form Data
Contains structured data files loaded by mental health assessment forms. These text files provide dropdown options and standardized responses.

#### `/mhAssessment/` - Assessment Form Data
- `MedPhyIssues.txt` - Medical/physical issues checklist
- `PsychiatricSymptoms.txt` - Psychiatric symptom options
- `PsychosocialIssues.txt` - Psychosocial concern categories
- `TreatmentPlan.txt` - Treatment planning options

#### `/mhOutcome/` - Outcome Form Data
- `Disposition.txt` - Patient disposition options
- `PatientNotSeen.txt` - Reasons for missed appointments
- `ProblemsEncountered.txt` - Treatment barriers/issues
- `ServicesProvided.txt` - Service delivery options

#### `/mhReferral/` - Referral Form Data
- `AdviceRegardingManagement.txt` - Management recommendations
- `InterventionsRequested.txt` - Requested intervention types
- `MedPhyIssues.txt` - Medical issues for referral
- `PsychiatricSymptoms.txt` - Psychiatric symptoms for referral
- `PsychosocialIssues.txt` - Psychosocial factors for referral

#### `README.txt`
Original documentation file for the dataFiles directory.

## How It Works

### Dynamic Loading System
1. **Form Request**: User selects a form to print/view
2. **Parameter Passing**: Form name and version passed to servlet
3. **Resource Loading**: 
   - `FrmPDFServlet` loads resources from `/oscar/form/prop/`
   - Filename constructed as: form name + ".txt"
   - Files loaded from classpath at runtime
4. **PDF Generation**: Data overlaid on PDF template using coordinates from .txt files

### Version Management
Forms maintain multiple versions because:
- Medical forms evolve with new standards and requirements
- Historical patient data must be printable with original form versions
- Different provinces update forms at different times
- Regulatory compliance requires maintaining specific form versions

### Technology Evolution
- **Legacy Forms (pre-2017)**: Use `.txt` configuration files with coordinate mappings
- **Modern Forms (2017+)**: Use Jasper Reports (`.jrxml`) for more sophisticated layouts
- **Hybrid Support**: System supports both technologies simultaneously

## Important Notes

1. **DO NOT DELETE** old form versions - they're needed to print historical patient records
2. **Dynamic Loading**: Files are loaded by name at runtime, not compile time
3. **Province-Specific**: Many forms are specific to BC (British Columbia) or ON (Ontario)
4. **Medical Standards**: Forms follow specific medical standards (Rourke, WHO growth charts)
5. **Coordinate Precision**: Print configuration files contain precise pixel coordinates
6. **Template Matching**: Each `.txt` file must match its corresponding `.pdf` template

## Related Code
- `/src/main/java/oscar/form/pdfservlet/FrmPDFServlet.java` - Main PDF generation servlet
- `/src/main/java/oscar/form/` - Form business logic
- `/src/main/webapp/form/` - Form JSP pages
- Form model classes in `/src/main/java/org/oscarehr/common/model/`

## Adding New Forms
1. Create PDF template in `/prop/`
2. Create corresponding print configuration `.txt` file with field coordinates
3. Or for modern forms: Create Jasper Reports `.jrxml` template
4. Add form model class and DAO if needed
5. Create JSP pages in `/src/main/webapp/form/`
6. Register form in database configuration

## Maintenance
- Regularly verify PDF templates open correctly
- Test print configurations when upgrading PDF libraries
- Maintain backward compatibility for all form versions
- Document any coordinate system changes