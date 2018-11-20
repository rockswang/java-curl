[![Licence](https://img.shields.io/badge/licence-Apache%20Licence%20%282.0%29-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.rockswang/java-curl.svg)](https://mvnrepository.com/artifact/com.github.rockswang/java-curl)

# 简介
CUrl类是以命令行工具CUrl为参考，使用标准Java的HttpURLConnection实现的Http工具类。

# 特点
* 基于标准Java运行库的Http类实现，源码兼容级别为1.6，适用性广泛，可用于服务端、Android等Java环境
* 代码精简紧凑，仅一个1000余行的Java源文件，无任何外部依赖，可不用Maven直接源码级重用
* 完全兼容CUrl命令行工具的常用开关，可直接作为命令行工具替代之
* 支持所有HTTP Method，支持多文件上传
* 通过ThreadLocal解决了标准Java中Cookie只能全局保存的问题，可每线程独立维护Cookie
* 可将线程中保存的Cookies序列化保存，方便建立Cookies池
* 支持HTTP认证，支持HTTPS，可启用/忽略证书安全
* 支持每连接代理，支持需认证的HTTP/HTTPS代理
* 跳转行为可控制，可获取到每步跳转的应答头信息
* 支持编程自定义应答解析器
* 支持失败重试，可编程自定义可重试异常

# 支持的参数
| 参数名 | 快捷方法 | 说明 |
| --- | --- | --- |
| --compressed | 无 | 请求以gzip压缩应答数据（但需服务器端支持）|
| --connect-timeout| timeout |连接超时时间，单位秒，默认0，即永不超时|
| -b, --cookie| cookie |从文件/IO对象/参数字符串中读取Cookie|
| -c, --cookie-jar| cookieJar |Cookie输出到文件/IO对象|
| -d, --data, --data-ascii| data |添加post数据，如果多次使用，则使用'&'连接，后添加的表单项键值对会覆盖之前的<br/>如果data以'@'开头，则后面部分作为文件名，数据由该文件读入，且删除文件中的回车换行|
| --data-raw | 无 | 同"--data"，但不对'@'特殊处理|
| --data-binary | 无 | 同"--data"，但读入文件时不删除回车换行字符|
| --data-urlencode| data(data,charset) |同"--data"，但对数据进行Url-Encode，可以在此选项后面附加字符集，比如"--data-urlencode-GBK"<br/>如果参数值首字符为'='：对'='后面的字符串整体进行Url-Encode* 如果参数值中包含'='：将字符串拆分为以'&'分割的键值对，键值对用'='分割，对键值对中所有的值进行Url-Encode<br/>如果参数值中不包含'='：<br/>--如果字符串中不包含'@'，则对字符串整体进行Url-Encode<br/>--如果字符串中包含'@'则以'@'分割字符串，'@'后面为输入文件名，则从该文件中读取文本并进行Url-Encode，'@'前面部分为键<br/>--如'@'为第一个字符，则文件中读出的文本整体进行Url-Encode|
| -D, --dump-header|dumpHeader|输出最后一步跳转的应答头到给定的文件/IO对象|
| -F, --form| form | 发起文件上传，添加一个文件或表单项<br/>-如参数值首字母为'@'或'<'则从指定的文件读取数据进行上传。'@'和'<'的区别在于，'@'的文件内容作为文件附件上传，'<'的文件内容作为普通表单项的值<br/>-否则参数值作为普通表单项的值|
| --form-string| form(formString) |发起文件上传，添加1个非文件表单项，注意此方法不对'@'进行特殊处理|
| -G, --get | 无 | 强制使用GET方法|
| -H, --header| header |添加一个请求头行，语法为：<br/>-"Host: baidu.com": 添加/设定一行普通请求头键值对<br/>-"Accept:": 删除给定请求头<br/>-"X-Custom-Header;": 添加/设定一个值为空的自定义请求头|
| -I, --head | 无 | 使用HEAD方法请求|
| -k, --insecure | insecure | 忽略HTTPS证书安全检查|
| -L, --location| location |自动跟随跳转（默认不开启）|
| -m, --max-time| timeout |传输超时时间，单位秒，默认0，即永不超时|
| -o, --output| output |指定输出文件/IO对象，默认stdout，即"-"|
| -x, --proxy| proxy |设定代理服务器|
| -U, --proxy-user | 无 | 设定代理服务器登录信息|
| -e, --referer | 无 | 设定Referer请求头内容|
| --retry| retry |设定重试次数，默认0|
| --retry-delay| retry |设定两次重试之间的延迟，单位秒，默认0|
| --retry-max-time| retry |设定最长重试总时间，单位秒，默认0，即永不超时|
| -s, --silent | 无 | 设定静默模式，即屏蔽所有输出|
| --stderr| stderr |设定stderr的输出文件/IO对象，默认stdout|
| -u, --user | 无 | 设定服务器登录信息|
| --url| CUrl, url |设定请求地址，本CUrl库不支持多url请求|
| -A, --user-agent | 无 | 设定"User-Agent"请求头内容|
| -X, --request | 无 | 指定HTTP请求方法|
| --x-max-download | 无 | 传输达到给定字节数（非精确）后放弃下载|
| --x-tags | 无 | 设定额外的键值对信息，存储在当前CUrl实例中，用于在编程中传递额外参数|

# 例子

##### 例1：POST表单提交
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

##### 例4：模拟手机浏览器上的AJAX请求，添加自定义请求头
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

##### 例6：编程自定义应答解析器，使用JSoup解析HTML
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
java -jar java-curl-1.2.0.jar https://httpbin.org/get ^
    -x 127.0.0.1:8888 -k ^
    -A "Mozilla/5.0 (Linux; U; Android 8.0.0; zh-cn; KNT-AL10 Build/HUAWEIKNT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) MQQBrowser/7.3 Chrome/37.0.0.0 Mobile Safari/537.36" ^
    -H "Referer: http://somesite.com/fake_referer" ^
    -H "X-Requested-With: XMLHttpRequest" ^
    -H "X-Auth-Token: xxxxxxx"
```
