package com.roxstudio.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class CUrlTest {

    @Test
    public void gzippedResponse() {
        CUrl curl = new CUrl("http://httpbin.org/gzip")
                .opt("--compressed") // Suggest the server using gzipped response
                .proxy("127.0.0.1", 8888).opt("-k");
        curl.exec();
        assertEquals(curl.getHttpCode(), 200);
    }

    @Test
    public void headMethod() {
        CUrl curl = new CUrl("http://httpbin.org/get")
                .dumpHeader("-") // output to stdout
                .opt("--head");
        curl.exec();
        assertEquals(curl.getHttpCode(), 200);
    }

    @Test
    public void getRedirectLocation() {
        CUrl curl = new CUrl("http://httpbin.org/redirect-to")
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
        CUrl curl = new CUrl("https://httpbin.org/get")
                .proxy("127.0.0.1", 8888) // Use Fiddler to capture & parse HTTPS traffic
                .opt("-k");  // Ignore certificate check since it's issued by Fiddler
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void httpPost() {
        CUrl curl = new CUrl("http://httpbin.org/post")
                .data("hello=world&foo=bar")
                .data("foo=overwrite");
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void uploadMultipleFiles() {
        CUrl.MemIO inMemFile = new CUrl.MemIO();
        try { inMemFile.getOutputStream().write("text file content blabla...".getBytes()); } catch (Exception ignored) {}
        CUrl curl = new CUrl("http://httpbin.org/post")
                .form("formItem", "value") // a plain form item
                .form("file", inMemFile)           // in-memory "file"
                .form("image", new CUrl.FileIO("D:\\tmp\\a2.png")); // A file in storage, change it to an existing path to avoid failure
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void httpBasicAuth() {
        CUrl curl = new CUrl("http://httpbin.org/basic-auth/abc/aaa")
                .proxy("127.0.0.1", 8888)
                .opt("-u", "abc:aaa");
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @Test
    public void mobileUserAgent() {
        CUrl curl = new CUrl("http://httpbin.org/get")
                .opt("-A", "Mozilla/5.0 (Linux; U; Android 8.0.0; zh-cn; KNT-AL10 Build/HUAWEIKNT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) MQQBrowser/7.3 Chrome/37.0.0.0 Mobile Safari/537.36");
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void customResolver() {
        CUrl curl = new CUrl("http://httpbin.org/get?a=b");
        Map<String, Object> json = curl.exec(jsonResolver, null);
        assertEquals(200, curl.getHttpCode());
        assertEquals("b", deepGet(json, "args.a"));
    }

    @Test
    public void threadSafeCookies() {
        final CountDownLatch count = new CountDownLatch(3);
        final CUrl[] curls = new CUrl[3];
        for (int i = 3; --i >= 0;) {
            final int idx = i;
            new Thread() {
                public void run() {
                    CUrl curl = curls[idx] = new CUrl("http://httpbin.org/get")
                            .cookie("value=" + idx);
                    curl.exec();
                    count.countDown();
                }
            }.start();
        }
        try { count.await(); } catch (Exception ignored) {} // make sure all requests are done
        assertEquals(200, curls[0].getHttpCode());
        assertEquals("value=0", deepGet(curls[0].getStdout(jsonResolver, null), "headers.Cookie"));
        assertEquals("value=1", deepGet(curls[1].getStdout(jsonResolver, null), "headers.Cookie"));
        assertEquals("value=2", deepGet(curls[2].getStdout(jsonResolver, null), "headers.Cookie"));
    }

    private CUrl.Resolver<Map<String, Object>> jsonResolver = new CUrl.Resolver<Map<String, Object>>() {
        @SuppressWarnings("unchecked")
        @Override
        public Map<String, Object> resolve(int httpCode, byte[] responseBody) throws Throwable {
            String json = new String(responseBody, "UTF-8");
            return new ObjectMapper().readValue(json, Map.class);
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
