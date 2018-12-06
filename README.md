[![Licence](https://img.shields.io/badge/licence-Apache%20Licence%20%282.0%29-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.rockswang/java-curl.svg)](https://mvnrepository.com/artifact/com.github.rockswang/java-curl)

[中文](README_zh.md)

# Introduction
java-curl is a pure-java HTTP utility implemented based on HttpURLConnection in the standard JRE, while the usage references to the commonly-used CURL command line tool under Linux.

# Features
* Based on the standard JRE, the source compatibility level is 1.6, can be used for Java server-side, Android and other Java environments.
* The code is super compact (one java file with less than 2000 lines), without any external dependencies, can be easily reused at source level.
* Easy to use, fully compatible with the most common switches of CURL tool, can be directly used as a command line tool.
* Support all HTTP methods; Support multi-part file uploads; Support simple HTTP authentication.
* Use ThreadLocal to solve the problem that cookies can only be stored globally in standard Java, cookies are maintained isolated for each thread.
* The cookie-store in the thread can be serialized and saved, which is convenient for setting up a cookie pool.
* Support HTTPS; Support self-signed certificate (JKS/BKS); Support ignoring certificate security check.
* Support per-connection proxy; Support HTTP/HTTPS proxy authorization.
* The redirect behavior can be controlled, and the response headers of each redirect-step can be obtained.
* Support programming custom response resolver, easy to convert raw responses into JSON/HTML/XML format directly with Jackson/Gson/Jsoup/DOM4J or other 3rd-party libraries.
* Support failed retry, programmable custom recoverable exceptions.

# Description

#### About switches and shortcuts
* All switches can be passed in via CUrl.opt(...) method. For a list of supported parameters, please refer to the [table](#supported-switches).
* Some frequently used switches provide short-cut methods, please refer to the [table](#supported-switches) and source code.
* The *opt* method accepts multiple parameters and values. Note that if a CUrl switch needing a value, then the switch and value should be passed-in as two method parameters, e.g.:<br/>
  - ```curl.opt("-d", "a=b", "-L")```<br/> 
  - The above example gives two command line switches, namely post data "a=b" and following redirect automatically.

#### About CUrl.IO and its subclasses
* CURL in Linux is a command line tool that is designed to read and write physical files, while java-curl as a programming library, support ByteArray or InputStream/OutputStream objects for reading and writing.
* ```CUrl.IO``` is the abstracted interface for both input and output, its subclasses include:<br/>
  - ```CUrl.MemIO```: corresponds to a byte buffer for direct memory access<br/>
  - ```CUrl.FileIO```: corresponding to physical file<br/>
  - ```CUrl.WrappedIO```: Simple wrapper for either InputStream or OutputStream<br/>
* Multiple methods can use IO as a parameter, including:<br/>
  - ```cert(io, password)```: Read the client certificate from IO<br/>
  - ```data(io, binary)```: read POST data from IO<br/>
  - ```form(name, io)```: Read file/text item from IO to submit an multi-part post for file-uploading<br/>
  - ```cookie(io)```: Read cookies from IO<br/>
  - ```cookieJar(io)```: Save cookies to IO<br/>
  - ```dumpHeader(io)```: dumps the response header to IO<br/>
  - ```stdout/stderr```: redirect standard-output/standard-error to IO.<br/>
* Following the CURL manual, "-" can be used to represent stdout, e.g.:<br/>
  - ```curl("http://...").opt("-D", "-", "-c", "-")```<br/>
  - The above example initiates a request and outputs both the response header and website cookies to stdout.

#### About Cookies
* There are two ways for handling cookies in standard Java. The first is to handle the *Cookie* request header and the *Set-Cookie* response header from the low level. Jsoup uses this approach, but there are some problems, including:<br/>
  - In addition to the key-value pairs, *Set-Cookie* contains domain, path, expire, httpOnly and other attributes, it's possible that multiple cookies with the same name, Jsoup use Map to store cookies, sometimes it leads to problems<br/>
  - According to some real-world tests, some versions of the JRE have a bug that loses the *Set-Cookie* value.
* The second way is to use Java's own CookieManager/CookieStore, but there is a serious problem, the API design is not reasonable, CookieStore can only have one globally singleton instance. That means in one VM, if multiple requests access the same site concurrently, then they always share the same cookies, this is not acceptable in many circumstances.
* CUrl class implements a ThreadLocal-based CookieStore, each thread has a separate cookie-store, which solves the above problem perfectly.
* In addition to the ```--cookie/--cookie-jar``` parameter, you can also use ```getCookieStore``` to get the CookieStore singleton, directly call its add/getCookies and other methods to read and write the current thread's cookies.
* Note 1: This class is slightly different from the CURL tool for convenience of use. Subsequent requests from the same thread do not automatically clear the cookie store. Therefore, for different urls on the same website, you don't have to add the ```--cookie/--cookie-jar``` parameter every time.
* Note 2: If you are using a thread pool, because the threads in the pool can be reused, to avoid cookie pollution, please add a ```cookie("")``` call on the first request in the thread, which will clear the thread-local cookie-store.

#### About CUrl.Resolver and its subclasses
* ```CUrl.Resolver``` is used to directly deserialize the raw response byte array into custom Java object, such as Xml, Json, Html, etc., can be combined with DOM4J, Jackson/Gson, Jsoup and other third-party libraries.
* In the implementation of Resolver.resolve() method, if ```CUrl.Recoverable``` or its subclass instances are thrown, then this fail can be retried. If retry parameters are specified, CUrl will automatically retry the given number of times or given duration
  - Example: Even though the server API returns a response of status 200, but the business level error is "Please try again later". At this time, even if the request itself is successful, you can still throw a ```Recoverable``` to instruct CUrl to retry.

#### About HTTPS
* For sites with valid certificates issued by legal certification authorities, direct access is available.
* You can specify a self-signed certificate (since 1.2.2) using ```cert(io, password)``` or ```opt("-E", "path/to/file\:password")```.
* You can also use ```insecure()``` or ```opt("-k")``` to instruct CUrl to ignore certificate security checks.
* Currently CA certificates is not supported. If you are using a traffic capture tool to intercept HTTPS requests, please ignore the certificate security check.
* You can use openssl, keytool to convert between PEM/P12/JKS/BKS certificates file format, see Example 8.

#### About redirects
* By default, the redirect is not automatically followed. Please use ```location()``` or ```opt("-L")``` to indicate that the redirect should be automatically followed.
* Like the CURL tool, only HTTP-30X redirects are supported, does not support refresh headers, page metas, etc.
* If you do not follow the redirect, you can use ```CUrl.getLocations().get(0)``` to get the redirected location URL after getting the 30X response.

# Examples

##### Example 1：POST form submission. Two data() call demonstrations that ```--data``` switch can be specified multiple times, and the parameter values can be overwritten.
```java
    public void httpPost() {
        CUrl curl = new CUrl("http://httpbin.org/post")
                .data("hello=world&foo=bar")
                .data("foo=overwrite");
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }
```

##### Example 2: Accessing an HTTPS Site via Fiddler Proxy (Traffic Capture Tool)
```java
    public void insecureHttpsViaFiddler() {
        CUrl curl = new CUrl("https://httpbin.org/get")
                .proxy("127.0.0.1", 8888) // Use Fiddler to capture & parse HTTPS traffic
                .insecure();  // Ignore certificate check since it's issued by Fiddler
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }
```

##### Example 3: Upload multiple files, one memory file, one physical file
```java
    public void uploadMultipleFiles() {
        CUrl.MemIO inMemFile = new CUrl.MemIO();
        try { inMemFile.getOutputStream().write("text file content blabla...".getBytes()); } catch (Exception ignored) {}
        CUrl curl = new CUrl("http://httpbin.org/post")
                .form("formItem", "value") // a plain form item
                .form("file", inMemFile)           // in-memory "file"
                .form("image", new CUrl.FileIO("D:\\tmp\\a2.png")); // A file in storage
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }
```

##### Example 4: Simulate an AJAX request from a mobile browser and add a custom request header. Specify single request header with header(), or specify multiple request headers at a time with headers().
```java
    public void customUserAgentAndHeaders() {
        String mobileUserAgent = "Mozilla/5.0 (Linux; U; Android 8.0.0; zh-cn; KNT-AL10 Build/HUAWEIKNT-AL10) " 
                + "AppleWebKit/537.36 (KHTML, like Gecko) MQQBrowser/7.3 Chrome/37.0.0.0 Mobile Safari/537.36";
        Map<String, String> fakeAjaxHeaders = new HashMap<String, String>();
        fakeAjaxHeaders.put("X-Requested-With", "XMLHttpRequest");
        fakeAjaxHeaders.put("Referer", "http://somesite.com/fake_referer");
        CUrl curl = new CUrl("http://httpbin.org/get")
                .opt("-A", mobileUserAgent) // simulate a mobile browser
                .headers(fakeAjaxHeaders)   // simulate an AJAX request
                .header("X-Auth-Token: xxxxxxx"); // other custom header, this might be calculated elsewhere
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }
```

##### Example 5: Multi-threaded concurrent requests, inter-threaded cookies are isolated between each other
```java
    public void threadSafeCookies() {
        final CountDownLatch count = new CountDownLatch(3);
        final CUrl[] curls = new CUrl[3];
        for (int i = 3; --i >= 0;) {
            final int idx = i;
            new Thread() {
                public void run() {
                    CUrl curl = curls[idx] = new CUrl("http://httpbin.org/get")
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
```

##### Example 6: Programming a custom response resolver that convert raw response to HTML with Jsoup
```java
    private CUrl.Resolver<Document> htmlResolver = new CUrl.Resolver<Document>() {
        @SuppressWarnings("unchecked")
        @Override
        public Document resolve(int httpCode, byte[] responseBody) throws Throwable {
            String html = new String(responseBody, "UTF-8");
            return Jsoup.parse(html);
        }
    };

    public void customResolver() {
        CUrl curl = new CUrl("http://httpbin.org/html");
        Document html = curl.exec(htmlResolver, null);
        assertEquals(200, curl.getHttpCode());
        assertEquals("Herman Melville - Moby-Dick", html.select("h1:first-child").text());
    }
```

##### Example 7: As a command line tool, same request with Example 4
```shell
java -jar java-curl-1.2.2.jar https://httpbin.org/get ^
    -x 127.0.0.1:8888 -k ^
    -A "Mozilla/5.0 (Linux; U; Android 8.0.0; zh-cn; KNT-AL10 Build/HUAWEIKNT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) MQQBrowser/7.3 Chrome/37.0.0.0 Mobile Safari/537.36" ^
    -H "Referer: http://somesite.com/fake_referer" ^
    -H "X-Requested-With: XMLHttpRequest" ^
    -H "X-Auth-Token: xxxxxxx"
```

##### Example 8: Using a self-signed certificate. Uses JKS in stadard JRE, uses BKS in Android
```shell
# Convert website certificate and private key to p12/pfx certificate
openssl pkcs12 -export -in cert.pem -inkey key.pem -name cert -out cert.p12
# Convert p12/pfx to jks format and set the password to 123456
keytool -importkeystore -srckeystore cert.p12 -srcstoretype pkcs12 -srcstorepass 123456 -destkeystore cert.jks -deststorepass 123456
# Convert Jks format to bks format, BKS certificate is applicable to Android platform
keytool -importkeystore -srckeystore cert.jks -srcstoretype JKS -srcstorepass 123456 -destkeystore cert.bks -deststoretype BKS -deststorepass 123456 -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath "path/to/bcprov-jdk16-140.jar"
# Call java-curl on the command line, you must specify the password of the JKS file.
java -jar java-curl-1.2.2.jar https://mysecuritysite.com -E cert.jks:123456
```

# Supported Switches
| Switch Name		| Short-cut Method		| Description |
| --- 				| ---					| --- |
| -E, --cert		| cert					| &lt;certificate:password&gt; Specify the client certificate file and password, only support JKS format certificate (only BKS is supported on Android) |
| --compressed		| NO					| Request to gzip compressed response data (but need server side support) |
| --connect-timeout	| timeout				| Connection timeout time, in seconds, default 0, that is, never timeout |
| -b, --cookie		| cookie				| Read cookies from file / IO object / parameter string |
| -c, --cookie-jar	| cookieJar				| Cookie output to file / IO object |
| -d, --data, --data-ascii | data			| Add post data, if used multiple times, use '&' to connect, the added form item key-value pair will overwrite the previous one. <br/>If data starts with '@', the latter part is used as the file name, and the data is used by File read in, and delete carriage return in the file |
| --data-raw		| NO					| Same as "--data", but not special handling for '@' |
| --data-binary		| NO					| Same as "--data", but does not delete carriage return line feed characters when reading in files |
| --data-urlencode	| data(data,charset)	| Same as "--data", but with Url-Encode for data, you can append a character set after this option, such as "--data-urlencode-GBK".<br/>If the first character of the parameter value is '=': the following string is whole Url-Encode;<br/> If the parameter value contains '=': split the string into key-value pairs separated by '&', the key-value pairs are split with '=', for key-value pairs All values in the value are Url-Encode.<br/>If the parameter value does not contain '=': <br/>-- If the string does not contain '@', then the entire string is Url-Encode<br/>-- If the string contains '@' then split the string with '@', after '@' is the input file name, then read the text from the file and perform Url-Encode, the front part of '@' is the key <br/>-- If '@' is the first character, the text read in the file is generally Url-Encode |
| -D, --dump-header	| dumpHeader			| Output the response header of the last step jump to the given file / IO object |
| -F, --form		| form					| Initiate file upload, add a file or form item. <br/>- If the initial value of the parameter value is '@' or '<', the data is read from the specified file for uploading. The difference between '@' and '<' is that the file content of '@' is uploaded as a file attachment, and the file content of '<' is used as the value of the normal form item. <br/>- Otherwise, the parameter value is used as the value of the normal form item. |
| --form-string		| form(formString)		| Initiate file upload, add 1 non-file form item, note that this method does not specialize for '@' |
| -G, --get			| NO					| Force the GET method. Will add the key-value pair specified by -d to the url as the query parameter |
| -H, --header		| header				| Add a request header line with the syntax:<br/>-- "Host: baidu.com": Add/set a normal request header key-value pair<br/>-- "Accept:": Delete the given request header <br/>-- "X-Custom-Header;": Add/set a custom request header with a value of null |
| -I, --head		| NO					| Request using the HEAD method |
| -k, --insecure	| insecure				| Ignore HTTPS certificate security check |
| -L, --location	| location				| Automatic follow redirect (not enabled by default) |
| -m, --max-time	| timeout				| Transmission timeout, in seconds, default 0, that is, never timeout |
| -o, --output		| output				| Specify the output file / IO object, the default stdout, which is "-" |
| -x, --proxy		| proxy					| Set proxy server |
| -U, --proxy-user	| NO					| Set proxy server authorization |
| -e, --referer		| NO					| Set the Referer request header content |
| --retry			| retry					| Set the number of retries, default 0 |
| --retry-delay		| retry					| Set the delay between two retries, in seconds, default 0 |
| --retry-max-time	| retry					| Set the maximum retry total time, in seconds, default 0, that is, never time out |
| -s, --silent		| NO					| Set silent mode, which suppress all outputs |
| --stderr			| stderr				| Set stderr output file / IO object, default stdout |
| -u, --user		| NO					| Set the HTTP Authorization information. Note that it is only used for simple HTTP authentication, which is the case where the system dialog box pops up in the browser. |
| --url				| CUrl, url				| Set the request address, this CUrl library does not support multiple url requests. |
| -A, --user-agent	| NO					| Set the "User-Agent" request header content |
| -X, --request		| NO					| Specify HTTP request method |
| --x-max-download	| NO					| Abandon download after the transfer reaches a given number of bytes (inaccurate) |
| --x-tags			| NO					| Set additional key-value pairs to be stored in the current CUrl instance for passing additional parameters in programming |
