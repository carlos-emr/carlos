package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public class LoginTest extends BaseTest {

    // Test the login successful scenario
    @Test
    public void testLoginSuccessful() {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            login(driver, wait);

            Assert.assertTrue(isLoginSuccessful(driver));
            System.out.println("Login successful, the current URL is: " + driver.getCurrentUrl());
        } finally {
            driver.quit();
        }
    }

    // Test the login failed scenario - invalid username
    @Test
    public void testLoginFailedWithInvalidUsername() {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            login(driver, wait, "invaliduser", PASSWORD, PIN);

            Assert.assertFalse(isLoginSuccessful(driver));
            System.out.println("Login failed with invalid username.");
        } finally {
            driver.quit();
        }
    }

    // Test the login failed scenario - invalid password
    @Test
    public void testLoginFailedWithInvalidPassword() {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            login(driver, wait, USERNAME, "wrongpassword", PIN);

            Assert.assertFalse(isLoginSuccessful(driver));
            System.out.println("Login failed with invalid password.");
        } finally {
            driver.quit();
        }
    }

    // Test the login failed scenario - invalid username and password
    @Test
    public void testLoginFailedWithInvalidUsernameAndPassword() {
        WebDriver driver = createHeadlessDriver();
        WebDriverWait wait = createWait(driver);

        try {
            login(driver, wait, "invaliduser", "wrongpassword", "0000");

            Assert.assertFalse(isLoginSuccessful(driver));
            System.out.println("Login failed with invalid username and password.");
        } finally {
            driver.quit();
        }
    }
}
