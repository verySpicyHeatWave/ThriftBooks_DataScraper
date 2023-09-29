//region Development Notes
/*
============================================================ TO-DO LIST ============================================================

1) Start looking into pumping the data into a SQL database.
    a) Reference link: https://stackoverflow.com/questions/2839321/connect-java-to-a-mysql-database
2) Figure out how to automatically download the image files and either save them somewhere to reference later or embed them into the
        SQL database (is that even possible?)

On occasion, run the scraper overnight and see how accurate my time estimate is. 2 minutes per page means 30 pages per hour. 120 pages should
        take about 4 hours and I should wind up with 3600 books in the CSV.

====================================================================================================================================
*/
//endregion

//region Import Statements
package veryspicyheatwave.bwb_datascraper;

import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.jetbrains.annotations.NotNull;

import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
//endregion

public class ThriftBooks_DataScraper
{
    //region Class-Wide Variables
    final static String BASE_URL = "https://www.thriftbooks.com";
    final static String GECKO_DRIVER = "geckodriver.exe";
    final static String SAVE_FILE = "dataScrape_" + System.currentTimeMillis() +".csv";
    final static String LOG_FILE = "dataScrape_" + System.currentTimeMillis() +".log";
    static boolean loggingEvents = false;
    static Genre filterGenre;
    //endregion


    //region Void Methods
    public static void main(String[] args)
    {
        printBanner();

        int firstPage;
        int lastPage;
        Scanner keyboard = new Scanner(System.in);

        do
        {
            firstPage = getFirstOrLastPage(keyboard, "first");
            if (firstPage == 0)
            {
                System.out.println("Exiting program...");
                return;
            }
            lastPage = getFirstOrLastPage(keyboard, "last");
            if (lastPage < firstPage)
                System.out.println("**ERROR: Last page can't be smaller than first page!\t");
        }
        while (lastPage < firstPage);

        filterGenre = getBookGenre(keyboard);
        System.out.println(filterGenre);

        loggingEvents = askIfUserWantsToLog(keyboard);
        if (loggingEvents)
            eventLogEntry("Log file generated");

        writeLineToCSV("Title, Author, Used Price, New Price, Genre, Format, ISBN Code, Release Date," +
                        "Page Length, Language, ThriftBooks URL, Image Link 1, Image Link 2");
        eventLogEntry("CSV file generated");

        long startTime = System.nanoTime();
        printMemoryUsageToEventLog();

        scrapeBookPages(getListOfBookURLs(firstPage, lastPage));

        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        eventLogEntry("Task complete in " + printDurationFromNanoseconds((int)duration));
    }


    static void scrapeBookPages(@NotNull ArrayList<String> listOfBookURLs)
    {
        WebDriver driver = getFFXDriver();
        eventLogEntry("Created the webdriver object to scrape the book URLs for book data");
        Wait<WebDriver> wait = new WebDriverWait(driver, 4);
        try
        {
            for (String bookURL : listOfBookURLs)
            {
                long currentBookStartTime = System.nanoTime();
                try
                {
                    BookEntry book = new BookEntry();
                    book.link = bookURL;
                    driver.get(book.link);
                    Thread.sleep(400);

                    wait.until(d -> driver.findElement(By.xpath("//div[@class='WorkMeta-details is-collapsed']")).isDisplayed());
                    parseTitleAuthor(driver.getTitle(), book);

                    WebElement titleBlock = driver.findElement(By.xpath("//h1[@class='WorkMeta-title Alternative Alternative-title']"));
                    if (titleBlock.getText() != null)
                        book.title = titleBlock.getText();

                    WebElement table = driver.findElement(By.xpath("//div[@class='WorkMeta-details is-collapsed']"));
                    parseTableDetails(table, book);

                    wait.until(d -> driver.findElement(By.xpath("//div[@class='Content']")).isDisplayed());
                    WebElement pageContents = driver.findElement(By.xpath("//div[@class='Content']"));
                    book.genre = parseGenreString(pageContents);

                    WebElement buttonContainer = driver.findElement(By.xpath("//div[@class='WorkSelector-rowContainer']"));
                    parseButtonContainer(driver, buttonContainer, wait, book);

                    SimpleDateFormat formatter = new SimpleDateFormat("MM/yyyy");

                    String dataEntry = String.format("\"%s\",\"%s\",%.2f,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\"",
                            book.title, book.author, book.usedPrice, book.newPrice, book.genre, book.format, book.isbnCode, formatter.format(book.releaseDate),
                            book.pageLength, book.language, book.link, book.paperbackImageLink, book.massImageLink);

                    writeLineToCSV(dataEntry);

                    eventLogEntry(String.format("Successfully added book number %,d titled \"%s\" to the file", (1 + listOfBookURLs.indexOf(bookURL)), book.title));
                    printMemoryUsageToEventLog();
                }
                catch (org.openqa.selenium.TimeoutException e)
                {
                    eventLogEntry("Error in method scrapeBookPages(): Selenium timed out when trying to access something on the page for book number " + (1 + listOfBookURLs.indexOf(bookURL)) + ": " + bookURL);
                    eventLogEntry(e.getMessage());
                }
                catch (org.openqa.selenium.StaleElementReferenceException e)
                {
                    eventLogEntry("Error in method scrapeBookPages(): Selenium couldn't locate a particular page element for book number " + (1 + listOfBookURLs.indexOf(bookURL)) + ": " + bookURL);
                    eventLogEntry(e.getMessage());
                }
                catch (InterruptedException e)
                {
                    eventLogEntry("Error in method scrapeBookPages(): Sleep function failed while scraping for book number " + (1 + listOfBookURLs.indexOf(bookURL)) + ": " + bookURL);
                    eventLogEntry(e.getMessage());
                }
                long currentBookEndTime = System.nanoTime();

                long duration = (currentBookEndTime - currentBookStartTime) / 1000000;
                eventLogEntry(printDurationFromNanoseconds((int) duration) + " spent on scraping book " + (1 + listOfBookURLs.indexOf(bookURL)));
            }
        }
        catch (Exception ex)
        {
            eventLogEntry("Error in method scrapeBookPages(): Unhandled error while scraping web pages. Killing the operation...");
            eventLogEntry(ex.getMessage());
        }
        finally
        {
            driver.quit();
            eventLogEntry("WebDriver instance successfully closed");
        }
    }


