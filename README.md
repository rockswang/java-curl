[![Licence](https://img.shields.io/badge/licence-Apache%20Licence%20%282.0%29-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.rockswang/java-curl.svg)](https://mvnrepository.com/artifact/com.github.rockswang/java-curl)

# 简介
CUrl类是以命令行工具CUrl为参考，使用标准Java的HttpURLConnection实现的Http工具类。

# 特点
* 基于标准Java运行库的Http类实现，源码兼容级别为1.6，适用性广泛，可用于服务端、Android等Java环境
* 代码精简紧凑，仅一个不到2000行的Java源文件，无任何外部依赖，可不用Maven直接源码级重用
* 支持CUrl命令行工具的常用开关，可直接作为命令行工具替代之
* 支持所有HTTP Method，支持多文件上传
* 通过ThreadLocal解决了标准Java中Cookie只能全局保存的问题，可每线程独立维护Cookie
* 支持HTTP认证，支持HTTPS，可忽略证书安全
* 支持每连接代理，支持需认证的HTTP/HTTPS代理
* 跳转行为可控制，可获取到每步跳转的应答头信息
* 支持编程自定义应答解析器
* 支持失败重试，可编程自定义可重试异常

# 支持的参数
* "--compressed"：请求以gzip压缩应答数据（但需服务器端支持）
* "--connect-timeout"及timeout方法：连接超时时间，单位秒，默认0，即永不超时
* "-b", "--cookie"及cookie方法：从文件/IO对象/参数字符串中读取Cookie
* "-c", "--cookie-jar"及cookieJar方法：Cookie输出到文件/IO对象
* "-d", "--data", "--data-ascii"及data方法：
     * 添加post数据，如果多次使用，则使用'&'连接，后添加的表单项键值对会覆盖之前的；
     * 如果data以'@'开头，则后面部分作为文件名，数据由该文件读入，且删除文件中的回车换行
* "--data-raw"：同"--data"，但不对'@'特殊处理
* "--data-binary"：同"--data"，但读入文件时不删除回车换行字符
* "--data-urlencode"及data(String,String)方法：同"--data"，但对数据进行Url-Encode，可以在此选项后面附加字符集，比如"--data-urlencode-GBK"
     * 如果参数值首字符为'='：对'='后面的字符串整体进行Url-Encode
     * 如果参数值中包含'='：将字符串拆分为以'&'分割的键值对，键值对用'='分割，对键值对中所有的值进行Url-Encode
     * 如果参数值中不包含'='：
        * 如果字符串中不包含'@'，则对字符串整体进行Url-Encode；
        * 如果字符串中包含'@'则以'@'分割字符串，'@'后面为输入文件名，则从该文件中读取文本并进行Url-Encode，'@'前面部分为键；
        * 如'@'为第一个字符，则文件中读出的文本整体进行Url-Encode
* "-D", "--dump-header"及dumpHeader方法：输出应答头到给定的文件/IO对象，可用于获取重定向地址
* "-F", "--form"及form方法：发起post文件上传，添加一个表单项
     * 如参数值首字母为'@'或'<'则从指定的文件读取数据进行上传。'@'和'<'的区别在于，'@'的文件内容作为文件附件上传，'<'的文件内容作为普通表单项的值
     * 否则参数值作为普通表单项的值
* "--form-string"及form(String)方法：发起post文件上传，添加1~N个非文件表单项，注意此方法不对'@'进行特殊处理
* "-G", "--get"：强制使用GET方法
* "-H", "--header"及header方法：添加一个请求头行，语法为：
     *  "Host: baidu.com": 添加/设定一行普通请求头键值对
     *  "Accept:": 删除给定请求头
     *  "X-Custom-Header;": 添加/设定一个值为空的自定义请求头
* "-I", "--head"：使用HEAD方法请求
* "--ignore-content-length"：在POST请求头中不包含Content-Length头信息
* "-k", "--insecure": 忽略HTTPS证书安全检查
* "-L", "--location"及location方法：自动跟随跳转（默认不开启）
* "-m", "--max-time"及timeout方法：传输超时时间，单位秒，默认0，即永不超时
* "--no-keepalive"：不发出"Connection: keep-alive"请求头
* "-o", "--output"及output方法：指定输出文件/IO对象
* "-x", "--proxy"及proxy方法：设定代理服务器
* "-U", "--proxy-user"：设定代理服务器登录信息
* "-e", "--referer"：设定Referer请求头内容
* "--retry"及retry方法：设定重试次数，默认0
* "--retry-delay"及retry方法：设定两次重试之间的延迟，单位秒，默认0
* "--retry-max-time"及retry方法：设定最长重试总时间，单位秒，默认0，即永不超时
* "-s", "--silent"：设定静默模式，即屏蔽所有输出
* "--stderr"及stderr方法：设定stderr的输出文件/IO对象，默认输出到标准输出上，即exec方法的返回值
* "-u", "--user"：设定服务器登录信息
* "--url"及url方法：设定请求地址，本CUrl库不支持多url请求
* "-A", "--user-agent"：设定"User-Agent"请求头内容
* "-X", "--request"：指定HTTP请求方法
* "--x-max-download"：传输达到给定字节数（非精确）后放弃下载
* "--x-tags"：设定额外的键值对信息，存储在当前CUrl实例中，用于在编程中传递额外参数

# 例子

## 通过Fiddler代理（抓包工具）访问HTTPS站点
```java
new CUrl("https://www.baidu.com/")
    .proxy("127.0.0.1", 8888)
    .opt("-k")
    .exec();
```
