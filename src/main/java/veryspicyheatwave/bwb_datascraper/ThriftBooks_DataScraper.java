package veryspicyheatwave.bwb_datascraper;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ThriftBooks_DataScraper
{
    
    static String BASE_URL = "https://www.thriftbooks.com";
    public static void main(String[] args)
    {       
        for (int i = 1; i < 3; i++)
        {
            try
            {   
                String urlToScrape = "https://www.thriftbooks.com/browse/#b.s=bestsellers-desc&b.p=" + i + "&b.pp=50&b.nr";
                
                Document docdoc = Jsoup.connect(urlToScrape)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/116.0.2")
                    .referrer("http://www.microsoft.com")
                    .timeout(5000)
                    .get();
                                
                System.out.println(urlToScrape + "\n");

                //Element content = docdoc.getElementById("content");
                Elements books = docdoc.getElementsByClass("SearchResultGridItem undefined");
                                     
                for (Element book : books)
                {
                    //System.out.println(book.className());

                    String title = book.select("p").first().text();
                    System.out.println(title);
                    String author = book.select("p").prev().text();
                    author = author.replaceAll(title + " ", "");
                    System.out.println(author);
                    String priceRance = book.select("p").last().text();
                    System.out.println(priceRance);
                    String bookURL = BASE_URL + book.select("a").attr("href");
                    System.out.println(bookURL + "\n");
                    //String link = book.select("a").attr("href");
                    //System.out.println(link);
                }
            //System.out.println(content.childrenSize());                    
            }
            catch (IOException e)
            {
                e.printStackTrace();
                System.out.println("Bummer, dude...");
            }
        }
        
            
        //System.out.println("Hello, biiiitch");
    }
}