    static void parseTitleAuthor(String titleAuthor, @NotNull BookEntry book)
    {
        titleAuthor = titleAuthor.replace(" book by ", "|");
        String[] titleAuthorSplit = titleAuthor.split("\\|");
        book.title = titleAuthorSplit[0];
        if (titleAuthorSplit.length > 1)
            book.author = titleAuthorSplit[1];
        else
            book.author = "Who??";
    }


    static void parseButtonContainer(@NotNull WebDriver driver, @NotNull WebElement buttonContainer, Wait<WebDriver> wait, BookEntry book) throws InterruptedException, StaleElementReferenceException, TimeoutException
    {
        List<WebElement> buttons = buttonContainer.findElements(By.tagName("button"));
        for (WebElement button : buttons)
        {
            wait.until(ExpectedConditions.visibilityOf(button));
            List<WebElement> buttDetails = button.findElements(By.xpath("./child::div"));
            for (WebElement buttDetail : buttDetails)
            {
                if (buttDetail.getText().equalsIgnoreCase("paperback"))
                {
                    WebElement priceRangeElement = buttDetail.findElement(By.xpath("./following-sibling::div[@class='']//span"));
                    if (priceRangeElement.getText().contains("$"))
                    {
                        getNewAndUsedPrices(book.paperbackPrices, priceRangeElement.getText());
                    }
                    button.click();
                    Thread.sleep(150);
                    WebElement image = driver.findElement(By.xpath("//img[@itemprop='image']"));
                    book.paperbackImageLink = image.getAttribute("src");
                    continue;
                }

                if (buttDetail.getText().equalsIgnoreCase("mass market paperback"))
                {
                    button.click();
                    Thread.sleep(150);
                    WebElement image = driver.findElement(By.xpath("//img[@itemprop='image']"));
                    book.massImageLink = image.getAttribute("src");
                    if (book.usedPrice <= 0 || book.newPrice <= 0)
                    {
                        WebElement priceRangeElement = buttDetail.findElement(By.xpath("./following-sibling::div[@class='']//span"));
                        getNewAndUsedPrices(book.massMarketPrices, priceRangeElement.getText());
                    }
                    continue;
                }

                if (buttDetail.getText().equalsIgnoreCase("hardcover"))
                {
                    WebElement priceRangeElement = buttDetail.findElement(By.xpath("./following-sibling::div[@class='']//span"));
                    if (priceRangeElement.getText().contains("$"))
                    {
                        getNewAndUsedPrices(book.hardcoverPrices, priceRangeElement.getText());
                    }
                }
            }
        }
        determineBestPriceSet(book);
    }


