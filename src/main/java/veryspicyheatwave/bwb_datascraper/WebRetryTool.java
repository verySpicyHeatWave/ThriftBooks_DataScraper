package veryspicyheatwave.bwb_datascraper;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Wait;

import java.util.ArrayList;
import java.util.List;
import static veryspicyheatwave.bwb_datascraper.ThriftBooks_DataScraper.errorLogger;

public final class WebRetryTool
{
    static List<WebElement> performWebActionWithRetries(WebTaskDoer taskDoer, String taskDescription, int numRetries) throws InterruptedException, RuntimeException
    {
        String failString = "Exception while trying to " + taskDescription;
        int retryTimer = 0;
        int tryCount;
        List<WebElement> resp = null;
        for (tryCount = 1; tryCount <= numRetries; tryCount++)
        {
            try
            {
                resp = taskDoer.doTask();
            }
            catch (org.openqa.selenium.TimeoutException | org.openqa.selenium.StaleElementReferenceException | NullPointerException e)
            {
                errorLogger.warn(failString);
                errorLogger.warn(e.getMessage());
                if (tryCount == numRetries)
                {
                    errorLogger.warn(failString);
                    throw new RuntimeException(failString, e);
                }
                else
                {
                    retryTimer += (tryCount * 1000);
                    Thread.sleep(retryTimer);
                    errorLogger.warn("Attempting try number " + (1 + tryCount) + " after a " + (retryTimer / 1000) + " second wait");
                }
            }
        }
        return resp;
    }

    static @NotNull List<WebElement> listGetterXPath(WebDriver driver, @NotNull Wait<WebDriver> wait, String xpathExpression, boolean getSingleElementOnly)
    {
        List<WebElement> resp = new ArrayList<>();
        wait.until(d -> driver.findElement(By.xpath(xpathExpression)).isDisplayed());
        if(getSingleElementOnly)
            resp.add(driver.findElement(By.xpath(xpathExpression)));
        else
            resp = driver.findElements(By.xpath(xpathExpression));
        return resp;
    }


    static @NotNull List<WebElement> listGetterXPath(WebElement element, @NotNull Wait<WebDriver> wait, String xpathExpression, boolean getSingleElementOnly)
    {
        List<WebElement> resp = new ArrayList<>();
        wait.until(d -> element.findElement(By.xpath(xpathExpression)).isDisplayed());
        if (getSingleElementOnly)
            resp.add(element.findElement(By.xpath(xpathExpression)));
        else
            resp = element.findElements(By.xpath(xpathExpression));
        return resp;
    }


    static @NotNull List<WebElement> listGetterTagName(WebElement element, @NotNull Wait<WebDriver> wait, String tagName, boolean getSingleElementOnly)
    {
        List<WebElement> resp = new ArrayList<>();
        wait.until(d -> element.findElement(By.tagName(tagName)).isDisplayed());
        if (getSingleElementOnly)
            resp.add(element.findElement(By.tagName(tagName)));
        else
            resp = element.findElements(By.tagName(tagName));
        return resp;
    }
}


