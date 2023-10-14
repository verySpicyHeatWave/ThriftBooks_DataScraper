package veryspicyheatwave.bwb_datascraper;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.List;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import static veryspicyheatwave.bwb_datascraper.WebRetryTool.*;

public class ThriftBooks_DataScraper
{
    //region Class-Wide Variables
    final static String BASE_URL = "https://www.thriftbooks.com";
    final static String FILTER_URL = "&b.pp=30&b.f.lang[]=40&b.pt=1&b.f.t[]=";
    final static String GECKO_DRIVER = "geckodriver.exe";
    final static long TIME_STAMP = System.currentTimeMillis();
    final static String SAVE_FILE = "dataScrape_" + TIME_STAMP +".csv";
    final static String LOG_FILE = "dataScrape_" + TIME_STAMP +".log";
    final static String FAIL_FILE = "dataScrape_FAILS.csv";
    final static String URL_FILE = "dataScrape_URLs.csv";
    static boolean loggingEvents = false;
    static Genre filterGenre;
    static PrimaryFilter filterPrimary;
    static long[] averageTimes;
    //endregion


    public static void main(String[] args) throws IOException, ParseException
    {
        printBanner();
        Scanner keyboard = new Scanner(System.in);
        int firstPage = 0, lastPage = 0;

        String scrapeMode = askWhatPagesToScrape(keyboard);
            if (scrapeMode.equalsIgnoreCase("n"))
            {
                do
                {
                    firstPage = getFirstOrLastPage(keyboard, "first");
                    if (firstPage == 0)
                    {
                        eventLogEntry("User chose to exit program...");
                        return;
                    }
                    lastPage = getFirstOrLastPage(keyboard, "last");
                    if (lastPage < firstPage)
                        System.out.println("**ERROR: Last page can't be smaller than first page!\t");
                }
                while (lastPage < firstPage);

                filterPrimary = getPrimaryFilter(keyboard);
                filterGenre = getBookGenre(keyboard);
                eventLogEntry("User selected catalog pages " + firstPage + " to " + lastPage + " filtering by the " + filterPrimary.getDisplayString() + " " + filterGenre.getDisplayString() + " books");
        }

        loggingEvents = askIfUserWantsToLog(keyboard);
        if (loggingEvents)
            eventLogEntry("Log file generated");

        writeLineToCSV("Title, Author, Used Price, New Price, Genre, Format, ISBN Code, Release Date," +
                        "Page Length, Language, ThriftBooks URL");
        eventLogEntry("CSV file generated");

        keyboard.close();
        long startTime = System.currentTimeMillis();
        System.out.println(startTime);

        if (scrapeMode.equalsIgnoreCase("n"))
            scrapeBookPages(getListOfBookURLs(firstPage, lastPage));

        else if (scrapeMode.equalsIgnoreCase("f"))
            scrapeBookPages(getListOfURLsFromFile(new File(FAIL_FILE)));

        else if (scrapeMode.equalsIgnoreCase("p"))
            scrapeBookPages(getListOfURLsFromFile(new File(URL_FILE)));

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        eventLogEntry("Full job complete in " + getFormattedDurationString((int)duration));
        eventLogEntry("Average of " + getFormattedDurationString((int)getAverageTime(averageTimes)) + " per book");
    }