    static void parseTableDetails(@NotNull WebElement table, BookEntry book) throws StaleElementReferenceException, TimeoutException
    {
        List<WebElement> elements = table.findElements(By.tagName("span"));
        int index = -1;

        for (WebElement detail : elements)
        {
            index++;
            if (detail.getText().toLowerCase().contains("isbn"))
            {
                book.isbnCode = elements.get(index + 1).getText();
                continue;
            }

            if (detail.getText().toLowerCase().contains("release"))
            {
                book.releaseDate = parseDateFromStr(elements.get(index + 1).getText());
                continue;
            }

            if (detail.getText().toLowerCase().contains("length"))
            {
                String tempPageLength = elements.get(index + 1).getText();
                tempPageLength = tempPageLength.replace(" Pages", "");
                book.pageLength = Integer.parseInt(tempPageLength);
                continue;
            }

            if (detail.getText().toLowerCase().contains("language"))
            {
                book.language = elements.get(index + 1).getText();
                continue;
            }

            if (detail.getAttribute("itemprop") != null && book.genre == null && detail.getAttribute("itemprop").toLowerCase().contains("name"))
            {
                book.genre = detail.getText();
            }
        }
    }


    static void getNewAndUsedPrices(@NotNull PriceStructure respBook, @NotNull String priceString)
    {
        if (priceString.contains(" - "))
        {
            String[] parsedPriceString = priceString.split("-");
            for (int i = 0; i < parsedPriceString.length; i++)
            {
                parsedPriceString[i] = parsedPriceString[i].trim();
                parsedPriceString[i] = parsedPriceString[i].replace("$", "");
            }
            respBook.usedPrice = Double.parseDouble(parsedPriceString[0]);
            respBook.newPrice = Double.parseDouble(parsedPriceString[1]);
        }
        else if (priceString.contains("$"))
        {
            priceString = priceString.trim();
            priceString = priceString.replace("$", "");
            respBook.newPrice = Double.parseDouble(priceString);
            respBook.usedPrice = 0;
        }
        else
        {
            respBook.usedPrice = 0;
            respBook.newPrice = 0;
        }
    }


    static void determineBestPriceSet(@NotNull BookEntry book)
    {
        ArrayList<PriceStructure> priceLists = new ArrayList<>();
        boolean[] removeIndices = {false, false, false};
        priceLists.add(book.paperbackPrices);
        priceLists.add(book.massMarketPrices);
        priceLists.add(book.hardcoverPrices);

        for (int i = 0; i <= priceLists.size() - 1; i++)
        {
            if (priceLists.get(i).newPrice <= 0 || priceLists.get(i).newPrice - priceLists.get(i).usedPrice < 2)
            {
                removeIndices[i] = true;
            }
        }

        for (int i = priceLists.size() - 1; i >= 0; i--)
        {
            if (removeIndices[i])
            {
                priceLists.remove(i);
            }
        }

        if (priceLists.size() == 1)
        {
            book.newPrice = priceLists.get(0).newPrice;
            book.usedPrice = priceLists.get(0).usedPrice;
            book.format = priceLists.get(0).format;
            return;
        }

        PriceStructure returnBook = new PriceStructure("null");
        returnBook.newPrice = 1000000000;
        returnBook.usedPrice = 1000000000;

        for (int i = 0; i <= priceLists.size() - 1; i++)
        {
            if (priceLists.get(i).newPrice < returnBook.newPrice)
            {
                returnBook = priceLists.get(i);
            }
        }

        book.newPrice = returnBook.newPrice;
        book.usedPrice = returnBook.usedPrice;
        book.format = returnBook.format;
    }


    static @NotNull String printDurationFromNanoseconds(int duration)
    {
        int millis = 0, seconds = 0, minutes = 0, hours = 0, days = 0;
        String timeString = "";

        if (duration > 1000)
        {
            millis = (duration % 1000);
            seconds = (duration / 1000);
        }

        if (seconds > 60)
        {
            minutes = seconds / 60;
            seconds = seconds % 60;
        }
        if (minutes > 60)
        {
            hours = minutes / 60;
            minutes = minutes % 60;
        }
        if (duration > 24)
        {
            days = hours / 24;
            hours = hours / 24;
        }

        if (days > 0)
        {
            timeString += String.format("%d days, ", days);
        }
        if (hours > 0)
        {
            timeString += String.format("%d hours, ", hours);
        }
        if (minutes > 0)
        {
            timeString += String.format("%d minutes ", minutes);
        }

        timeString += String.format("%d.%d seconds", seconds, millis);

        return timeString;
    }


    static void writeLineToCSV(String dataEntry)
    {
        try (FileWriter writer = new FileWriter(SAVE_FILE, true))
        {
            writer.write(dataEntry + "\n");
        }
        catch (IOException ex)
        {
            System.out.println("ERROR: Failed to write to CSV file. Oops!");
        }
    }


