package veryspicyheatwave.bwb_datascraper;

import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.WebElement;

import java.util.List;

import static veryspicyheatwave.bwb_datascraper.ThriftBooks_DataScraper.eventLogEntry;

public final class WebRetryTool
{
    static List<WebElement> getListOfWebElements(WebElementListGetter listGetter, String elementDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to get the list of WebElements: " + elementDescription;
        int retryTimer = 0;
        int tryCount;
        List<WebElement> resp = null;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                resp = listGetter.getListOfWebElements();
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


    static WebElement getWebElement(WebElementGetter webGetter, String elementDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to get the WebElement: " + elementDescription;
        int retryTimer = 0;
        int tryCount;
        WebElement resp = null;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                resp = webGetter.getWebElement();
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



    static void performWebAction(WebTaskDoer taskDoer, String taskDescription) throws InterruptedException, RuntimeException
    {
        String failString = "Error: Exception while trying to " + taskDescription;
        int retryTimer = 0;
        int tryCount;
        for (tryCount = 1; tryCount <= 4; tryCount++)
        {
            try
            {
                taskDoer.doTask();
            }
            catch (org.openqa.selenium.TimeoutException | org.openqa.selenium.StaleElementReferenceException |
                   ElementClickInterceptedException | NullPointerException e)
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


