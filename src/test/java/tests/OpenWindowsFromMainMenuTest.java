package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.Set;

// No need to test the open search window scenario as it is already tested in the SearchTest.java file
// No need to test the open admin panel scenario as it is already tested in the OpenAdminPanelTest.java file
// No need to test the open schedule page scenario as it is already opened after login
// No need to test the open resources and help windows scenarios as they are not available
public class OpenWindowsFromMainMenuTest extends BaseTest {

    // Test the open caseload page successful scenario
    @Test
    public void openCaseloadPageSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'Caseload' tab on the menu bar
            WebElement caseloadTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Caseload")));
            caseloadTab.click();

            // Check for encountering 500 error
            boolean is500Error = false;
            try {
                WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
                if (errorElement.isDisplayed()) {
                    is500Error = true;
                    System.out.println("500 error encountered while opening caseload page.");
                    Assert.fail("500 error encountered while opening caseload page.");
                }
            } catch (NoSuchElementException e) {
                System.out.println("No 500 error encountered.");
            }

            if (!is500Error) {
                wait.until(ExpectedConditions.urlContains("provider/providercontrol.jsp"));
                Assert.assertTrue(driver.getCurrentUrl().contains("provider/providercontrol.jsp"));
                System.out.println("Caseload page opened successfully.");
            }

            // Display the caseload page for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open report window successful scenario
    @Test
    public void openReportWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'Report' tab on the menu bar
            WebElement reportTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Report")));
            reportTab.click();

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Wait for the new window to open and switch to it
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }

            // Check for encountering 500 error
            boolean is500Error = false;
            try {
                WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
                if (errorElement.isDisplayed()) {
                    is500Error = true;
                    System.out.println("500 error encountered while opening report window.");
                    Assert.fail("500 error encountered while opening report window.");
                }
            } catch (NoSuchElementException e) {
                System.out.println("No 500 error encountered.");
            }

            if (!is500Error) {
                wait.until(ExpectedConditions.urlContains("report/reportindex.jsp"));
                Assert.assertTrue(driver.getCurrentUrl().contains("report/reportindex.jsp"));
                System.out.println("Report window opened successfully.");
            }

            // Display the report window for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open billing window successful scenario
    @Test
    public void openBillingWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'Billing' tab on the menu bar
            WebElement billingTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Billing")));
            billingTab.click();

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Wait for the new window to open and switch to it
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }

            // Check for encountering 500 error
            boolean is500Error = false;
            try {
                WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
                if (errorElement.isDisplayed()) {
                    is500Error = true;
                    System.out.println("500 error encountered while opening billing window.");
                    Assert.fail("500 error encountered while opening billing window.");
                }
            } catch (NoSuchElementException e) {
                System.out.println("No 500 error encountered.");
            }

            if (!is500Error) {
                wait.until(ExpectedConditions.urlContains("billing/CA/ON/billingONReport.jsp"));
                Assert.assertTrue(driver.getCurrentUrl().contains("billing/CA/ON/billingONReport.jsp"));
                System.out.println("Billing window opened successfully.");
            }

            // Display the billing window for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open inbox window successful scenario
    @Test
    public void openInboxWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'Inbox' tab on the menu bar
            WebElement inboxTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Inbox")));
            inboxTab.click();

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Wait for the new window to open and switch to it
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }

            // Check for encountering 500 error
            boolean is500Error = false;
            try {
                WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
                if (errorElement.isDisplayed()) {
                    is500Error = true;
                    System.out.println("500 error encountered while opening inbox window.");
                    Assert.fail("500 error encountered while opening inbox window.");
                }
            } catch (NoSuchElementException e) {
                System.out.println("No 500 error encountered.");
            }

            if (!is500Error) {
                wait.until(ExpectedConditions.urlContains("documentManager/inboxManage.do"));
                Assert.assertTrue(driver.getCurrentUrl().contains("documentManager/inboxManage.do"));
                System.out.println("Inbox window opened successfully.");
            }

            // Display the inbox window for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open msg window successful scenario
    @Test
    public void openMsgWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'Msg' tab on the menu bar
            WebElement msgTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Msg")));
            msgTab.click();

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Wait for the new window to open and switch to it
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }

            // Check for encountering 500 error
            boolean is500Error = false;
            try {
                WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
                if (errorElement.isDisplayed()) {
                    is500Error = true;
                    System.out.println("500 error encountered while opening msg window.");
                    Assert.fail("500 error encountered while opening msg window.");
                }
            } catch (NoSuchElementException e) {
                System.out.println("No 500 error encountered.");
            }

            if (!is500Error) {
                wait.until(ExpectedConditions.urlContains("messenger/DisplayMessages.do"));
                Assert.assertTrue(driver.getCurrentUrl().contains("messenger/DisplayMessages.do"));
                System.out.println("Msg window opened successfully.");
            }

            // Display the msg window for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open consultations window successful scenario
    @Test
    public void openConsultationsWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'Consultations' tab on the menu bar
            WebElement consultationsTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Consultations")));
            consultationsTab.click();

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Wait for the new window to open and switch to it
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
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
                wait.until(ExpectedConditions.urlContains("oscarEncounter/IncomingConsultation.do"));
                Assert.assertTrue(driver.getCurrentUrl().contains("oscarEncounter/IncomingConsultation.do"));
                System.out.println("Consultations window opened successfully.");
            }

            // Display the consultations window for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open edoc window successful scenario
    @Test
    public void openEdocWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'eDoc' tab on the menu bar
            WebElement edocTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("eDoc")));
            edocTab.click();

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Wait for the new window to open and switch to it
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }

            // Check for encountering 500 error
            boolean is500Error = false;
            try {
                WebElement errorElement = driver.findElement(By.xpath("//*[contains(text(), 'Looks like something went wrong...')]"));
                if (errorElement.isDisplayed()) {
                    is500Error = true;
                    System.out.println("500 error encountered while opening edoc window.");
                    Assert.fail("500 error encountered while opening edoc window.");
                }
            } catch (NoSuchElementException e) {
                System.out.println("No 500 error encountered.");
            }

            if (!is500Error) {
                wait.until(ExpectedConditions.urlContains("documentManager/documentReport.jsp"));
                Assert.assertTrue(driver.getCurrentUrl().contains("documentManager/documentReport.jsp"));
                System.out.println("eDoc window opened successfully.");
            }

            // Display the edoc window for 2 sec
            Thread.sleep(2000);
        } finally {
            driver.quit();
        }
    }

    // Test the open tickler window successful scenario
    @Test
    public void openTicklerWindowSuccessful() throws InterruptedException {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            // Login
            login(driver, wait);
            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());

            // Click on the 'Tickler' tab on the menu bar
            WebElement ticklerTab = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Tickler")));
            ticklerTab.click();

            // Store the current window handle
            String mainWindowHandle = driver.getWindowHandle();

            // Wait for the new window to open and switch to it
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            Set<String> allWindowHandles = driver.getWindowHandles();
            for (String windowHandle : allWindowHandles) {
                if (!windowHandle.equals(mainWindowHandle)) {
                    driver.switchTo().window(windowHandle);
                    break;
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
        } finally {
            driver.quit();
        }
    }

}