    static void eventLogEntry(String logEntry)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        logEntry = formatter.format(LocalDateTime.now()) + ":\t" + logEntry;
        System.out.println(logEntry);
        if (loggingEvents)
        {
            try (FileWriter writer = new FileWriter(LOG_FILE, true))
            {
                writer.write(logEntry + "\n");
            }
            catch (IOException ex)
            {
                System.out.println("ERROR: Failed to write to log file. Oops!");
            }
        }
    }


    static void printMemoryUsageToEventLog()
    {
        eventLogEntry(String.format("Memory usage: %d kb / %d kb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024, Runtime.getRuntime().totalMemory() / 1024));
    }


    static void printBanner()
    {
        // Hey, I didn't know you could do a text block like that, that's pretty nice! Thanks, Java!
        System.out.print("""
                $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$'               `$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ \s
                $$$$$$$$$$$$$$$$$$$$$$$$$$$$'                   `$$$$$$$$$$$$$$$$$$$$$$$$$$$$
                $$$'`$$$$$$$$$$$$$'`$$$$$$!                       !$$$$$$'`$$$$$$$$$$$$$'`$$$
                $$$$  $$$$$$$$$$$  $$$$$$$                         $$$$$$$  $$$$$$$$$$$  $$$$
                $$$$. `$' \\' \\$`  $$$$$$$!                         !$$$$$$$  '$/ `/ `$' .$$$$
                $$$$$. !\\  i  i .$$$$$$$$                           $$$$$$$$. i  i  /! .$$$$$
                $$$$$$   `--`--.$$$$$$$$$                           $$$$$$$$$.--'--'   $$$$$$
                $$$$$$L        `$$$$$^^$$                           $$^^$$$$$'        J$$$$$$
                $$$$$$$.   .'   ""~   $$$    $.                 .$  $$$   ~""   `.   .$$$$$$$
                $$$$$$$$.  ;      .e$$$$$!    $$.             .$$  !$$$$$e,      ;  .$$$$$$$$
                $$$$$$$$$   `.$$$$$$$$$$$$     $$$.         .$$$   $$$$$$$$$$$$.'   $$$$$$$$$
                $$$$$$$$    .$$$$$$$$$$$$$!     $$`$$$$$$$$'$$    !$$$$$$$$$$$$$.    $$$$$$$$
                $JT&yd$     $$$$$$$$$$$$$$$$.    $    $$    $   .$$$$$$$$$$$$$$$$     $by&TL$
                                                 $    $$    $
                                                 $.   $$   .$
                                                 `$        $'
                                                  `$$$$$$$$'

                """);
        System.out.println("B. Cobb's Selenium Web scraper for Thrift Books");
        System.out.println("The super fun but slightly useless learning project!\n\n");
    }
    //endregion


    //region Return Methods
    static Genre getBookGenre(Scanner keyboard)
    {
        System.out.println("Enter the number of which genre would you like to get: ");

        for (Genre g : Genre.values())
        {
            System.out.printf("[%d]: %s\n", g.ordinal(), g.getDisplayString());
        }
        int selection = keyboard.nextInt();

        while (selection < 0 || selection > Genre.values().length)
        {
            System.out.println("Invalid selection. Try again. ");
            selection = keyboard.nextInt();
        }
        keyboard.nextLine();
        return Genre.values()[selection];
    }


    static String parseGenreString(@NotNull WebElement pageContents)
    {
        List<WebElement> spans = pageContents.findElements(By.tagName("span"));
        for (WebElement span : spans)
        {
            if (span.getAttribute("itemprop") != null && span.getAttribute("itemprop").toLowerCase().contains("name"))
            {
                return span.getText();
            }
        }
        return filterGenre.getDisplayString();
    }


    static @NotNull ArrayList<String> getListOfBookURLs(int firstPage, int lastPage)
    {
        WebDriver driver = getFFXDriver();
        ArrayList<String> listOfBookURLs = new ArrayList<>();
        eventLogEntry("Created the webdriver object to scrape for the book URLs");
        Wait<WebDriver> wait = new WebDriverWait(driver, 4);

        for (int pageNo = firstPage; pageNo < lastPage + 1; pageNo++)
        {
            try
            {
                long currentPageStartTime = System.nanoTime();
                driver.navigate().to("about:blank");
                Thread.sleep(666);
                String pageURL = BASE_URL + "/browse/?b.search=#b.s=mostPopular-desc&b.p=" + pageNo + "&b.pp=30&b.pt=1&b.f.t%5B%5D=" + filterGenre.getFilterNo();
                driver.get(pageURL);
                wait.until(d -> driver.findElement(By.xpath("/html/body/div[4]/div/div[2]/div/div[2]/div/div/div/div/div[2]/div[2]/div[1]/div/div[1]")).isDisplayed());

                WebElement searchContainer = driver.findElement(By.xpath("/html/body/div[4]/div/div[2]/div/div[2]/div/div/div/div/div[2]/div[2]/div[1]/div/div[1]"));
                List<WebElement> books = searchContainer.findElements(By.tagName("a"));
                for (WebElement book : books)
                {
                    String bookURL = book.getAttribute("href");
                    listOfBookURLs.add(bookURL);
                }
                eventLogEntry("Got a list of book links from page " + pageNo);
                long currentPageEndTime = System.nanoTime();
                long duration = (currentPageEndTime - currentPageStartTime) / 1000000;
                eventLogEntry(printDurationFromNanoseconds((int) duration) + " spent scraping page " + pageNo);
                printMemoryUsageToEventLog();
            }
            catch (org.openqa.selenium.TimeoutException e)
            {
                eventLogEntry("Error in function getListOfBookURLs(): Selenium timed out when trying to access a book on catalog page " + pageNo);
                eventLogEntry(e.getMessage());
            }
            catch (org.openqa.selenium.StaleElementReferenceException e)
            {
                eventLogEntry("Error in function getListOfBookURLs(): Selenium couldn't locate a particular page element for catalog page number " + pageNo);
                eventLogEntry(e.getMessage());
            }
            catch (InterruptedException ex)
            {
                eventLogEntry("Error in function getListOfBookURLs(): Sleep function failed while scraping catalog page " + pageNo);
                eventLogEntry(ex.getMessage());
            }
            catch (Exception ex)
            {
                eventLogEntry("Error in function getListOfBookURLs(): Unhandled exception while scraping catalog page " + pageNo);
                eventLogEntry(ex.getMessage());
            }
        }

        eventLogEntry("Successfully retrieved " + listOfBookURLs.size() + " book links from " + (lastPage - firstPage + 1) + " pages");
        driver.quit();
        eventLogEntry("WebDriver instance successfully closed");
        return listOfBookURLs;
    }


    static Date parseDateFromStr(String dateStr)
    {
        SimpleDateFormat format = new SimpleDateFormat("MMMM yyyy");

        Date resp = new Date();
        try
        {
            resp = null;
            resp = format.parse(dateStr);
        }
        catch (ParseException ex)
        {
            eventLogEntry("Failed to parse date/time value from text string...");
        }
        return resp;
    }

    
    static @NotNull WebDriver getFFXDriver()
    {
        System.setProperty("webdriver.gecko.driver",GECKO_DRIVER);
        FirefoxOptions ffxOptions = new FirefoxOptions();
        FirefoxProfile profile = new FirefoxProfile();        
        ffxOptions.setCapability(FirefoxDriver.PROFILE, profile);
        ffxOptions.setHeadless(true);
        return new FirefoxDriver(ffxOptions);
    }


    static int getFirstOrLastPage(Scanner keyboard, @NotNull String page)
    {
        int resp;
        if (page.equalsIgnoreCase("first"))
        {
            do
            {
                System.out.println("Enter the first page you want to read, or enter 0 to exit: ");
                resp = keyboard.nextInt();
                if (resp < 0)
                    System.out.println("\t**ERROR: First page number must be at least 1");
            }
            while (resp < 0);
        }
        else if (page.equalsIgnoreCase("last"))
        {
            do
            {
                System.out.println("Enter the last page you want to read: ");
                resp = keyboard.nextInt();
                if (resp < 0)
                    System.out.println("\t**ERROR: Last page number must be at least 1");
            }
            while (resp < 0);
        }
        else
        {
            resp = 1;
        }
        keyboard.nextLine();
        return resp;
    }


    static boolean askIfUserWantsToLog(@NotNull Scanner keyboard)
    {
        String logEventsResponse;
        do
        {
            System.out.println("Would you like to log events to a file? [(y)es/(n)o]");
            logEventsResponse = keyboard.nextLine();
        }
        while (!logEventsResponse.equalsIgnoreCase("yes") && !logEventsResponse.equalsIgnoreCase("no") && !logEventsResponse.equalsIgnoreCase("y") && !logEventsResponse.equalsIgnoreCase("n"));

        return logEventsResponse.toLowerCase().charAt(0) == 'y';
    }

    //endregion
}