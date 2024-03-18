package com.roxstudio.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CUrlTest {

    private static final boolean ENABLE_FIDDLER_FOR_ALL_TEST = true;

    @Test
    public void gzippedResponse() {
        CUrl curl = curl("http://httpbin.org/gzip")
                .opt("--compressed"); // Suggest the server using gzipped response
        curl.exec();
        assertEquals(curl.getHttpCode(), 200);
    }

    @Test
    public void headMethod() {
        CUrl curl = curl("http://httpbin.org/get")
                .dumpHeader("-") // output to stdout
                .opt("--head");
        curl.exec();
        assertEquals(curl.getHttpCode(), 200);
    }

    @Test
    public void getRedirectLocation() {
        CUrl curl = curl("http://httpbin.org/redirect-to")
                .data("url=http://www.baidu.com", "UTF-8") // do URL-Encoding for each form value
                .opt("--get"); // force using GET method
        curl.exec();
        String location = null;
        List<String[]> responseHeaders = curl.getResponseHeaders().get(0); // Follow redirect is disabled, only one response here
        for (String[] headerValuePair: responseHeaders) {
            if ("Location".equals(headerValuePair[0])) {
                location = headerValuePair[1];
                break;
            }
        }
        assertEquals(302, curl.getHttpCode());
        assertEquals(location, "http://www.baidu.com");
    }

    @Test
    public void insecureHttpsViaFiddler() {
        CUrl curl = curl("https://httpbin.org/get")
                .proxy("127.0.0.1", 8888) // Use Fiddler to capture & parse HTTPS traffic
                .insecure();  // Ignore certificate check since it's issued by Fiddler
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void httpPost() {
        CUrl curl = curl("http://httpbin.org/post")
                .data("hello=world&foo=bar")
                .data("foo=overwrite");
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void uploadMultipleFiles() {
        CUrl.MemIO inMemFile = new CUrl.MemIO();
        try { inMemFile.getOutputStream().write("text file content blabla...".getBytes()); } catch (Exception ignored) {}
        CUrl curl = curl("http://httpbin.org/post")
                .form("formItem", "value") // a plain form item
                .form("file", inMemFile)           // in-memory "file"
                .form("image", new CUrl.FileIO("src/test/resources/a2.png")); // A file in storage, change it to an existing path to avoid failure
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void httpBasicAuth() {
        CUrl curl = curl("http://httpbin.org/basic-auth/abc/aaa")
                .proxy("127.0.0.1", 8888)
                .opt("-u", "abc:aaa");
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void customUserAgentAndHeaders() {
        String mobileUserAgent = "Mozilla/5.0 (Linux; U; Android 8.0.0; zh-cn; KNT-AL10 Build/HUAWEIKNT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) MQQBrowser/7.3 Chrome/37.0.0.0 Mobile Safari/537.36";
        Map<String, String> fakeAjaxHeaders = new HashMap<String, String>();
        fakeAjaxHeaders.put("X-Requested-With", "XMLHttpRequest");
        fakeAjaxHeaders.put("Referer", "http://somesite.com/fake_referer");
        CUrl curl = curl("http://httpbin.org/get")
                .opt("-A", mobileUserAgent) // simulate a mobile browser
                .headers(fakeAjaxHeaders)   // simulate an AJAX request
                .header("X-Auth-Token: xxxxxxx"); // other custom header, this might be calculated elsewhere
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void customResolver() {
        CUrl curl = curl("http://httpbin.org/json");
        // execute request and convert response to JSON
        Map<String, Object> json = curl.exec(jsonResolver, null);
        assertEquals(200, curl.getHttpCode());
        assertEquals("Yours Truly", deepGet(json, "slideshow.author"));
        assertEquals("Why <em>WonderWidgets</em> are great", deepGet(json, "slideshow.slides.1.items.0"));
        // execute request and convert response to HTML
        curl = curl("http://httpbin.org/html");
        Document html = curl.exec(htmlResolver, null);
        assertEquals(200, curl.getHttpCode());
        assertEquals("Herman Melville - Moby-Dick", html.select("h1:first-child").text());
    }

    @Test
    public void threadSafeCookies() {
        final CountDownLatch count = new CountDownLatch(3);
        final CUrl[] curls = new CUrl[3];
        for (int i = 3; --i >= 0;) {
            final int idx = i;
            new Thread() {
                public void run() {
                    CUrl curl = curls[idx] = curl("http://httpbin.org/get")
                            .cookie("thread" + idx + "=#" + idx);
                    curl.exec();
                    count.countDown();
                }
            }.start();
        }
        try { count.await(); } catch (Exception ignored) {} // make sure all requests are done
        assertEquals(200, curls[0].getHttpCode());
        assertEquals("thread0=#0", deepGet(curls[0].getStdout(jsonResolver, null), "headers.Cookie"));
        assertEquals("thread1=#1", deepGet(curls[1].getStdout(jsonResolver, null), "headers.Cookie"));
        assertEquals("thread2=#2", deepGet(curls[2].getStdout(jsonResolver, null), "headers.Cookie"));
    }

    @Test
    public void reuseCookieAcrossThreads() {
        final CUrl.IO cookieJar = new CUrl.MemIO();
        final CountDownLatch lock = new CountDownLatch(1);
        new Thread() {
            public void run() {
                CUrl curl = curl("http://httpbin.org/cookies/set/from/server") // server-side Set-Cookie response header
                        .cookie("foo=bar; hello=world") // multiple cookies are seperated by "; "
                        .cookieJar(cookieJar); // write cookies to an IO instance
                curl.exec();
                lock.countDown();
            }
        }.start();
        try { lock.await(); } catch (Exception ignored) {} // make sure request is done
        CUrl curl = curl("http://httpbin.org/cookies")
                .cookie(cookieJar); // reuse cookies
        curl.exec();
        assertEquals(200, curl.getHttpCode());
        assertEquals("bar", deepGet(curl.getStdout(jsonResolver, null), "cookies.foo"));
        assertEquals("world", deepGet(curl.getStdout(jsonResolver, null), "cookies.hello"));
        assertEquals("server", deepGet(curl.getStdout(jsonResolver, null), "cookies.from"));
    }

    @Test
    public void selfSignedCertificate() {
        CUrl curl = new CUrl("https://www.baidu.com/")
                .opt("--verbose")
                .cert(new CUrl.FileIO("D:/tmp/test_jks.jks"), "123456")
                .proxy("127.0.0.1", 8888);
        System.out.println(curl.exec(CUrl.UTF8, null));
    }

    @Test
    public void givenCorrectRootCA_whenGet_thenSuccess() {
        // If you have a corporate firewall that intercepts outbound TLS connections, you will need to provide your own
        // root jks bundle
        // keytool -importcert -file inputfile.pem -keystore output_file.jks -storetype jks
        CUrl curl = new CUrl("https://www.baidu.com/")
                .opt("--verbose")
                .opt("--cacert", "src/test/resources/global_sign_root_r1.jks")
                .proxy("127.0.0.1", 8888);
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void givenWrongRootCA_whenGet_thenException() {
        // any random root CA that will fail all targets
        CUrl curl = new CUrl("https://www.baidu.com/")
                .opt("--verbose")
                .opt("--cacert", "src/test/resources/random_root.jks")
                .proxy("127.0.0.1", 8888);
        String result = new String(curl.exec(), StandardCharsets.UTF_8);
        assertTrue(result.contains("unable to find valid certification path to requested target"));
    }

    ///////////////////////////////////////////////////////////////////////////////

    private CUrl curl(String url) {
        CUrl curl = new CUrl(url);
        if (ENABLE_FIDDLER_FOR_ALL_TEST) {
            curl.proxy("127.0.0.1", 8888).insecure();
        }
        return curl;
    }

    /** Implement a custom resolver that convert raw response to JSON */
    private CUrl.Resolver<Map<String, Object>> jsonResolver = new CUrl.Resolver<Map<String, Object>>() {
        @SuppressWarnings("unchecked")
        @Override
        public Map<String, Object> resolve(int httpCode, byte[] responseBody) throws Throwable {
            String json = new String(responseBody, "UTF-8");
            return new ObjectMapper().readValue(json, Map.class);
        }
    };
    /** Implement a custom resolver that convert raw response to Jsoup Document */
    private CUrl.Resolver<Document> htmlResolver = new CUrl.Resolver<Document>() {
        @SuppressWarnings("unchecked")
        @Override
        public Document resolve(int httpCode, byte[] responseBody) throws Throwable {
            String html = new String(responseBody, "UTF-8");
            return Jsoup.parse(html);
        }
    };

    @SuppressWarnings("unchecked")
    private static <T> T deepGet(Object obj, String names) {
        boolean isMap;
        if (!(isMap = obj instanceof Map) && !(obj instanceof List)) return null;
        int idx = names.indexOf('.');
        String n = idx > 0 ? names.substring(0, idx) : names;
        names = idx > 0 ? names.substring(idx + 1) : null;
        if (isMap) {
            obj = ((Map<String, ?>) obj).get(n);
        } else {
            idx = Integer.parseInt(n);
            if (idx < 0 || idx >= ((List<?>) obj).size()) return null;
            obj = ((List<?>) obj).get(idx);
        }
        return names != null ? CUrlTest.<T>deepGet(obj, names) : obj != null ? (T) obj : null;
    }

}
