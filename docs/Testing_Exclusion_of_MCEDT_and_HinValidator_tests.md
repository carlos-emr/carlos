**OSCAR TESTING**

**Skipping MCEDT and HINValidator using maven-surefire plugin**

**Overview:**

As part of resolving skipped test cases, certain test cases that are not needed in the regular build process have been moved to the exclusion list in the Maven Surefire Plugin configuration. This change ensures that while these tests are compiled and moved to the target directory, they are excluded from the standard mvn test run. This allows the tests to be readily available for execution when needed.

**Recent Changes**

The recent update includes the exclusion of test cases from two modules:

- HinValidator
- MCEDT

**Code Changes:**
The <excludes> tags in the pom.xml have been updated to include the HinValidatorTest and MCEDT tests. The updated configuration is as follows:

```xml
<plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      <configuration>
        <skipTests>false</skipTests>
          <excludes>
              <exclude>**/*EDTTest.java</exclude>
              <exclude>**/HinValidatorTest.java</exclude>
              <exclude>**/AR2005*.java</exclude>
              <exclude>**/OntarioMDSpec4DataTest.java</exclude>
              <exclude>**/ONAREnhancedBornConnectorTest.java</exclude>
              <exclude>org/oscarehr/e2e/**/*.java</exclude>
          </excludes>
        <systemPropertyVariables>
                <oscar.dbinit.skip>${oscar.dbinit.skip}</oscar.dbinit.skip>
                <buildDirectory>${project.build.directory}</buildDirectory>
        </systemPropertyVariables>
      </configuration>
</plugin>

```
\
**List of affected Test Classes:**

The following test classes have been excluded from the regular build process:

1. HinValidatorTest
1. DeleteEDTTest 
1. DownloadEDTTest 
1. EDTBaseTest 
1. GetTypeListEDTTest 
1. InfoEDTTest 
1. ListEDTTest 
1. SubmitEDTTest 
1. UpdateEDTTest 
1. UploadEDTTest

\
**Running Specific Test Classes:**

To run the excluded test cases individually, use the following commands:

- For **HinValidatorTest**
    - `mvn test -Dtest= HinValidatorTest`
- For **DeleteEDTTest**
    - `mvn test -Dtest= DeleteEDTTest `
- Repeat the above pattern for other classes
    - `mvn test -Dtest= ClassName`

\
**Running All MCEDT Tests at Once**

To run all MCEDT test cases at once, use the following command:

`mvn test -Dtest= \*EDTTest `


## Re-Including Tests in the Build Process

When you are ready to include these test cases in the regular build process, simply remove the corresponding patterns from the `<excludes>` tag in the pom.xml.

For Instance:

`<excludes>`\
~~`<exclude>`**/*EDTTest.java`</exclude>`~~ \
`</excludes>`