    //region Primary WebScraper Methods
    static @NotNull ArrayList<String> getListOfBookURLs(int firstPage, int lastPage) throws IOException
    {
        WebDriver driver = getFFXDriver();
        ArrayList<String> listOfBookURLs = new ArrayList<>();
        eventLogEntry("Created the webdriver object to scrape for the book URLs");
        Wait<WebDriver> wait = new WebDriverWait(driver, 4);
        int numberOfBooks = 0;

        for (int pageNo = firstPage; pageNo < lastPage + 1; pageNo++)
        {
            try
            {
                long currentPageStartTime = System.nanoTime();
                String pageURL = BASE_URL + filterPrimary.getFilterString() + pageNo + FILTER_URL + filterGenre.getFilterNo();

                performWebActionWithRetries(() -> {
                    driver.get("about:blank");
                    return new ArrayList<>();
                }, "load a blank page", 4);
                Thread.sleep(666);

                performWebActionWithRetries(() -> {
                    driver.get(pageURL);
                    return new ArrayList<>();
                }, "load the URL for catalog page " + pageNo, 4);
                Thread.sleep(2000);

                WebElement searchContainer = performWebActionWithRetries(() -> {
                    String xpathExpression = "/html/body/div[4]/div/div[2]/div/div[2]/div/div/div/div/div[2]/div[2]/div[1]/div/div[1]";
                    return listGetterXPath(driver, wait, xpathExpression, true);
                }, "collect catalog page " + pageNo + "'s search results", 4).get(0);

                List<WebElement> books = performWebActionWithRetries(() -> {
                    String tagName = "a";
                    return listGetterTagName(searchContainer, wait, tagName, false);
                }, "find the books in the search results container",4);

                for (WebElement book : books)
                {
                    String bookURL = book.getAttribute("href");
                    listOfBookURLs.add(bookURL);
                    writeURLsToFile(bookURL, new File(URL_FILE));
                }
                int booksRetrieved = listOfBookURLs.size() - numberOfBooks;
                numberOfBooks = listOfBookURLs.size();
                eventLogEntry("Got a list of " + booksRetrieved + " book links from page " + pageNo);
                long currentPageEndTime = System.nanoTime();
                long duration = (currentPageEndTime - currentPageStartTime) / 1000000;
                eventLogEntry(getFormattedDurationString((int) duration) + " spent scraping page " + pageNo);
            }
            catch (RuntimeException | InterruptedException e)
            {
                eventLogEntry("Error: Exception while trying to access a book on catalog page " + pageNo);
                eventLogEntry(e.getMessage());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                eventLogEntry(sw.toString());
                sw.close();
            }
        }

        eventLogEntry("Successfully retrieved " + listOfBookURLs.size() + " book links from " + (lastPage - firstPage + 1) + " pages");
        driver.quit();
        eventLogEntry("WebDriver instance successfully closed");
        return listOfBookURLs;
    }


