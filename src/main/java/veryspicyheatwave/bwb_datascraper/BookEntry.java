package veryspicyheatwave.bwb_datascraper;

import java.util.Date;

class BookEntry
{
    String title;
    String author;
    String link;
    String isbnCode;
    Date releaseDate;
    int pageLength;
    String language;
    String genre;
    String format;
    double newPrice;
    double usedPrice;
    String paperbackImageLink;
    String massImageLink;

    PriceStructure massMarketPrices = new PriceStructure("Paperback");
    PriceStructure paperbackPrices = new PriceStructure("Paperback");
    PriceStructure hardcoverPrices = new PriceStructure("Hardcover");


    BookEntry (){}
}