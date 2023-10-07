package veryspicyheatwave.bwb_datascraper;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;

import java.util.List;

import static veryspicyheatwave.bwb_datascraper.ThriftBooks_DataScraper.eventLogEntry;

public final class WebRetryTool
{
    static List<WebElement> getListOfWebElementsByTagName(WebElement element, String tagName, String elementDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to get the list of WebElements: " + elementDescription;
        int retryTimer = 0;
        int tryCount;
        List<WebElement> resp = null;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                resp = element.findElements(By.tagName(tagName));
            }
            catch (org.openqa.selenium.TimeoutException | org.openqa.selenium.StaleElementReferenceException | NullPointerException e)
            {
                eventLogEntry(failString);
                eventLogEntry(e.getMessage());
                if (tryCount == 4)
                {
                    eventLogEntry(failString);
                    throw new RuntimeException(failString, e);
                }
                else
                {
                    retryTimer += (tryCount * 1000);
                    Thread.sleep(retryTimer);
                    eventLogEntry("Attempting try number " + (1 + tryCount) + " after a " + (retryTimer / 1000) + " second wait");
                }
            }
        }
        return resp;
    }


    static List<WebElement> getListOfWebElementsByXPath(WebElement element, String xpathExpression, String elementDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to get the WebElements: " + elementDescription;
        int retryTimer = 0;
        int tryCount;
        List<WebElement> resp = null;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                resp = element.findElements(By.xpath(xpathExpression));
            }
            catch (org.openqa.selenium.TimeoutException | org.openqa.selenium.StaleElementReferenceException | NullPointerException e)
            {
                eventLogEntry(failString);
                eventLogEntry(e.getMessage());
                if (tryCount == 4)
                {
                    eventLogEntry(failString);
                    throw new RuntimeException(failString, e);
                }
                else
                {
                    retryTimer += (tryCount * 1000);
                    Thread.sleep(retryTimer);
                    eventLogEntry("Attempting try number " + (1 + tryCount) + " after a " + (retryTimer / 1000) + " second wait");
                }
            }
        }
        return resp;
    }


    static WebElement getWebElementByXPath(WebDriver driver, Wait<WebDriver> wait, String xpathExpression, String elementDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to get the WebElement: " + elementDescription;
        int retryTimer = 0;
        int tryCount;
        WebElement resp = null;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
                resp = driver.findElement(By.xpath(xpathExpression));
            }
            catch (org.openqa.selenium.TimeoutException | org.openqa.selenium.StaleElementReferenceException | NullPointerException e)
            {
                eventLogEntry(failString);
                eventLogEntry(e.getMessage());
                if (tryCount == 4)
                {
                    eventLogEntry(failString);
                    throw new RuntimeException(failString, e);
                }
                else
                {
                    retryTimer += (tryCount * 1000);
                    Thread.sleep(retryTimer);
                    eventLogEntry("Attempting try number " + (1 + tryCount) + " after a " + (retryTimer / 1000) + " second wait");
                }
            }
        }
        return resp;
    }


    static WebElement getWebElementByXPath(WebElement element, Wait<WebDriver> wait, String xpathExpression, String elementDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to get the WebElement: " + elementDescription;
        int retryTimer = 0;
        int tryCount;
        WebElement resp = null;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                wait.until(d -> element.findElement(By.xpath(xpathExpression)).isDisplayed());
                resp = element.findElement(By.xpath(xpathExpression));
            }
            catch (org.openqa.selenium.TimeoutException | org.openqa.selenium.StaleElementReferenceException | NullPointerException e)
            {
                eventLogEntry(failString);
                eventLogEntry(e.getMessage());
                if (tryCount == 4)
                {
                    eventLogEntry(failString);
                    throw new RuntimeException(failString, e);
                }
                else
                {
                    retryTimer += (tryCount * 1000);
                    Thread.sleep(retryTimer);
                    eventLogEntry("Attempting try number " + (1 + tryCount) + " after a " + (retryTimer / 1000) + " second wait");
                }
            }
        }
        return resp;
    }


    static void loadWebPage(WebDriver driver, String url, String urlDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to load the URL: " + urlDescription;
        int retryTimer = 0;
        int tryCount;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                driver.get(url);
            }
            catch (org.openqa.selenium.TimeoutException | org.openqa.selenium.StaleElementReferenceException | NullPointerException e)
            {
                eventLogEntry(failString);
                eventLogEntry(e.getMessage());
                if (tryCount == 4)
                {
                    eventLogEntry(failString);
                    throw new RuntimeException(failString, e);
                }
                else
                {
                    retryTimer += (tryCount * 1000);
                    Thread.sleep(retryTimer);
                    eventLogEntry("Attempting try number " + (1 + tryCount) + " after a " + (retryTimer / 1000) + " second wait");
                }
            }
        }
    }


    static void clickButton(WebElement button, String buttonDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to load the URL: " + buttonDescription;
        int retryTimer = 0;
        int tryCount;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                button.click();
            }
            catch (org.openqa.selenium.TimeoutException | org.openqa.selenium.StaleElementReferenceException | NullPointerException e)
            {
                eventLogEntry(failString);
                eventLogEntry(e.getMessage());
                if (tryCount == 4)
                {
                    eventLogEntry(failString);
                    throw new RuntimeException(failString, e);
                }
                else
                {
                    retryTimer += (tryCount * 1000);
                    Thread.sleep(retryTimer);
                    eventLogEntry("Attempting try number " + (1 + tryCount) + " after a " + (retryTimer / 1000) + " second wait");
                }
            }
        }
    }
}