    static void scrapeBookPages(@NotNull ArrayList<String> listOfBookURLs) throws IOException, ParseException
    {
        WebDriver driver = getFFXDriver();
        eventLogEntry("Created the webdriver object to scrape the book URLs for book data");
        Wait<WebDriver> wait = new WebDriverWait(driver, 4);
        int bookSuccesses = 0, bookFailures = 0, bookDuplicates = 0;
        int avgIndex = 0;
        averageTimes = new long[listOfBookURLs.size()];

        for (String bookURL : listOfBookURLs)
        {
            long currentBookStartTime = System.nanoTime();
            BookEntry book = new BookEntry();
            book.link = bookURL;
            try
            {
                performWebActionWithRetries(() -> {
                    driver.get(bookURL);
                    return new ArrayList<>();
                    }, "load book number " + listOfBookURLs.indexOf(bookURL) + " book URL", 4);
                Thread.sleep(400);
                parseTitleAuthor(driver.getTitle(), book);

                WebElement pageContents = performWebActionWithRetries(() -> {
                    String xpathExpression = "//div[@class='Content']";
                    return listGetterXPath(driver, wait, xpathExpression, true);
                }, "build a list of all of the book page contents", 4).get(0);
                book.genre = parseGenreString(pageContents, wait);

                WebElement titleBlock = performWebActionWithRetries(() -> {
                    String xpathExpression = "//h1[@class='WorkMeta-title Alternative Alternative-title']";
                    return listGetterXPath(driver, wait, xpathExpression, true);
                }, "locate and parse the title block", 4).get(0);
                if (titleBlock.getText() != null)
                    book.title = titleBlock.getText();

                WebElement table = performWebActionWithRetries(() -> {
                    String xpathExpression = "//div[@class='WorkMeta-details is-collapsed']";
                    return listGetterXPath(driver,wait,xpathExpression,true);
                }, "find the paperback book data table", 4).get(0);
                parseTableDetails(table, wait, book);

                parseButtonContainer(driver, wait, book);

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

                String dataEntry = String.format("'%s','%s',%.2f,%.2f,'%s','%s','%s','%s',%d,'%s','%s'",
                        book.title.replace("'", "\\'"), book.author.replace("'", "\\'"), book.usedPrice, book.newPrice, book.genre.replace("'", "\\'"),
                        book.format, book.isbnCode, formatter.format(book.releaseDate),
                        book.pageLength, book.language, book.link);

                writeLineToCSV(dataEntry);
                insertBookIntoSQLdb(dataEntry);
                eventLogEntry(String.format("Successfully added book number %,d titled \"%s\" to the SQL database", (1 + listOfBookURLs.indexOf(bookURL)), book.title));

                if (!(book.paperbackImageLink == null))
                    downloadImageFromURL(book.paperbackImageLink,book.title,"01");

                if (!(book.massImageLink == null))
                    downloadImageFromURL(book.massImageLink,book.title,"02");

                eventLogEntry(String.format("Successfully added book number %,d titled \"%s\" to the CSV file", (1 + listOfBookURLs.indexOf(bookURL)), book.title));
                bookSuccesses++;
            }
            catch (InterruptedException | RuntimeException | SQLException | ClassNotFoundException e)
            {
                eventLogEntry("Error: Exception while parsing the page for book number " + (1 + listOfBookURLs.indexOf(bookURL)) + ": " + bookURL);
                eventLogEntry(e.getMessage());
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                eventLogEntry(sw.toString());
                sw.close();
                if (!(e.getMessage().contains("null")) && e.getMessage().contains("Duplicate entry"))
                {
                    bookDuplicates++;
                }
                else
                {
                    writeURLsToFile(bookURL, new File(FAIL_FILE));
                    bookFailures++;
                }
            }
            finally
            {
                System.out.println(bookSuccesses + " successes, " + bookFailures + " failures, " + bookDuplicates + " duplicate entries");
                long currentBookEndTime = System.nanoTime();
                long duration = (currentBookEndTime - currentBookStartTime) / 1000000;
                averageTimes[avgIndex] = duration;
                avgIndex++;
                eventLogEntry(getFormattedDurationString((int) duration) + " spent on scraping book " + (1 + listOfBookURLs.indexOf(bookURL)));
            }
        }

        driver.quit();
        eventLogEntry("WebDriver instance successfully closed");
        eventLogEntry("Web scraping complete: " + bookSuccesses + " books successfully added");
        eventLogEntry(bookFailures + " books could not be processed");
        eventLogEntry(bookDuplicates + " books were duplicate entry attempts to the database");
    }
    //endregion


    //region WebElement Parsers
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


