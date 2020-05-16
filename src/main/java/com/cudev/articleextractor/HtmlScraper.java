package com.cudev.articleextractor;

import de.l3s.boilerpipe.document.TextBlock;
import de.l3s.boilerpipe.extractors.ArticleSentencesExtractor;
import de.l3s.boilerpipe.extractors.ExtractorBase;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.SAXException;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class HtmlScraper {

    private static final ExtractorBase extractor = ArticleSentencesExtractor.getInstance();

    static {
        // avoid SSL certificate errors
        trustAllHttpsCertificates();
    }

    public static class Results {
        public String url;
        public String title;
        public List<String> textBlocks;
        public List<Link> links;

        Results() {
        }

        @Override
        public String toString() {
            return String.format("Results{ %s <%s>: %d links, %d textBlocks }",
                    title, url, links.size(), textBlocks.size());
        }
    }

    public static Results scrape(String url) throws IOException, TikaException, SAXException {
        // fetch HTML
        InputStream input = new URL(url).openStream();

        // setup extractors
        LinkContentHandler linkHandler = new LinkContentHandler();
        BoilerpipeContentHandler textHandler =
                new BoilerpipeContentHandler(new BodyContentHandler(), extractor);

        // parse using boilerpipe+tika
        Metadata metadata = new Metadata();
        HtmlParser parser = new HtmlParser();
        parser.parse(input,
                new TeeContentHandler(linkHandler, textHandler),
                metadata,
                new ParseContext());

        // gather results
        Results results = new Results();
        results.url = url;
        results.title = metadata.get("title");

        List<TextBlock> blocks = textHandler.getTextDocument().getTextBlocks();
        results.textBlocks = blocks.stream()
                //.filter(TextBlock::isContent)
                .map(TextBlock::getText)
                .collect(Collectors.toList());

        // keep only <a> tags with non-empty href + avoid duplicates
        results.links = linkHandler.getLinks().stream()
                .filter(l -> l.isAnchor() &&
                        !l.getUri().isEmpty() &&
                        !l.getUri().startsWith("#"))
                .filter(distinctBy(Link::getUri))
                .collect(Collectors.toList());

        return results;
    }


    // ---------------------------------------------------
    // utilities

    // distinct by property in a Java stream
    private static <T> Predicate<T> distinctBy(Function<? super T, ?> keyExtractor) {
        final Set<Object> seen = new HashSet<>();
        return t -> seen.add(keyExtractor.apply(t));
    }

    // Make the URL#getConnection accept unsecure HTTPS certificates as well to avoid
    // `java.net.ssl.SSLHandshakeException`. Just call it once per JVM.
    // see http://www.rgagnon.com/javadetails/java-fix-certificate-problem-in-HTTPS.html
    // for more details
    private static void trustAllHttpsCertificates() {
        try {
            TrustManager[] dummyTrustManagers = new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}
                public X509Certificate[] getAcceptedIssuers() { return null; }
            }};

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, dummyTrustManagers, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create and install all-trusting host name verifier
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String s, SSLSession sslSession) { return true; }
            });
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            System.out.println("Error setting up dummy certificate: " + e);
        }
    }
}
