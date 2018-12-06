[![Licence](https://img.shields.io/badge/licence-Apache%20Licence%20%282.0%29-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.rockswang/java-curl.svg)](https://mvnrepository.com/artifact/com.github.rockswang/java-curl)

[English Version](README.md)

# 简介
CUrl类是以Linux下常用命令行工具CUrl为参考，基于标准Java运行库中的HttpURLConnection实现的Http工具类。

# 特点
* 基于标准Java运行库的Http类实现，源码兼容级别为1.6，适用性广泛，可用于服务端、Android等Java环境
* 代码精简紧凑，仅一个1000余行的Java源文件，无任何外部依赖，可不用Maven直接源码级重用
* 简单易用，完全兼容CUrl命令行工具的常用开关，可直接作为命令行工具替代之
* 支持所有HTTP Method，支持multipart多文件上传；支持简单HTTP认证
* 通过ThreadLocal解决了标准Java中Cookie只能全局保存的问题，可每线程独立维护Cookie
* 可将线程中保存的Cookies序列化保存，方便建立Cookies池
* 支持HTTPS，支持自签名证书（JKS/BKS）；亦可忽略证书安全检查
* 支持每连接代理，支持需认证的HTTP/HTTPS代理
* 跳转行为可控制，可获取到每步跳转的应答头信息
* 支持编程自定义应答解析器，结合Jackson/Gson/Jsoup等库即可解析JSON/HTML等格式的应答
* 支持失败重试，可编程自定义可重试异常

# 说明

#### 关于参数和快捷方法
* 所有参数均可以通过`CUrl.opt(...)`方法传入，具体支持的参数列表请参见文末表格
* 部分常用参数提供了快捷方法，具体请见文末表格和源码
* `opt`方法接受多个参数和值。注意，如果一个CUrl参数需要提供值，那么应该分成两个方法参数传入，比如：
  * `curl.opt("-d", "a=b", "-L")`
  * 上面例子中提供了两个命令行参数，即post数据"a=b"，以及自动跟随重定向

#### 关于`CUrl.IO`及其子类
* Linux中的CURL是个命令行工具，只能读取、输出物理文件，但作为编程库的java-curl，支持以字节数组或输入输出流对象作为读取和写入的目标
* `CUrl.IO`即抽象出来的输入输出接口，其子类包括：
  * `CUrl.MemIO`：对应于ByteArrayInputStream/ByteArrayOutputStream，用于直接内存读取或写入
  * `CUrl.FileIO`: 对应物理文件
  * `CUrl.WrappedIO`: 流对象的简单包装，要么只能作为输入，要么只能作为输出
* CUrl的多个方法都可以使用IO作为参数，包括：
  * `cert(io, password)`: 从IO读取客户端证书
  * `data(io, binary)`: 从IO中读取POST数据
  * `form(name, io)`: 从IO中读取，添加一个文件上传表单项
  * `cookie(io`): 从IO中读取Cookies
  * `cookieJar(io)`: 把Cookies保存到IO
  * `dumpHeader(io)`: 将应答头倾印到IO
  * `stdout/stderr`: 重定向标准输出/标准错误输出到IO
* 注意，所有输出类参数，均可以用"-"代表stdout，比如
  * `curl("http://...").opt("-D", "-", "-c", "-")`
  * 上例发起请求并把应答头、网站Cookie均输出到stdout

#### 关于Cookies
* 基于标准Java处理cookies有两种方案，一种是自行处理Cookie请求头和Set-Cookie应答头，Jsoup即使用此方案，但有一些问题，包括：
  * Set-Cookie中除了键值对外，还有domain, path, expire, httpOnly等属性，有可能出现同名的Cookie，Jsoup用Map简单处理，有时会有问题
  * 据实际测试，部分版本JRE有丢失Set-Cookie值的BUG
* 第二种方案即使用Java自己的CookieManager/CookieStore，但有一个严重问题，此处API设计不合理，CookieStore只能有一个全局默认单例，也就是说，一个JVM进程中如果多个请求并发访问同一站点，那么它们是共用同一份Cookie的，这在很多情况下并不适用！
* CUrl类实现了一个基于ThreadLocal的CookieStore，每条线程有独立的cookie，完美解决了上述问题
* 除了`--cookie/--cookie-jar`参数外，还可以使用getCookieStore获取到CookieStore单例，直接调用其`add/getCookies`等方法读写当前线程的cookies
* 注意1：本类为了方便使用，和CURL工具略有区别，同一线程的多次请求不会自动清除cookie存储。因此，对同一网站的不同url，不必每次添加`--cookie/--cookie-jar`参数
* 注意2：如果使用线程池，由于池中线程会被重用，为了避免Cookie污染，请在线程中第一次请求上添加`--cookie("")`调用，这会清除本线程cookie存储

#### 关于`CUrl.Resolver`及其子类
* `CUrl.Resolver`用于直接将原始应答字节数组反序列化为自定义Java对象，比如Xml, Json, Html等，可以结合JDOM, Jackson/Gson, Jsoup等第三方库使用
* 在`Resolver.resolve`的实现方法中，如果抛出`CUrl.Recoverable`或其子类实例，则表示此错误可重试，如果指定了重试参数，则CUrl会自动重试给定次数或给定时间
  * 举例：服务端API返回200的正常应答，但业务级错误为“请稍候重试”，此时即使请求本身是成功的，仍然可以抛出一个`Recoverable`异常指示CUrl重试

#### 关于HTTPS
* 对于有合法认证机构签发的有效证书的站点，可以直接访问
* 可以使用cert(io, password)或opt("-E", "path/to/file:password")指定自签名证书 (since 1.2.2)
* 也可使用insecure()/opt("-k")指示CUrl忽略证书安全检查
* 不支持指定CA证书，如使用抓包工具拦截HTTPS请求，请忽略证书安全检查
* 可以用openssl, keytool在PEM/P12/JKS/BKS等格式证书间相互转换，请参见例8

#### 关于重定向
* 默认不自动跟随重定向，请使用`location()/opt("-L")`指示自动跟随重定向
* 跟CURL工具一样，只支持30X重定向，不支持refresh头部，页面meta等重定向方式
* 如果不跟随重定向，可以在获取到30X应答后，使用`CUrl.getLocations().get(0)`获取到重定向的目标URL

# 例子

##### 例1：POST表单提交。两次data调用演示可多次指定`--data`命令行参数，且参数值可覆盖
```java
    public void httpPost() {
        CUrl curl = new CUrl("http://httpbin.org/post")
                .data("hello=world&foo=bar")
                .data("foo=overwrite");
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }
```

##### 例2：通过Fiddler代理（抓包工具）访问HTTPS站点
```java
    public void insecureHttpsViaFiddler() {
        CUrl curl = new CUrl("https://httpbin.org/get")
                .proxy("127.0.0.1", 8888) // Use Fiddler to capture & parse HTTPS traffic
                .insecure();  // Ignore certificate check since it's issued by Fiddler
        curl.exec();
        assertEquals(200, curl.getHttpCode());
    }
```

##### 例3：上传多个文件，一个内存文件，一个物理文件
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

##### 例4：模拟手机浏览器上的AJAX请求，添加自定义请求头。可用`header()`指定单一请求头，也可用`headers()`一次指定多个请求头
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

##### 例5：多线程并发请求，线程间Cookies相互独立
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

##### 例6：编程自定义应答解析器，使用Jsoup解析HTML
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

##### 例7：作为命令行工具使用，请求内容参考例4
```shell
java -jar java-curl-1.2.2.jar https://httpbin.org/get ^
    -x 127.0.0.1:8888 -k ^
    -A "Mozilla/5.0 (Linux; U; Android 8.0.0; zh-cn; KNT-AL10 Build/HUAWEIKNT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) MQQBrowser/7.3 Chrome/37.0.0.0 Mobile Safari/537.36" ^
    -H "Referer: http://somesite.com/fake_referer" ^
    -H "X-Requested-With: XMLHttpRequest" ^
    -H "X-Auth-Token: xxxxxxx"
```

##### 例8：使用自签名证书。java平台用JKS格式证书，android平台需使用BKS格式证书
```shell
# 网站证书和私钥转换成p12/pfx证书
openssl pkcs12 -export -in cert.pem -inkey key.pem -name cert -out cert.p12
# p12/pfx转成jks格式，并设定密码为123456
keytool -importkeystore -srckeystore cert.p12 -srcstoretype pkcs12 -srcstorepass 123456 -destkeystore cert.jks -deststorepass 123456
# jks格式转成bks格式，BKS证书适用于Android平台
keytool -importkeystore -srckeystore cert.jks -srcstoretype JKS -srcstorepass 123456 -destkeystore cert.bks -deststoretype BKS -deststorepass 123456 -provider org.bouncycastle.jce.provider.BouncyCastleProvider -providerpath "path/to/bcprov-jdk16-140.jar"
# 命令行调用java-curl，必须指定JKS文件的密码
java -jar java-curl-1.2.2.jar https://mysecuritysite.com -E cert.jks:123456
```

# 支持的参数
| 参数名 			| 快捷方法				| 说明 |
| ----------------- | --------------------- | ---- |
| -E, --cert		| cert					| &lt;certificate:password&gt; 指定客户端证书文件和密码，仅支持JKS格式证书(Android上只支持BKS) |
| --compressed		| 无					| 请求以gzip压缩应答数据（但需服务器端支持） |
| --connect-timeout	| timeout				| 连接超时时间，单位秒，默认0，即永不超时 |
| -b, --cookie		| cookie				| 从文件/IO对象/参数字符串中读取Cookie |
| -c, --cookie-jar	| cookieJar				| Cookie输出到文件/IO对象 |
| -d, --data, --data-ascii | data			| 添加post数据，如果多次使用，则使用'&'连接，后添加的表单项键值对会覆盖之前的<br/>如果data以'@'开头，则后面部分作为文件名，数据由该文件读入，且删除文件中的回车换行 |
| --data-raw		| 无					| 同"--data"，但不对'@'特殊处理 |
| --data-binary		| 无					| 同"--data"，但读入文件时不删除回车换行字符 |
| --data-urlencode	| data(data,charset)	| 同"--data"，但对数据进行Url-Encode，可以在此选项后面附加字符集，比如"--data-urlencode-GBK"<br/>如果参数值首字符为'='：对'='后面的字符串整体进行Url-Encode* 如果参数值中包含'='：将字符串拆分为以'&'分割的键值对，键值对用'='分割，对键值对中所有的值进行Url-Encode<br/>如果参数值中不包含'='：<br/>--如果字符串中不包含'@'，则对字符串整体进行Url-Encode<br/>--如果字符串中包含'@'则以'@'分割字符串，'@'后面为输入文件名，则从该文件中读取文本并进行Url-Encode，'@'前面部分为键<br/>--如'@'为第一个字符，则文件中读出的文本整体进行Url-Encode |
| -D, --dump-header	| dumpHeader			| 输出最后一步跳转的应答头到给定的文件/IO对象 |
| -F, --form		| form					| 发起文件上传，添加一个文件或表单项<br/>-如参数值首字母为'@'或'<'则从指定的文件读取数据进行上传。'@'和'<'的区别在于，'@'的文件内容作为文件附件上传，'<'的文件内容作为普通表单项的值<br/>-否则参数值作为普通表单项的值 |
| --form-string		| form(formString)		| 发起文件上传，添加1个非文件表单项，注意此方法不对'@'进行特殊处理 |
| -G, --get			| 无					| 强制使用GET方法。会把-d指定的键值对添加到url后作为查询参数 |
| -H, --header		| header				| 添加一个请求头行，语法为：<br/>-"Host: baidu.com": 添加/设定一行普通请求头键值对<br/>-"Accept:": 删除给定请求头<br/>-"X-Custom-Header;": 添加/设定一个值为空的自定义请求头 |
| -I, --head		| 无					| 使用HEAD方法请求 |
| -k, --insecure	| insecure				| 忽略HTTPS证书安全检查 |
| -L, --location	| location				| 自动跟随跳转（默认不开启） |
| -m, --max-time	| timeout				| 传输超时时间，单位秒，默认0，即永不超时 |
| -o, --output		| output				| 指定输出文件/IO对象，默认stdout，即"-" |
| -x, --proxy		| proxy					| 设定代理服务器 |
| -U, --proxy-user	| 无					| 设定代理服务器登录信息 |
| -e, --referer		| 无					| 设定Referer请求头内容 |
| --retry			| retry					| 设定重试次数，默认0 |
| --retry-delay		| retry					| 设定两次重试之间的延迟，单位秒，默认0 |
| --retry-max-time	| retry					| 设定最长重试总时间，单位秒，默认0，即永不超时 |
| -s, --silent		| 无					| 设定静默模式，即屏蔽所有输出 |
| --stderr			| stderr				| 设定stderr的输出文件/IO对象，默认stdout |
| -u, --user		| 无					| 设定服务器登录信息。注意只用于简单HTTP认证，即浏览器中弹出系统对话框的情况 |
| --url				| CUrl, url				| 设定请求地址，本CUrl库不支持多url请求 |
| -A, --user-agent	| 无					| 设定"User-Agent"请求头内容 |
| -X, --request		| 无					| 指定HTTP请求方法 |
| --x-max-download	| 无					| 传输达到给定字节数（非精确）后放弃下载 |
| --x-tags			| 无					| 设定额外的键值对信息，存储在当前CUrl实例中，用于在编程中传递额外参数 |