    static void parseButtonContainer(@NotNull WebDriver driver, Wait<WebDriver> wait, BookEntry book) throws InterruptedException, StaleElementReferenceException, TimeoutException
    {
        WebElement buttonContainer = performWebActionWithRetries(() -> {
            String xpathExpression = "//div[@class='WorkSelector-row WorkSelector-td-height']";
            return listGetterXPath(driver, wait, xpathExpression, true);
        }, "locate the button container", 4).get(0);

        List<WebElement> buttons = performWebActionWithRetries(() -> {
            String tagName = "button";
            return listGetterTagName(buttonContainer, wait, tagName, false);
        }, "get the list of book binding buttons", 4);

        for (WebElement button : buttons)
        {
            List<WebElement> buttDetails = performWebActionWithRetries(() -> {
                String xpathExpression = "./child::div";
                return listGetterXPath(button,wait,xpathExpression,false);
            }, "get the button details for a given button", 4);
            for (WebElement buttDetail : buttDetails)
            {
                if (buttDetail.getText().equalsIgnoreCase("paperback"))
                {
                    performWebActionWithRetries(() -> {
                        JavascriptExecutor js = (JavascriptExecutor)driver;
                        js.executeScript("arguments[0].click();", button);
                        return new ArrayList<>();
                    }, "click the paperback filter button", 4);

                    WebElement priceRangeElement = performWebActionWithRetries(() -> {
                        String xpathExpression = "./following-sibling::div[@class='']//span";
                        return listGetterXPath(buttDetail,wait,xpathExpression,true);
                    }, "identify the paperback price range", 4).get(0);
                    if (priceRangeElement.getText().contains("$"))
                    {
                        getNewAndUsedPrices(book.paperbackPrices, priceRangeElement.getText());
                    }

                    WebElement image = performWebActionWithRetries(() -> {
                        String xpathExpression = "//img[@itemprop='image']";
                        return listGetterXPath(driver,wait,xpathExpression,true);
                    }, "store the paperback image link", 4).get(0);
                    book.paperbackImageLink = image.getAttribute("src");

                    continue;
                }



                if (buttDetail.getText().equalsIgnoreCase("mass market paperback"))
                {
                    performWebActionWithRetries(() -> {
                        JavascriptExecutor js = (JavascriptExecutor)driver;
                        js.executeScript("arguments[0].click();", button);
                        return new ArrayList<>();
                    }, "click the mass market paperback filter button", 4);

                    WebElement priceRangeElement = performWebActionWithRetries(() -> {
                        String xpathExpression = "./following-sibling::div[@class='']//span";
                        return listGetterXPath(buttDetail,wait,xpathExpression,true);
                    }, "identify the mass market paperback price range", 4).get(0);
                    if (priceRangeElement.getText().contains("$"))
                    {
                        getNewAndUsedPrices(book.massMarketPrices, priceRangeElement.getText());
                    }

                    WebElement image = performWebActionWithRetries(() -> {
                        String xpathExpression = "//img[@itemprop='image']";
                        return listGetterXPath(driver,wait,xpathExpression,true);
                    }, "store the mass market paperback image link", 4).get(0);
                    book.massImageLink = image.getAttribute("src");

                    continue;
                }

                if (buttDetail.getText().equalsIgnoreCase("hardcover"))
                {
                    WebElement priceRangeElement = performWebActionWithRetries(() -> {
                        String xpathExpression = "./following-sibling::div[@class='']//span";
                        return listGetterXPath(buttDetail,wait,xpathExpression,true);
                    }, "identify the hardcover price range", 4).get(0);
                    if (priceRangeElement.getText().contains("$"))
                    {
                        getNewAndUsedPrices(book.hardcoverPrices, priceRangeElement.getText());
                    }
                }
            }
        }
        determineBestPriceSet(book);
    }


