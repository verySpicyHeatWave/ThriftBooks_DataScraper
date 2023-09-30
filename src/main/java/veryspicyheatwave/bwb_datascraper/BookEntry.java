package veryspicyheatwave.bwb_datascraper;

import java.util.Date;

class BookEntry
{
    String title;
    String author;
    String isbnCode;
    Date releaseDate;
    int pageLength;
    String language;
    String genre;
    String format;
    double newPrice;
    double usedPrice;
    String link;
    String paperbackImageLink;
    String massImageLink;
    String buttonName;

    PriceStructure massMarketPrices = new PriceStructure("Paperback", "mass market paperback");
    PriceStructure paperbackPrices = new PriceStructure("Paperback", "paperback");
    PriceStructure hardcoverPrices = new PriceStructure("Hardcover", "hardcover");


    BookEntry (){}
}