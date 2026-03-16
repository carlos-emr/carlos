package tests;

import org.testng.annotations.Test;
import org.testng.Assert;
import org.openqa.selenium.WebDriver;

public class GoogleTest extends BaseTest {

    @Test
    public void testGooglePage() {
        WebDriver driver = createHeadlessDriver();

        try {
            driver.get("https://www.google.com");

            Assert.assertEquals(driver.getTitle(), "Google");
            System.out.println("The title is: " + driver.getTitle());
        } finally {
            driver.quit();
        }
    }
}
