package com.cudev.articleextractor;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.Link;
import org.xml.sax.SAXException;

import java.io.IOException;

class Main {
    static final String url = "http://www.flagsarenotlanguages.com/blog/why-flags-do-not-represent-language/";


    public static void main(String[] args) throws TikaException, IOException, SAXException {
        HtmlScraper.Results results = HtmlScraper.scrape(url);
        System.out.println(results);
        System.out.println("\nTextBlocks\n=================");
        results.textBlocks.forEach(System.out::println);
        System.out.println("\nLinks\n=================");
        results.links.stream().map(Link::getUri).forEach(System.out::println);
    }
}
