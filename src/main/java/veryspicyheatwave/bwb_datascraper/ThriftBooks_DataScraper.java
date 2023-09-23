package veryspicyheatwave.bwb_datascraper;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

// Selenium Dependencies
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;


public class ThriftBooks_DataScraper
{    
    static String BASE_URL = "https://www.thriftbooks.com";    
    static String GECKO_DRIVER_PATH = "C:/Users/smash/Downloads/geckodriver-v0.33.0-win64/geckodriver.exe";
    
    static WebDriver getFFXDriver()
    {
        System.setProperty("webdriver.gecko.driver",GECKO_DRIVER_PATH);        
        FirefoxOptions ffxOptions = new FirefoxOptions();
        FirefoxProfile profile = new FirefoxProfile();        
        ffxOptions.setCapability(FirefoxDriver.PROFILE, profile);
        WebDriver driver = new FirefoxDriver();
        System.out.println("Created the new web driver");
        return driver;
    }
    
    static void BypassWebroot(WebDriver driver)
    {
        
        //Click the "Allow" button on my WebRoot blocker
        try
        {
            Thread.sleep(2000);
            WebElement allowBTN = driver.findElement(By.id("allowButton"));
            allowBTN.click();
            System.out.println("Bypassed the Webroot filter page");
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(ThriftBooks_DataScraper.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    static void ScrapePages(WebDriver driver, int numberOfPages) throws InterruptedException    
    {                
        //Click the DENY button for cookies to get the banner out of the way
            //I don't think I need this anymore but I'm keeping it just in case I do
        //driver.findElement(By.cssSelector("button[class=' osano-cm-deny osano-cm-buttons__button osano-cm-button osano-cm-button--type_deny '")).click();
        //Thread.sleep(500);     
        //System.out.println("Clicked the \"Deny\" button on the pop-up banner!");        
        //WebDriverWait wait = new WebDriverWait(driver, 10);
        
            
        
        for (int pageNo = 1; pageNo < numberOfPages + 1; pageNo++)
        {
            
            driver.navigate().to("about:blank");
            Thread.sleep(500);
            String pageURL = BASE_URL + "/browse/#b.s=mostPopular-desc&b.p=" + pageNo + "&b.pp=30&b.oos";
            driver.navigate().to(pageURL);
            Thread.sleep(2000);
            /*             
            PSEUDOCODE:
                For each page
                    Get a list of all of the book entries <a class="SearchResultGridItem undefined" ...>
                    For each book
                        Get the title <p class="SearchResultGridItem-title">
                        Get the author <p class="SearchResultGridItem-author">
                        Get the price range <p class="SearchResultGridItem-price">
                        Get the link <a class attribute href="<LINK>"....
                        Navigate to the link
                        Wait half a second (necessary??)
                        Get some other crap I haven't really determined yet
                            --Book Image Link?
                            --Book Genre?
                            --Book Review Rating?
                            --New book price
                            --Used book price
                        Navigate back
                        Wait half a second (necessary??)                
            */

            
            List<WebElement> books = driver.findElements(By.tagName("a"));
            System.out.println("Got the list of books");
            for (WebElement book : books)
            {
                boolean isABook = false;
                
                List<WebElement> details = book.findElements(By.tagName("p"));
                for (WebElement detail : details)
                {
                    if (detail.getText().contains("from:"))
                    {
                        break;
                    }
                    System.out.println(detail.getText());
                    isABook = true;
                }
                if (isABook)
                {
                    String bookURL = book.getAttribute("href");
                    System.out.println(bookURL);                    
                    driver.navigate().to(bookURL);
                    Thread.sleep(1000);
                    
                    //Get the goods...!
                }
                
            }
            
            
            // Click the "Next Page" button
            
            /*
            WebElement nextPageButton = driver.findElement(By.cssSelector("button[class='Pagination-link is-right is-link'"));
            try
            {                
                nextPageButton.click();
            }
            catch (Exception ex)
            {
                System.out.println("Failed the first click. Trying again...");
                if (nextPageButton != null)
                {
                    //wait.until(ExpectedConditions.visibilityOf(nextPageButton));
                    Thread.sleep(2000);
                    nextPageButton.sendKeys("don't accept these keys");
                    try
                    {
                        nextPageButton.click();
                    }
                    catch (StaleElementReferenceException SERE)
                    {
                        System.out.println("Couldn't click the next page button (or so they say!)");
                        //SERE.printStackTrace();
                        continue;
                    }  
                }          
                //Thread.sleep(5000);
            }
            */
        }
    }
    
    public static void main(String[] args) throws InterruptedException
    {   
        //Set up the FirefoxDriver object
        WebDriver driver = getFFXDriver();        
        BypassWebroot(driver);        
        
        
        //Wait a little bit before moving on
        try
        {
            Thread.sleep(1500);
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(ThriftBooks_DataScraper.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        ScrapePages(driver, 2);

    }
}