    static void parseTableDetails(@NotNull WebElement table, Wait<WebDriver> wait, BookEntry book) throws StaleElementReferenceException, TimeoutException, InterruptedException
    {
        List<WebElement> elements = performWebActionWithRetries(() -> {
            String tagName = "span";
            return listGetterTagName(table,wait,tagName,false);
        }, "get the book details from the lower table", 4);
        int index = -1;

        for (WebElement detail : elements)
        {
            index++;
            if (detail.getText().toLowerCase().contains("isbn"))
            {
                if (elements.get(index + 1).getText().length() < 13)
                {
                    book.isbnCode = elements.get(index + 1).getText();
                    continue;
                }
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
                if (Integer.parseInt(tempPageLength) > 10)
                {
                    book.pageLength = Integer.parseInt(tempPageLength);
                    continue;
                }
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


    static String parseGenreString(@NotNull WebElement pageContents, Wait<WebDriver> wait) throws InterruptedException
    {
        List<WebElement> spans = performWebActionWithRetries(() -> {
            String tagName = "span";
            return listGetterTagName(pageContents,wait,tagName,false);
        }, "find the genre element at the top of the page", 4);

        for (WebElement span : spans)
        {
            if (span.getAttribute("itemprop") != null && span.getAttribute("itemprop").toLowerCase().contains("name"))
            {
                return span.getText();
            }
        }
        return filterGenre.getDisplayString();
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
    //endregion


    //region SQL Methods
    static void insertBookIntoSQLdb(String csvStr) throws FileNotFoundException, ClassNotFoundException, SQLException
    {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String pwSQL = getSQLPassword();
        Connection con = null;
        try
        {
            String insertionQuery = "INSERT INTO books(title,author,used_price,new_price,genre,binding_type,isbn_code,release_date,page_length,language,bookURL) VALUES";
            insertionQuery += "(" + csvStr + ");";

            con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/thriftBooksDB", "root", pwSQL);
            Statement statement = con.createStatement();
            statement.execute(insertionQuery);
            con.close();
        } catch (SQLException sqlE)
        {
            assert con != null;
            con.close();
            throw sqlE;
        }
    }


    static String getSQLPassword() throws FileNotFoundException
    {
        File pwFile = new File("pw.txt");
        Scanner pwFileScan = new Scanner(pwFile);
        String resp = pwFileScan.nextLine();
        pwFileScan.close();
        return resp;
    }
    //endregion


    //region File I/O and Print Methods
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


    static void writeURLsToFile(String dataEntry, File writeFile)
    {
        try (FileWriter writer = new FileWriter(writeFile, true))
        {
            writer.write(dataEntry + "\n");
        }
        catch (IOException ex)
        {
            System.out.println("ERROR: Failed to write to" + writeFile + " Oops!");
        }
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


    static void downloadImageFromURL(@NotNull String imageURL, @NotNull String bookTitle, String imageNumber)
    {
        String cleanBookTitle = bookTitle.replace(":"," -");
        if (imageURL.equalsIgnoreCase("null"))
        {
            eventLogEntry("No image URL was found");
            return;
        }

        Path fileOutputPath = Paths.get("images/" + cleanBookTitle);
        File fileOutputStr =  new File(fileOutputPath + "/" + imageNumber + ".jpg");
        if (!Files.exists(fileOutputPath))
            if (!(new File(String.valueOf(fileOutputPath)).mkdirs()))
            {
                eventLogEntry("Error while trying to create directory " + fileOutputPath);
                return;
            }

        try (BufferedInputStream in = new BufferedInputStream(new URI(imageURL).toURL().openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(fileOutputStr))
        {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1)
            {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
            eventLogEntry("Created new image file: " + fileOutputStr);
        }
        catch (IOException | InvalidPathException | URISyntaxException e)
        {
            eventLogEntry("Error while creating image file: " + e.getMessage());
        }
    }


    static @NotNull ArrayList<String> getListOfURLsFromFile(@NotNull File readFile) throws FileNotFoundException
    {
        if (!readFile.exists())
        {
            eventLogEntry(readFile + " does not exist! No pages will be scraped");
            return new ArrayList<>();
        }
        Scanner readFileScan = new Scanner(readFile);
        ArrayList<String> failURLs = new ArrayList<>();

        while (readFileScan.hasNext())
        {
            failURLs.add(readFileScan.nextLine());
        }
        readFileScan.close();
        if (readFile.equals(new File(FAIL_FILE)))
            if (readFile.delete())
                System.out.println("Deleted original fail file before proceeding");

        return failURLs;
    }
    //endregion


    //region User Interaction Methods
    static int getFirstOrLastPage(Scanner keyboard, @NotNull String page)
    {
        String introStr, errorStr;
        int resp;
        if (page.equalsIgnoreCase("first"))
        {
            introStr = "Enter the first page you want to read, or enter 0 to exit: ";
            errorStr = "First";
        }
        else
        {
            introStr = "Enter the last page you want to read: ";
            errorStr = "Last";
        }
        do
        {
            System.out.println(introStr);
            resp = keyboard.nextInt();
            if (resp < 0)
                System.out.println("\t**ERROR: " + errorStr + " page number must be at least 1");
            else if (resp > 333)
                System.out.println("\t**ERROR: " + errorStr + " page number cannot be more than 333");
        }
        while (resp < 0 || resp > 333);

        keyboard.nextLine();
        return resp;
    }


    static boolean askIfUserWantsToLog(@NotNull Scanner keyboard)
    {
        String userResponse;
        do
        {
            System.out.println("Would you like to log events to a file? [(y)es/(n)o]");
            userResponse = keyboard.nextLine();
        }
        while (!userResponse.equalsIgnoreCase("yes")
                && !userResponse.equalsIgnoreCase("no")
                && !userResponse.equalsIgnoreCase("y")
                && !userResponse.equalsIgnoreCase("n"));

        return userResponse.toLowerCase().charAt(0) == 'y';
    }


    static String askWhatPagesToScrape(@NotNull Scanner keyboard)
    {
        String userResponse;
        do
        {
            System.out.println("""
                    Would you like to:
                        Scrape [n]ew pages?
                        Scrape [f]ailed pages?
                        Scrape [p]ast pages?
                        """);
            userResponse = keyboard.nextLine();
        }
        while (!userResponse.equalsIgnoreCase("n")
                && !userResponse.equalsIgnoreCase("f")
                && !userResponse.equalsIgnoreCase("p"));
        return userResponse;
    }


    static PrimaryFilter getPrimaryFilter(Scanner keyboard)
    {
        System.out.println("Enter the number of which way you'd like the pages to be filtered: ");

        for (PrimaryFilter g : PrimaryFilter.values())
        {
            System.out.printf("[%d]: %s\n", g.ordinal(), g.getDisplayString());
        }
        int selection = keyboard.nextInt();

        while (selection < 0 || selection > PrimaryFilter.values().length)
        {
            System.out.println("Invalid selection. Try again. ");
            selection = keyboard.nextInt();
        }
        keyboard.nextLine();
        return PrimaryFilter.values()[selection];
    }


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
    //endregion


    //region Misc Algorithms and Utility Methods
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
            if (priceLists.get(i).newPrice <= 0)
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

        PriceStructure returnBook = new PriceStructure("Undefined", "Undefined");
        returnBook.newPrice = 1000000000;
        returnBook.usedPrice = 1000000000;

        for (int i = 0; i <= priceLists.size() - 1; i++)
        {
            if (priceLists.get(i).newPrice < returnBook.newPrice)
            {
                returnBook = priceLists.get(i);
            }
        }

        if (returnBook.usedPrice == 1000000000)
        {
            returnBook.usedPrice = 0;
            returnBook.newPrice = 0;
        }
        book.newPrice = returnBook.newPrice;
        book.usedPrice = returnBook.usedPrice;
        book.format = returnBook.format;
        book.buttonName = returnBook.buttonName;
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


    static @NotNull String getFormattedDurationString(int duration)
    {
        int millis = 0, seconds = 0, minutes = 0, hours = 0, days = 0;
        String timeString = "";

        if (duration > 1000)
        {
            seconds = (duration / 1000);
            millis = (duration % 1000);
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
        if (hours > 24)
        {
            days = hours / 24;
            hours = hours % 24;
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


    @Contract(pure = true)
    static long getAverageTime(long @NotNull [] averageTimes)
    {
        long sum = 0;
        for (long time : averageTimes)
        {
            sum += time;
        }
        return sum / averageTimes.length;

    }
    //endregion
}