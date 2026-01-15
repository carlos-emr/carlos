package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;
import java.util.List;

public class OpenEchartSubSectionsTest {
    
    // Test the open preventions window successful scenario
    @Test
    public void testOpenPreventionsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Preventions' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement preventionsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Preventions")));
        preventionsTab.click();

        // Wait for the preventions window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String preventionsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                preventionsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscarPrevention/index.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening preventions window.");
                Assert.fail("500 error encountered while opening preventions window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscarPrevention/index.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscarPrevention/index.jsp"));
            System.out.println("Preventions window opened successfully.");
        }

        // Display the preventions window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open tickler window successful scenario
    @Test
    public void testOpenTicklerWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Tickler' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement ticklerTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Tickler")));
        ticklerTab.click();

        // Wait for the tickler window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String ticklerWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                ticklerWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("tickler/ticklerMain.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening tickler window.");
                Assert.fail("500 error encountered while opening tickler window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("tickler/ticklerMain.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("tickler/ticklerMain.jsp"));
            System.out.println("Tickler window opened successfully.");
        }

        // Display the tickler window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open disease registry window successful scenario
    @Test
    public void testOpenDiseaseRegistryWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Disease Registry' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement diseaseRegistryTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Disease Registry")));
        diseaseRegistryTab.click();

        // Wait for the disease registry window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String diseaseRegistryWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                diseaseRegistryWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("dxresearch/setupDxResearch.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening disease registry window.");
                Assert.fail("500 error encountered while opening disease registry window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("dxresearch/setupDxResearch.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("dxresearch/setupDxResearch.do"));
            System.out.println("Disease registry window opened successfully.");
        }

        // Display the disease registry window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open forms window successful scenario
    @Test
    public void testOpenFormsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Forms' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement formsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Forms")));
        formsTab.click();

        // Wait for the forms window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String formsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                formsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscarEncounter/formlist.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening forms window.");
                Assert.fail("500 error encountered while opening forms window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscarEncounter/formlist.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscarEncounter/formlist.jsp"));
            System.out.println("Forms window opened successfully.");
        }

        // Display the forms window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open eforms window successful scenario
    @Test
    public void testOpenEformsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'eForms' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement eformsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("eForms")));
        eformsTab.click();

        // Wait for the eforms window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String eformsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                eformsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("eform/efmpatientformlist.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening eforms window.");
                Assert.fail("500 error encountered while opening eforms window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("eform/efmpatientformlist.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("eform/efmpatientformlist.jsp"));
            System.out.println("eForms window opened successfully.");
        }

        // Display the eforms window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open documents window successful scenario
    @Test
    public void testOpenDocumentsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Documents' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement documentsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Documents")));
        documentsTab.click();

        // Wait for the documents window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String documentsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                documentsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("documentManager/documentReport.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening documents window.");
                Assert.fail("500 error encountered while opening documents window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("documentManager/documentReport.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("documentManager/documentReport.jsp"));
            System.out.println("Documents window opened successfully.");
        }

        // Display the documents window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open lab result window successful scenario
    @Test
    public void testOpenLabResultWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Lab Result' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement labResultTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Lab Result")));
        labResultTab.click();

        // Wait for the lab result window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String labResultWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                labResultWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("lab/DemographicLab.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening lab result window.");
                Assert.fail("500 error encountered while opening lab result window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("lab/DemographicLab.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("lab/DemographicLab.jsp"));
            System.out.println("Lab result window opened successfully.");
        }

        // Display the lab result window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open messenger window successful scenario
    @Test
    public void testOpenMessengerWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Messenger' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement messengerTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Messenger")));
        messengerTab.click();

        // Wait for the messenger window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String messengerWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                messengerWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("messenger/DisplayDemographicMessages.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening messenger window.");
                Assert.fail("500 error encountered while opening messenger window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("messenger/DisplayDemographicMessages.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("messenger/DisplayDemographicMessages.do"));
            System.out.println("Messenger window opened successfully.");
        }

        // Display the messenger window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open measurements window successful scenario
    @Test
    public void testOpenMeasurementsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Measurements' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement measurementsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Measurements")));
        measurementsTab.click();

        // Wait for the measurements window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String measurementsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                measurementsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscarMeasurements/SetupHistoryIndex.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening measurements window.");
                Assert.fail("500 error encountered while opening measurements window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscarMeasurements/SetupHistoryIndex.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscarMeasurements/SetupHistoryIndex.do"));
            System.out.println("Measurements window opened successfully.");
        }

        // Display the measurements window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open consultations window successful scenario
    @Test
    public void testOpenConsultationsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Consultations' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement consultationsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Consultations")));
        consultationsTab.click();

        // Wait for the consultations window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String consultationsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                consultationsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening consultations window.");
                Assert.fail("500 error encountered while opening consultations window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp"));
            System.out.println("Consultations window opened successfully.");
        }

        // Display the consultations window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open hrm documents window successful scenario
    @Test
    public void testOpenHRMDocumentsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'HRM Documents' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement hrmDocumentsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("HRM Documents")));
        hrmDocumentsTab.click();

        // Wait for the hrm documents window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String hrmDocumentsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                hrmDocumentsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("hospitalReportManager/displayHRMDocList.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening hrm documents window.");
                Assert.fail("500 error encountered while opening hrm documents window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("hospitalReportManager/displayHRMDocList.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("hospitalReportManager/displayHRMDocList.jsp"));
            System.out.println("HRM documents window opened successfully.");
        }

        // Display the hrm documents window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open social history window successful scenario
    @Test
    public void testOpenSocialHistoryWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Social History' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement socialHistoryTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Social History")));
        socialHistoryTab.click();

        // Wait for the social history window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String socialHistoryWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                socialHistoryWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening social history window.");
                Assert.fail("500 error encountered while opening social history window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementEntry.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do"));
            System.out.println("Social history window opened successfully.");
        }

        // Display the social history window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open medical history window successful scenario
    @Test
    public void testOpenMedicalHistoryWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Medical History' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement medicalHistoryTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Medical History")));
        medicalHistoryTab.click();

        // Wait for the medical history window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String medicalHistoryWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                medicalHistoryWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening medical history window.");
                Assert.fail("500 error encountered while opening medical history window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementEntry.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do"));
            System.out.println("Medical history window opened successfully.");
        }

        // Display the medical history window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open ongoing concerns window successful scenario
    @Test
    public void testOpenOngoingConcernsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Ongoing Concerns' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement ongoingConcernsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Ongoing Concerns")));
        ongoingConcernsTab.click();

        // Wait for the ongoing concerns window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String ongoingConcernsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                ongoingConcernsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening ongoing concerns window.");
                Assert.fail("500 error encountered while opening ongoing concerns window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementEntry.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do"));
            System.out.println("Ongoing concerns window opened successfully.");
        }

        // Display the ongoing concerns window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open reminders window successful scenario
    @Test
    public void testOpenRemindersWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Reminders' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement remindersTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Reminders")));
        remindersTab.click();

        // Wait for the reminders window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String remindersWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                remindersWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening reminders window.");
                Assert.fail("500 error encountered while opening reminders window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementEntry.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do"));
            System.out.println("Reminders window opened successfully.");
        }

        // Display the reminders window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open allergies window successful scenario
    @Test
    public void testOpenAllergiesWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Allergies' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement allergiesTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Allergies")));
        allergiesTab.click();

        // Wait for the allergies window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String allergiesWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                allergiesWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscarRx/showAllergy.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening allergies window.");
                Assert.fail("500 error encountered while opening allergies window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscarRx/showAllergy.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscarRx/showAllergy.do"));
            System.out.println("Allergies window opened successfully.");
        }

        // Display the allergies window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open medications window successful scenario
    @Test
    public void testOpenMedicationsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Medications' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement medicationsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Medications")));
        medicationsTab.click();

        // Wait for the medications window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String medicationsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                medicationsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscarRx/choosePatient.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening medications window.");
                Assert.fail("500 error encountered while opening medications window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscarRx/choosePatient.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscarRx/choosePatient.do"));
            System.out.println("Medications window opened successfully.");
        }

        // Display the medications window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open other meds window successful scenario
    @Test
    public void testOpenOtherMedsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Other Meds' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement otherMedsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Other Meds")));
        otherMedsTab.click();

        // Wait for the other meds window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String otherMedsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                otherMedsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening other meds window.");
                Assert.fail("500 error encountered while opening other meds window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementEntry.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do"));
            System.out.println("Other meds window opened successfully.");
        }

        // Display the other meds window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open risk factors window successful scenario
    @Test
    public void testOpenRiskFactorsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Risk Factors' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement riskFactorsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Risk Factors")));
        riskFactorsTab.click();

        // Wait for the risk factors window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String riskFactorsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                riskFactorsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening risk factors window.");
                Assert.fail("500 error encountered while opening risk factors window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementEntry.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do"));
            System.out.println("Risk factors window opened successfully.");
        }

        // Display the risk factors window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open family history window successful scenario
    @Test
    public void testOpenFamilyHistoryWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Family History' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement familyHistoryTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Family History")));
        familyHistoryTab.click();

        // Wait for the family history window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String familyHistoryWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                familyHistoryWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening family history window.");
                Assert.fail("500 error encountered while opening family history window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementEntry.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementEntry.do"));
            System.out.println("Family history window opened successfully.");
        }

        // Display the family history window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open unresolved issues page successful scenario
    @Test
    public void testOpenUnresolvedIssuesPageSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Unresolved Issues' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement unresolvedIssuesTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Unresolved Issues")));
        unresolvedIssuesTab.click();

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening unresolved issues page.");
                Assert.fail("500 error encountered while opening unresolved issues page.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementView.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementView.do"));
            System.out.println("Unresolved issues page opened successfully.");
        }

        // Display the unresolved issues page for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open resolved issues page successful scenario
    @Test
    public void testOpenResolvedIssuesPageSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Resolved Issues' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement resolvedIssuesTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Resolved Issues")));
        resolvedIssuesTab.click();

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening resolved issues page.");
                Assert.fail("500 error encountered while opening resolved issues page.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/CaseManagementView.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/CaseManagementView.do"));
            System.out.println("Resolved issues page opened successfully.");
        }

        // Display the unresolved issues page for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open decision support alerts window successful scenario
    @Test
    public void testOpenDecisionSupportAlertsWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Decision Support Alerts' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement decisionSupportAlertsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Decision Support Alerts")));
        decisionSupportAlertsTab.click();

        // Wait for the decision support alerts window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String decisionSupportAlertsWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                decisionSupportAlertsWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("decisionSupport/guidelineAction.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening decision support alerts window.");
                Assert.fail("500 error encountered while opening decision support alerts window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("decisionSupport/guidelineAction.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("decisionSupport/guidelineAction.do"));
            System.out.println("Decision support alerts window opened successfully.");
        }

        // Display the decision support alerts window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open episodes window successful scenario
    @Test
    public void testOpenEpisodesWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Episodes' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement episodesTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Episodes")));
        episodesTab.click();

        // Wait for the episodes window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String episodesWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                episodesWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("oscar/Episode.do")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening episodes window.");
                Assert.fail("500 error encountered while opening episodes window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("oscar/Episode.do"));
            Assert.assertTrue(driver.getCurrentUrl().contains("oscar/Episode.do"));
            System.out.println("Episodes window opened successfully.");
        }

        // Display the episodes window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

    // Test the open health care team window successful scenario
    @Test
    public void testOpenHealthCareTeamWindowSuccessful() throws InterruptedException {

        // Open the main page
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver"); // Set the path to the chromedriver executable
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        driver.get("http://localhost:8080/");

        // Locate the username and password fields
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        WebElement passwordField = driver.findElement(By.name("password")); 

        // Enter the username and password
        usernameField.sendKeys("oscardoc");
        passwordField.sendKeys("mac2002#");

        // Locate and click the login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
        
        // Verify the login was successful
        String currentUrl = driver.getCurrentUrl();
        Assert.assertTrue(currentUrl.contains("provider/providercontrol.jsp"));
        System.out.println("Login successful, the current URL is: " + currentUrl);

        // Click on the 'Search' tab on the menu bar
        WebElement searchTab = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@onclick, \"popupPage2('../demographic/search.jsp');\")]")));
        searchTab.click();
        System.out.println("Search demographic page open successful, the current URL is: " + driver.getCurrentUrl());

        // Store the current window handle
        String mainWindowHandle = driver.getWindowHandle();

        // Switch to the search demographic window
        wait.until(ExpectedConditions.numberOfWindowsToBe(2));
        Set<String> allWindowHandles = driver.getWindowHandles();
        String demographicSearchWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle)) {
                demographicSearchWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                break;
            }
        }

        // Ensure the URL of the search demographic window is correct
        wait.until(ExpectedConditions.urlContains("demographic/search.jsp"));

        // Enter "%" in the search bar and press Enter to get all patients
        WebElement searchBar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("keyword")));
        searchBar.sendKeys("%");
        searchBar.sendKeys(Keys.RETURN);

        // Locate the patient results table
        WebElement patientResultsTable = driver.findElement(By.id("patientResults"));
        List<WebElement> rows = patientResultsTable.findElements(By.tagName("tr"));

        // Find the patient with indicated demographic number - 7 and open the echart
        for (WebElement row : rows) {
            List<WebElement> cells = row.findElements(By.tagName("td"));
            if (cells.size() > 0) {
                String demographicNo = cells.get(0).getText();
                if (demographicNo.equals("7")) { 
                    WebElement eChartButton = row.findElement(By.className("encounterBtn"));
                    eChartButton.click();
                    break;
                }
            }
        }

        // Switch to the eChart window
        wait.until(ExpectedConditions.numberOfWindowsToBe(3));
        allWindowHandles = driver.getWindowHandles();
        String eChartWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle)) {
                eChartWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("casemgmt/forward.jsp")) {
                    break;
                }
            }
        }

        // Handle the popup asking to keep editing
        try {
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
        } catch (TimeoutException e) {
            System.out.println("No alert present.");
        }

        wait.until(ExpectedConditions.urlContains("casemgmt/forward.jsp"));
        Assert.assertTrue(driver.getCurrentUrl().contains("casemgmt/forward.jsp"));
        System.out.println("E-Chart opened successfully of patient with id 7.");

        // Scroll to the top to reveal the 'Health Care Team' tab
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        WebElement healthCareTeamTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Health Care Team")));
        healthCareTeamTab.click();

        // Wait for the health care team window to open
        wait.until(ExpectedConditions.numberOfWindowsToBe(4));
        allWindowHandles = driver.getWindowHandles();
        String healthCareTeamWindowHandle = null;
        for (String windowHandle : allWindowHandles) {
            if (!windowHandle.equals(mainWindowHandle) && !windowHandle.equals(demographicSearchWindowHandle) && !windowHandle.equals(eChartWindowHandle)) {
                healthCareTeamWindowHandle = windowHandle;
                driver.switchTo().window(windowHandle);
                if (driver.getCurrentUrl().contains("demographic/displayHealthCareTeam.jsp")) {
                    break;
                }
            }
        }

        // Check for encountering 500 error
        boolean is500Error = false;
        try {
            WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
            if (errorElement.isDisplayed()) {
                is500Error = true;
                System.out.println("500 error encountered while opening health care team window.");
                Assert.fail("500 error encountered while opening health care team window.");
            }
        } catch (NoSuchElementException e) {
            System.out.println("No 500 error encountered.");
        }

        if (!is500Error) {
            wait.until(ExpectedConditions.urlContains("demographic/displayHealthCareTeam.jsp"));
            Assert.assertTrue(driver.getCurrentUrl().contains("demographic/displayHealthCareTeam.jsp"));
            System.out.println("Health care team window opened successfully.");
        }

        // Display the health care team window for 2 sec
        Thread.sleep(2000);

        // Close the browser
        driver.quit();

    }

}
