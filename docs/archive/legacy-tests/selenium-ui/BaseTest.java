package tests;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

/**
 * Base test class with common setup and helper methods for Selenium tests.
 */
public class BaseTest {

    // Test credentials - can be overridden via environment variables
    protected static final String USERNAME = getEnvOrDefault("TEST_USERNAME", "openodoc");
    protected static final String PASSWORD = getEnvOrDefault("TEST_PASSWORD", "OpenO2025");
    protected static final String PIN = getEnvOrDefault("TEST_PIN", "2025");

    // Application URL - can be overridden via environment variable
    protected static final String BASE_URL = getEnvOrDefault("TEST_BASE_URL", "http://localhost:8080/");

    // Default wait timeout in seconds
    protected static final int DEFAULT_TIMEOUT = 10;

    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Creates a headless Chrome WebDriver instance.
     * @return configured WebDriver
     */
    protected WebDriver createHeadlessDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
        return new ChromeDriver(options);
    }

    /**
     * Creates a WebDriverWait with default timeout.
     * @param driver the WebDriver instance
     * @return configured WebDriverWait
     */
    protected WebDriverWait createWait(WebDriver driver) {
        return new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT));
    }

    /**
     * Performs login with default credentials.
     * @param driver the WebDriver instance
     * @param wait the WebDriverWait instance
     */
    protected void login(WebDriver driver, WebDriverWait wait) {
        login(driver, wait, USERNAME, PASSWORD, PIN);
    }

    /**
     * Performs login with specified credentials.
     * @param driver the WebDriver instance
     * @param wait the WebDriverWait instance
     * @param username the username
     * @param password the password
     * @param pin the PIN
     */
    protected void login(WebDriver driver, WebDriverWait wait, String username, String password, String pin) {
        driver.get(BASE_URL);

        // Locate and fill username field
        WebElement usernameField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        usernameField.sendKeys(username);

        // Locate and fill password field
        WebElement passwordField = driver.findElement(By.name("password"));
        passwordField.sendKeys(password);

        // Locate and fill PIN field
        WebElement pinField = driver.findElement(By.name("pin"));
        pinField.sendKeys(pin);

        // Click login button
        WebElement loginButton = driver.findElement(By.cssSelector("input[type='submit'][value='Login']"));
        loginButton.click();
    }

    /**
     * Checks if login was successful by verifying URL contains provider control page.
     * @param driver the WebDriver instance
     * @return true if login successful
     */
    protected boolean isLoginSuccessful(WebDriver driver) {
        return driver.getCurrentUrl().contains("provider/providercontrol.jsp");
    }
}
