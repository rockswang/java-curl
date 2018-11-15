package com.roxstudio.utils;

import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.net.Proxy;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Note:
 * * In order to set Restricted Headers i.e. "Origin" etc., you may need to add "-Dsun.net.http.allowRestrictedHeaders=true" in JVM argument
 * * To use HTTPS Proxy Authorization, due to the HTTPS tunnel BASIC authentication has been disabled by default since JDK8u111, you may need to add "-Djdk.http.auth.tunneling.disabledSchemes=" in JVM argument
 * * To add JVM arguments In TOMCAT, modify catalina.bat/catalina.sh
 */
@SuppressWarnings({"rawtypes", "unchecked", "serial", "UnusedReturnValue", "WeakerAccess", "unused", "JavaDoc"})
public final class CUrl {
	
	private static final String DEFAULT_USER_AGENT = "Java-CURL version 1.2.0 by Rocks Wang <rockswang@foxmail.com>";
	private static final Pattern ptnOptionName = Pattern.compile("-{1,2}[a-zA-Z][a-zA-Z0-9\\-.]*");
	private static final CookieStore cookieStore = new CookieStore();
	private static HostnameVerifier insecureVerifier = null;
	private static SSLSocketFactory insecureFactory = null;
	
	static {
		try {
			// Try to enable the setting to restricted headers like "Origin", this is expected to be executed before HttpURLConnection class-loading
			System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

			// Modify the system-wide Cookie manager to ThreadLocal-based instance
			CookieManager.setDefault(new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL));

			// For insecure HTTPS
			insecureVerifier = new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) { return true; }
			};
			TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() { return null; }
				public void checkClientTrusted(X509Certificate[] arg0, String arg1) {}
				public void checkServerTrusted(X509Certificate[] arg0, String arg1) {}
			}};
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAll, new SecureRandom());
			insecureFactory = sc.getSocketFactory();
		} catch (Exception ignored) {}
	}
	
	private static final Map<String, Integer> optMap = Util.mapPut(new HashMap<String, Integer>(),
			"--compressed", 1, 				// Request compressed response (using deflate or gzip)
			"--connect-timeout", 2, 		// SECONDS  Maximum time allowed for connection
			"-b", 3, 
			"--cookie", 3, 					// STRING/FILE  Read cookies from STRING/FILE (H)
			"-c", 4,
			"--cookie-jar", 4, 				// FILE  Write cookies to FILE after operation (H)
			"-d", 5,
			"--data", 5, 					// DATA	 HTTP POST data (H)
			"--data-ascii", 5, 				// DATA	 HTTP POST ASCII data (H)
			"--data-raw", 51, 				// DATA	 HTTP POST raw data (H)
			"--data-binary", 52, 			// DATA	 HTTP POST binary data (H)
			"--data-urlencode", 53, 		// DATA	 HTTP POST data url encoded (H)
			"-D", 6,
			"--dump-header", 6, 			// FILE  Write the headers to FILE
			"-F", 7,
			"--form", 7, 					// CONTENT  Specify HTTP multipart POST data (H)
			"--form-string", 71,			// STRING  Specify HTTP multipart POST data (H)
			"-G", 8,
			"--get", 8, 					// Send the -d data with a HTTP GET (H)
			"-H", 10,
			"--header", 10, 				// LINE   Pass custom header LINE to server (H)
			"-I", 11,
			"--head", 11, 					// Show document info only
			"--ignore-content-length", 12, // Ignore the HTTP Content-Length header
			"-k", 31,
			"--insecure", 31,				// Allow insecure server connections when using SSL
			"-L", 13,
			"--location", 13, 				// Follow redirects (H)
			"-m", 14,
			"--max-time", 14, 				// SECONDS  Maximum time allowed for the transfer
			"--no-keepalive", 15, 			// Disable keepalive use on the connection
			"-o", 16,
			"--output", 16, 				// FILE   Write to FILE instead of stdout
			"-x", 17,
			"--proxy", 17, 					// [PROTOCOL://]HOST[:PORT]  Use proxy on given port
			"-U", 18,
			"--proxy-user", 18, 			// USER[:PASSWORD]  Proxy user and password
			"-e", 19,
			"--referer", 19, 				// Referer URL (H)
			"--retry", 20, 					// NUM   Retry request NUM times if transient problems occur
			"--retry-delay", 21, 			// SECONDS  Wait SECONDS between retries
			"--retry-max-time", 22, 		// SECONDS  Retry only within this period
			"-s", 23,
			"--silent", 23, 				// Silent mode (don't output anything)
			"--stderr", 24, 				// FILE   Where to redirect stderr (use "-" for stdout)
			"-u", 28,
			"--user", 28, 					// USER[:PASSWORD]  Server user and password
			"--url", 25, 					// URL	   URL to work with
			"-A", 26,
			"--user-agent", 26, 			// STRING  Send User-Agent STRING to server (H)
			"-X", 27, 
			"--request", 27,				// COMMAND  Specify request command to use
			"--x-max-download", 29,			// BYTES Maximum bytes allowed for the download
			"--x-tags", 30,					// DATA extra key-value pairs, storage only
			"", 0 // placeholder
	);
	
	private static final String BOUNDARY = "------------aia113jBkadk7289";
	private static final byte[] NEWLINE = "\r\n".getBytes();

	private final List<String> options = new ArrayList<String>();
	private final Map<String, IO> iomap = new HashMap<String, IO>();
	private final Map<String, String> tags = new LinkedHashMap<String, String>();
	private final Map<String, String> headers = new LinkedHashMap<String, String>();
	private final List<List<String[]>> responseHeaders = new ArrayList<List<String[]>>(4);
	private final List<URL> locations = new ArrayList<URL>(4);
	private long startTime;
	private long execTime;
	private int httpCode;
	private byte[] rawStdout;
	
	public CUrl() {}
	
	public CUrl(String url) {
		this.url(url);
	}
	
	/**
	 * Specify 0~N options, please refer to https://curl.haxx.se/docs/manpage.html
	 * Note: the option name and corresponding value must be divided into two arguments, rather than one single string seperated by space
	 * @param options e.g. opt("-H", "X-Requested-With: XMLHttpRequest")
	 */
	public final CUrl opt(String... options) {
		for (String o: options) {
			if (o.startsWith("'") && o.endsWith("'")) o = o.substring(1, o.length() - 1);
			this.options.add(o);
		}
		return this;
	}
	
	public final CUrl url(String url) {
		return opt("--url", url);
	}
	
	/**
	 * Follow redirection automatically, false be default.
	 * Only apply to HTTP CODE 30x
	 */
	public final CUrl location() {
		return opt("-L");
	}
	
	/**
	 * Specify the proxy server
	 */
	public final CUrl proxy(String host, int port) {
		return opt("-x", host + ":" + port);
	}
	
	/**
	 * Specify retry related options, default values are 0
	 * @param retry Retry times
	 * @param retryDelay The interval between two retries (in second)
	 * @param retryMaxTime The max retry time in second, 0 means infinite
	 */
	public final CUrl retry(int retry, float retryDelay, float retryMaxTime) {
		return opt("--retry", Integer.toString(retry), 
				"--retry-delay", Float.toString(retryDelay),
				"--retry-max-time", Float.toString(retryMaxTime));
	}
	
	/**
	 * Specify timeout, default values are 0
	 * @param connectTimeoutSeconds Connection timeout in second
	 * @param readTimeoutSeconds Read timeout in second
	 */
	public final CUrl timeout(float connectTimeoutSeconds, float readTimeoutSeconds) {
		return opt("--connect-timeout", Float.toString(connectTimeoutSeconds), 
				"--max-time", Float.toString(readTimeoutSeconds));
	}
	
	/**
	 * Add a custom request header
	 * @param headerLine Syntax:
	 *  "Host: baidu.com": add/set a request header-value pair
	 *  "Accept:": delete a previously added request header
	 *  "X-Custom-Header;": add/set a request header with empty value
	 */
	public final CUrl header(String headerLine) {
		return opt("-H", headerLine);
	}
	
	public final CUrl headers(Map<String, ?> headers) {
		for (Map.Entry<String, ?> kv: headers.entrySet()) {
			Object k = kv.getKey(), v = kv.getValue();
			opt("-H", v == null ? k + ":" : v.toString().length() == 0 ? k + ";" : k + ": " + v);
		}
		return this;
	}
	
	/**
	 * Add post data. The data among multiple calls will be joined with '&amp;'
	 * @param data 如果data以'@'开头，则后面部分作为文件名，数据由该文件读入
	 */
	public final CUrl data(String data) {
		return data(data, false);
	}
	
	/**
	 * Add post data. The data among multiple calls will be joined with '&amp;'
	 * @param data 如果data以'@'开头且raw=false，则后面部分作为文件名，数据由该文件读入
	 * @param raw 如为真则不对'@'做特殊处理
	 */
	public final CUrl data(String data, boolean raw) {
		return opt(raw ? "--data-raw" : "-d", data);
	}
	
	/**
	 * 从input中读取数据作为post数据
	 * Read data from input and use as post data
	 * @param input
	 * @param binary 如为真则读取数据中的回车换行符会保留，否则会被删除
	 */
	public final CUrl data(IO input, boolean binary) {
		String key;
		iomap.put(key = "IO#" + iomap.size(), input);
		return opt(binary ? "--data-binary" : "-d", "@" + key);
	}
	
	/**
	 * 添加urlencode的post数据
	 * Add URL-encoded post data
	 * @param data, 语法如下/syntax:
	 *  "content": 如content中不包含'=', '@'，则直接把整个content作为数据整体进行urlencode
	 *  "=content": '='后面的content整体进行urlencode，不处理特殊字符，第一个'='不包含在数据内容中
	 *  "name1=value1[&amp;name2=value2...]": 按照'&amp;'拆分后，对每个值进行urlencode，注意键不进行处理
	 *  "@filename": '@'后面的部分作为文件名，从文件中读取内容并进行urlencode，回车换行保留
	 *  "name@filename": 读取'@'后面的文件内容作为值进行urlencode，并以name为键
	 * @param charset urlencode使用的字符集，如为null则默认使用"UTF-8"
	 */
	public final CUrl data(String data, String charset) {
		return opt("--data-urlencode" + (charset != null ? "-" + charset : ""), data);
	}
	
	/**
	 * 发起post文件上传，添加一个表单项
	 * Issue a file-upload post and add a form item
	 * @param name 表单项名
	 * @param content 如首字母为'@'或'&lt;'则从指定的文件读取数据进行上传。
	 *  '@'和'&lt;'的区别在于，'@'的文件内容作为文件附件上传，'&lt;'的文件内容作为普通表单项
	 */
	public final CUrl form(String name, String content) {
		return opt("-F", name + "=" + content);
	}
	
	/**
	 * 发起post文件上传，添加一个文件上传的表单项
	 * Issue a file-upload post and add a file item
	 * @param name 表单项名
	 * @param input 上传的数据IO
	 */
	public final CUrl form(String name, IO input) {
		String key;
		iomap.put(key = "IO#" + iomap.size(), input);
		return opt("-F", name + "=@" + key);
	}
	
	/**
	 * 发起post文件上传，添加1~N个非文件表单项，注意此方法不对'@'进行特殊处理
	 * @param formString 语法为"name1=value1[&amp;name2=value2...]"
	 */
	public final CUrl form(String formString) {
		return opt("--form-string", formString);
	}
	
	/** 
	 * 输出Cookie到给定的文件
	 * Output Cookie to given file path
	 * @param output 文件路径，使用'-'表示输出到标准输出。默认不输出
	 */
	public final CUrl cookieJar(String output) {
		return opt("-c", output);
	}
	
	/** 
	 * 输出Cookie到给定的数据IO
	 * Output Cookie to given IO object
	 * @param output 数据IO，注意cookieJar的输出会清除output中的原有内容
	 */
	public final CUrl cookieJar(IO output) {
		String key;
		iomap.put(key = "IO#" + iomap.size(), output);
		return opt("-c", key);
	}
	
	/** 
	 * 添加请求Cookie
	 * Add custom Cookies in request
	 * @param input 格式为"NAME1=VALUE1; NAME2=VALUE2"的Cookie键值对。
	 *  如字串中不包含'='则作为输入文件名；
	 *  如传入空字符串则仅清空当前线程的Cookie
	 */
	public final CUrl cookie(String input) {
		return opt("-b", input);
	}
	
	/**
	 * 读取数据IO并添加请求Cookie。
	 * 注意CUrl会自动为同一线程内的多次请求维持Cookie
	 * @param input
	 * @return
	 */
	public final CUrl cookie(IO input) {
		String key;
		iomap.put(key = "IO#" + iomap.size(), input);
		return opt("-b", key);
	}
	
	/** 
	 * 倾印原始响应头到给定的文件
	 * Dump raw response headers to specified file path
	 * @param output 输出文件的路径，使用'-'表示输出到标准输出。默认不输出。 
	 */
	public final CUrl dumpHeader(String output) {
		return opt("-D", output);
	}
	
	/** 
	 * 倾印原始响应头到给定的数据IO
	 * @param output  
	 */
	public final CUrl dumpHeader(IO output) {
		String key;
		iomap.put(key = "IO#" + iomap.size(), output);
		return opt("-D", key);
	}
	
	/** 
	 * 重定向标准错误输出到给定的文件
	 * Redirect stderr to specified file path, use '-' for stdout
	 * @param output 输出文件路径。使用'-'表示输出到标准输出。默认输出到标准输出。
	 */
	public final CUrl stderr(String output) {
		return opt("--stderr", output); // output can be an OutputStream/File/path_to_file
	}
	
	/** 
	 * 重定向标准错误输出到给定的数据IO
	 * @param output
	 */
	public final CUrl stderr(IO output) {
		String key;
		iomap.put(key = "IO#" + iomap.size(), output);
		return opt("--stderr", key);
	}
	
	/** 
	 * 输出应答数据到给定文件。注意标准输出默认即为exec方法的返回值。
	 * Output response data to specified fila path
	 * @param output 输出文件路径。使用'-'表示输出到标准输出。默认输出到标准输出。
	 */
	public final CUrl output(String output) {
		return opt("-o", output); // output can be an OutputStream/File/path_to_file
	}
	
	/** 
	 * 输出应答数据到给定数据IO
	 * @param output
	 */
	public final CUrl output(IO output) {
		String key;
		iomap.put(key = "IO#" + iomap.size(), output);
		return opt("-o", key);
	}
	
	/**
	 * 添加一个数据IO，可作为数据输入或数据输出，在--data等参数值中引用
	 * @param key
	 * @param io
	 * @return
	 */
	public final CUrl io(String key, IO io) {
		iomap.put(key, io);
		return this;
	}

	public static CookieStore getCookieStore() {
		return cookieStore;
	}

	public static void saveCookies(IO output) {
		StringBuilder sb = new StringBuilder();
		for (HttpCookie cookie: cookieStore.getCookies()) {
			long expire = cookie.getMaxAge() <= 0 || cookie.getMaxAge() >= Integer.MAX_VALUE ?
					Integer.MAX_VALUE : cookie.getMaxAge() + System.currentTimeMillis() / 1000L;
			sb.append(cookie.getDomain()).append('\t')
					.append("FALSE").append('\t')
					.append(cookie.getPath()).append('\t')
					.append(cookie.getSecure() ? "TRUE" : "FALSE").append('\t')
					.append(expire).append('\t')
					.append(cookie.getName()).append('\t')
					.append(cookie.getValue()).append('\n');
		}
		writeOutput(output, Util.s2b(sb.toString(), null), false);
	}

	public static void loadCookies(IO input) {
		String s = Util.b2s(readInput(input), null, null);
		BufferedReader br = new BufferedReader(new StringReader(s));
		try {
			for (String line = br.readLine(), l[]; line != null; line = br.readLine()) {
				if (line.trim().length() == 0 || line.startsWith("# ") || (l = line.split("\t")).length < 7) continue;
				HttpCookie cookie = new HttpCookie(l[5], l[6]);
				cookie.setDomain(l[0]);
				cookie.setPath(l[2]);
				cookie.setSecure("TRUE".equals(l[3]));
				long expire = Long.parseLong(l[4]);
				cookie.setMaxAge(expire >= Integer.MAX_VALUE ? Integer.MAX_VALUE : expire * 1000L - System.currentTimeMillis());
				if (!cookie.hasExpired()) cookieStore.add(null, cookie);
			}
		} catch (Exception ignored) { } // should not happen
	}

	/**
	 * 打印输出完整CUrl命令行以及IO映射表。
	 */
	public final String toString() {
		StringBuilder sb = new StringBuilder("curl");
		for (String s: options) {
			sb.append(' ').append(ptnOptionName.matcher(s).matches() ? s : '"' + s + '"');
		}
		sb.append("\r\n> IOMap: ").append(iomap);
		return sb.toString();
	}
	
	public final List<String> getOptions() {
		return options;
	}
	
	public final Map<String, String> getTags() {
		return tags;
	}
	
	public final Map<String, String> getHeaders() {
		return headers;
	}
	
	public final List<List<String[]>> getResponseHeaders() {
		return responseHeaders;
	}
	
	public final long getExecTime() {
		return execTime;
	}
	
	public final int getHttpCode() {
		return httpCode;
	}
	
	public final <T> T getStdout(Resolver<T> resolver, T fallback) {
		try { return resolver.resolve(httpCode, rawStdout); } catch (Throwable ignored) {}
		return fallback;
	}
	
	public final List<URL> getLocations() {
		return locations;
	}
	
	/**
	 * 解析参数，执行请求，并将标准输出以给定的encoding解码为字符串
	 * Parse options and execute the request。Decode the raw response to String with given character-encoding
	 * @param encoding，如传入null则默认使用"UTF-8"
	 * @return 标准输出数据，以encoding编码为字符串
	 */
	public final String exec(String encoding) {
		return exec(encoding != null ? new ToStringResolver(encoding) : UTF8, null);
	}
	
	/**
	 * 解析参数，执行请求，返回原始字节数组
	 * Parse options and execute the request, return raw response.
	 * @return 标准输出数据
	 */
	public final byte[] exec() {
		return exec(RAW, null);
	}
	
	/**
	 * 解析参数并执行请求
	 * 默认仅包含应答数据。指定"--silent"参数则不输出。
	 * @param resolver 输出解析器
	 * @param fallback 默认返回值
	 * @return 将标准输出中的数据使用解析器转换为对象。如失败，则返回fallback
	 */
	public final <T> T exec(Resolver<T> resolver, T fallback) {
		startTime = System.currentTimeMillis();
		tags.clear();
		headers.clear();
		responseHeaders.clear();
		locations.clear();
		execTime = 0;
		httpCode = -1;
		rawStdout = null;
		Proxy proxy = Proxy.NO_PROXY;
		String url = null, redirect = null, method = null, cookie = null, charset = "UTF-8";
		final MemIO stdout = new MemIO();
		IO stderr = stdout, output = stdout, cookieJar = null, dumpHeader = null;
		StringBuilder dataSb = new StringBuilder();
		Map<String, Util.Ref<String>> form = new LinkedHashMap<String, Util.Ref<String>>();
		float connectTimeout = 0, maxTime = 0, retryDelay = 0, retryMaxTime = 0;
		int retry = 0, maxDownload = 0;
		boolean location = false, ignoreContentLength = false, noKeepAlive = false, silent = false, mergeData = false, insecure = false;
		Util.mapPut(headers, "Accept", "*/*", "User-Agent", DEFAULT_USER_AGENT);
		iomap.put("-", stdout);
		Throwable lastEx = null;
		for (int i = 0, n = options.size(); i < n; i++) {
			String opt = options.get(i);
			if (opt.startsWith("http://") || opt.startsWith("https://")) {
				url = opt;
				continue;
			}
			if (opt.startsWith("--data-urlencode-")) {
				charset = opt.substring(17);
				opt = "--data-urlencode";
			}
			switch (Util.mapGet(optMap, opt, -1)) {
			case 1: // --compressed  Request compressed response (using deflate or gzip)
				headers.put("Accept-Encoding", "gzip, deflate");
				break;
			case 2: // --connect-timeout  SECONDS  Maximum time allowed for connection
				connectTimeout = Float.parseFloat(options.get(++i)); 
				break;
			case 3: // --cookie  STRING/FILE  Read cookies from STRING/FILE (H)
				cookie = options.get(++i);
				break;
			case 4: // --cookie-jar  FILE  Write cookies to FILE after operation (H)
				cookieJar = getIO(options.get(++i)); 
				break;
			case 5: // --data  DATA	 HTTP POST data (H)
				String data = options.get(++i);
				if (data.startsWith("@")) data = Util.b2s(readInput(getIO(data.substring(1))), null, null).replaceAll("[\r\n]+", "");
				mergeData = dataSb.length() > 0;
				dataSb.append(mergeData ? "&" : "").append(data);
				break;
			case 51: // --data-raw  DATA	 not handle "@"
				mergeData = dataSb.length() > 0;
				dataSb.append(mergeData ? "&" : "").append(options.get(++i));
				break;
			case 52: // --data-binary  DATA	 not stripping CR/LF
				data = options.get(++i);
				if (data.startsWith("@")) data = Util.b2s(readInput(getIO(data.substring(1))), null, null);
				mergeData = dataSb.length() > 0;
				dataSb.append(mergeData ? "&" : "").append(data);
				break;
			case 53: // --data-urlencode 
				mergeData = dataSb.length() > 0;
				data = options.get(++i);
				int idx, atIdx;
				switch (idx = data.indexOf("=")) {
				case -1: // no '='
					if ((atIdx = data.indexOf("@")) >= 0) { // [name]@filename
						String prefix = atIdx > 0 ? data.substring(0, atIdx) + "=" : "";
						try { 
							data = prefix + URLEncoder.encode(Util.b2s(readInput(getIO(data.substring(atIdx + 1))), null, ""), charset);
						} catch (Exception e) {
							lastEx = e;
						}
						break;
					} // else fall through
				case 0: // =content
					try { 
						data = URLEncoder.encode(data.substring(idx + 1), charset);
					} catch (Exception e) {
						lastEx = e;
					}
					break;
				default: // name=content
					Map<String, String> m = Util.split(data, "&", "=", new LinkedHashMap<String, String>());
					for (Map.Entry<String, String> en: m.entrySet()) {
						try { en.setValue(URLEncoder.encode(en.getValue(), "UTF-8")); } catch (Exception ignored) { }
					}
					data = Util.join(m, "&", "=");
				}
				dataSb.append(mergeData ? "&" : "").append(data);
				break;
			case 6: // --dump-header  FILE  Write the headers to FILE
				dumpHeader = getIO(options.get(++i)); 
				break;
			case 7: // --form  CONTENT  Specify HTTP multipart POST data (H)
				data = options.get(++i);
				idx = data.indexOf('=');
				form.put(data.substring(0, idx), new Util.Ref<String>(1, data.substring(idx + 1)));
				break;
			case 71: // --form-string  STRING  Specify HTTP multipart POST data (H)
				for (String[] pair: Util.split(options.get(++i), "&", "=")) {
					form.put(pair[0], new Util.Ref<String>(pair[1]));
				}
				break;
			case 8: // --get  Send the -d data with a HTTP GET (H)
				method = "GET";
				break;
			case 10: // --header  LINE   Pass custom header LINE to server (H)
				String[] hh = options.get(++i).split(":", 2);
				String name = hh[0].trim();
				if (hh.length == 1 && name.endsWith(";")) { // "X-Custom-Header;"
					headers.put(name.substring(0, name.length() - 1), "");
				} else if (hh.length == 1) { // "Host:"
					headers.remove(name);
				} else { // "Host: baidu.com"
					headers.put(name, hh[1].trim());
				}
				break;
			case 11: // --head  Show document info only
				method = "HEAD";
				break;
			case 12: // --ignore-content-length  Ignore the HTTP Content-Length header
				ignoreContentLength = true;
				break;
			case 13: // --location  Follow redirects (H)
				location = true;
				break;
			case 14: // --max-time  SECONDS  Maximum time allowed for the transfer
				maxTime = Float.parseFloat(options.get(++i)); 
				break;
			case 15: // --no-keepalive  Disable keepalive use on the connection
				noKeepAlive = true;
				break;
			case 16: // --output  FILE   Write to FILE instead of stdout
				output = getIO(options.get(++i)); 
				break;
			case 17: // --proxy  [PROTOCOL://]HOST[:PORT]  Use proxy on given port
				String[] pp = options.get(++i).split(":");
				InetSocketAddress addr = new InetSocketAddress(pp[0], pp.length > 1 ? Integer.parseInt(pp[1]) : 1080);
				proxy = new Proxy(Proxy.Type.HTTP, addr);
				break;
			case 18: // --proxy-user  USER[:PASSWORD]  Proxy user and password
				final String proxyAuth = options.get(++i);
				headers.put("Proxy-Authorization", "Basic " + Util.base64Encode(proxyAuth.getBytes()));
				Authenticator.setDefault(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						String[] up = proxyAuth.split(":");
						return new PasswordAuthentication(up[0], (up.length > 1 ? up[1] : "").toCharArray());
					}
				});
				break;
			case 19: // --referer  Referer URL (H)
				headers.put("Referer", options.get(++i));
				break;
			case 20: // --retry  NUM   Retry request NUM times if transient problems occur
				retry = Integer.parseInt(options.get(++i));
				break;
			case 21: // --retry-delay  SECONDS  Wait SECONDS between retries
				retryDelay = Float.parseFloat(options.get(++i));
				break;
			case 22: // --retry-max-time  SECONDS  Retry only within this period
				retryMaxTime = Float.parseFloat(options.get(++i));
				break;
			case 23: // --silent  Silent mode (don't output anything)
				silent = true;
				break;
			case 24: // --stderr  FILE   Where to redirect stderr (use "-" for stdout)
				stderr = getIO(options.get(++i));
				break;
			case 25: // --url  URL	   URL to work with
				url = options.get(++i);
				break;
			case 26: // --user-agent  STRING  Send User-Agent STRING to server (H)
				headers.put("User-Agent", options.get(++i));
				break;
			case 27: // --request  COMMAND  Specify request command to use
				method = options.get(++i);
				break;
			case 28: // -u, --user USER[:PASSWORD]  Server user and password
				headers.put("Authorization", "Basic " + Util.base64Encode(options.get(++i).getBytes()));
				break;
			case 29: // --x-max-download  BYTES Maximum bytes allowed for the download
				maxDownload = Integer.parseInt(options.get(++i));
				break;
			case 30: // --x-tags  DATA extra key-value pairs, storage only
				Util.split(options.get(++i), "&", "=", tags);
				break;
			case 31: //
				insecure = true;
				break;
			default: lastEx = new IllegalArgumentException("option " + opt + ": is unknown");
			}
			if (lastEx != null) 
				return error(stdout, stderr, lastEx, silent, resolver, fallback);
		}
		if (url == null) {
			lastEx = new IllegalArgumentException("no URL specified!");
			return error(stdout, stderr, lastEx, silent, resolver, fallback);
		}
		if (dataSb.length() > 0 && form.size() > 0 
				|| dataSb.length() > 0 && "HEAD".equals(method) 
				|| form.size() > 0 && "HEAD".equals(method)) {
			lastEx = new IllegalArgumentException("Warning: You can only select one HTTP request!");
			return error(stdout, stderr, lastEx, silent, resolver, fallback);
		}
		String dataStr = "";
		if (form.size() > 0) {
			if (method == null) method = "POST";
		} else if (dataSb.length() > 0) {
			dataStr = !mergeData ? dataSb.toString() 
					: Util.join(Util.split(dataSb.toString(), "&", "=", new LinkedHashMap<String, String>()), "&", "=");
			if (method == null) method = "POST";
		}
		if (method == null) method = "GET";
		if (!noKeepAlive) headers.put("Connection", "keep-alive");
		if (cookie != null) { // --cookie '' will clear the CookieStore
			cookieStore.removeAll();
			if (cookie.indexOf('=') > 0) {
				parseCookies(url, cookie);
			} else if (cookie.trim().length() > 0) {
				loadCookies(getIO(cookie));
			}
		}
		
		boolean needRetry = false;
		if (dataStr.length() > 0 && "GET".equals(method)) url += (url.contains("?") ? "&" : "?") + dataStr;
		URL urlObj = null;
		int retryLeft = retry;
		do {
			try {
				if (redirect != null) {
					urlObj = new URL(urlObj, redirect);
					method = "GET";
				} else {
					urlObj = new URL(url);
				}
				if (retryLeft == retry) { // add at first time
					if (locations.size() > 51) {
						redirect = null;
						throw new RuntimeException("Too many redirects."); 
					}
					locations.add(urlObj); 
					responseHeaders.add(new ArrayList<String[]>());
				}
				HttpURLConnection con = (HttpURLConnection) urlObj.openConnection(proxy);
				con.setRequestMethod(method);
				con.setUseCaches(false);
				con.setConnectTimeout((int) (connectTimeout * 1000f));
				con.setReadTimeout((int) (maxTime * 1000f));
				con.setInstanceFollowRedirects(false);
				if (insecure && con instanceof HttpsURLConnection) {
					((HttpsURLConnection) con).setHostnameVerifier(insecureVerifier);
					((HttpsURLConnection) con).setSSLSocketFactory(insecureFactory);
				}
				for (Map.Entry<String, String> h: headers.entrySet()) con.setRequestProperty(h.getKey(), h.getValue());
				if ("POST".equals(method)) {
					con.setDoInput(true);
					con.setDoOutput(true);
					byte[] data;
					if (form.size() > 0) { // it's upload
						con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
						ByteArrayOutputStream os = new ByteArrayOutputStream();
						byte[] bb;
						for (Map.Entry<String, Util.Ref<String>> en: form.entrySet()) {
							String name = en.getKey(), filename = null, type = null;
							Util.Ref<String> val = en.getValue();
							if (val.getInt() == 1) {
								String[][] ll = Util.split(val.get(), ";", "=");
								String _1st = unquote(ll[0][0].trim());
								for (int j = 1; j < ll.length; j++) {
									if (ll[j].length > 1 && "type".equals(ll[j][0].trim())) {
										type = unquote(ll[j][1].trim());
									} else if (ll[j].length > 1 && "filename".equals(ll[j][0].trim())) {
										filename = unquote(ll[j][1].trim());
									}
								}
								if (_1st.startsWith("@") || _1st.startsWith("<")) { // it's file
									IO in = getIO(_1st.substring(1));
									File f = in instanceof FileIO ? ((FileIO) in).f : null;
									filename = _1st.startsWith("<") ? null : 
											filename != null ? filename : f != null ? f.getAbsolutePath() : name;
									if (f != null && !(f.exists() && f.isFile() && f.canRead()))
										throw new IllegalArgumentException("couldn't open file \"" + filename + "\"");
									bb = readInput(in);
								} else {
									bb = Util.s2b(_1st, null);
								}
							} else {
								bb = Util.s2b(val.get(), null);
							}
							os.write(("--" + BOUNDARY + "\r\n").getBytes());
							os.write(("Content-Disposition: form-data; name=\"" + name + "\"").getBytes());
							if (filename != null) os.write(("; filename=\"" + filename + "\"").getBytes());
							if (type != null) os.write(("\r\nContent-Type: " + type).getBytes());
							os.write(NEWLINE);
							os.write(NEWLINE);
							os.write(bb);
							os.write(NEWLINE);
						}
						os.write(("--" + BOUNDARY + "--\r\n").getBytes());
						data = os.toByteArray();
					} else {
						data = Util.s2b(dataStr, null); // UTF-8
						if (!ignoreContentLength) {
							con.setRequestProperty("Content-Length", Integer.toString(data.length));
						}
						if (!headers.containsKey("Content-Type")) {
							con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
						}
					}
					try {
						OutputStream os = con.getOutputStream();
						os.write(data);
						os.flush();
					} catch (Exception ex) { // connect timeout
						throw new Recoverable(ex, -1);
					}
				}
				redirect = null;
				httpCode = con.getResponseCode();
				if (httpCode >= 300 && httpCode < 400) redirect = con.getHeaderField("Location");
				if (redirect != null) retryLeft = retry;
				InputStream is;
				try {
					is = con.getInputStream();
				} catch (Exception e) {
					if (httpCode == 407 && proxy != Proxy.NO_PROXY && "https".equals(urlObj.getProtocol()) 
							&& headers.containsKey("Proxy-Authorization")) {
						throw new RuntimeException(e.getMessage() + "\nTry using VM argument \"-Djdk.http.auth.tunneling.disabledSchemes=\"", e);
					}
					if (redirect == null) lastEx = e;
					is = con.getErrorStream();
				}
				if (is == null && lastEx != null) throw lastEx;
				byte[] bb = Util.readStream(is, maxDownload, true);
				if (maxDownload <= 0 && bb != null && bb.length > 3 && bb[0] == 31 && bb[1] == -117 && bb[2] == 8) { // gzip, deflate
					is = new GZIPInputStream(new ByteArrayInputStream(bb));
					bb = Util.readStream(is, false);
				}
				int idx = locations.size() - 1;
				fillResponseHeaders(con, responseHeaders.get(idx));
				if (dumpHeader != null) dumpHeader(responseHeaders.get(idx), dumpHeader); 
				if (bb != null && bb.length > 0) writeOutput(output, bb, output == dumpHeader);
				if (lastEx != null) throw lastEx;
				if (redirect == null || !location) {
					rawStdout = stdout.toByteArray();
					execTime = System.currentTimeMillis() - startTime;
					if (cookieJar != null) saveCookies(cookieJar);
					return silent ? fallback : getStdout(resolver, fallback);
				}
			} catch (Throwable e) {
				needRetry = isRecoverable(e.getClass());
				lastEx = e instanceof Recoverable ? e.getCause() : e;
				if (needRetry && retryLeft > 0 && retryDelay > 0) 
					try { Thread.sleep((long) (retryDelay * 1000d)); } catch (Exception ignored) {}
			}
		} while (location && redirect != null || needRetry && --retryLeft >= 0 
				&& (retryMaxTime <= 0 || System.currentTimeMillis() - startTime < (long) (retryMaxTime * 1000d)));
		return error(stdout, stderr, lastEx, silent, resolver, fallback);
	}
	
	/** 根据key获取对应IO，如果iomap中没有，则key作为文件路径创建一个FileIO  */
	private IO getIO(String key) {
		IO io;
		return (io = iomap.get(key)) == null ? new FileIO(key) : io;
	}
	
	private <T> T error(IO stdout, IO stderr, Throwable ex, boolean silent, Resolver<T> rr, T fallback) {
		writeOutput(stderr, Util.dumpStackTrace(ex, false).getBytes(), true);
		httpCode = ex instanceof Recoverable ? ((Recoverable) ex).httpCode : -1;
		rawStdout = ((MemIO) stdout).toByteArray();
		execTime = System.currentTimeMillis() - startTime;
		return silent ? fallback : getStdout(rr, fallback);
	}

	private static void parseCookies(String url, String input) {
		String host = null;
		try { host = new URI(url).getHost(); } catch (Exception ignored) { }
		for (String[] pair: Util.split(input, ";", "=")) {
			HttpCookie cookie = new HttpCookie(pair[0], Util.urlDecode(pair[1]));
			cookie.setDomain(host);
			cookie.setPath("/");
			cookie.setSecure(false);
			cookieStore.add(null, cookie);
		}
	}

	private static String unquote(String s) {
		return s.startsWith("'") && s.endsWith("'") || s.startsWith("\"") && s.endsWith("\"") ?
				s.substring(1, s.length() - 1) : s;
	}
	
	private static void fillResponseHeaders(HttpURLConnection con, List<String[]> headers) {
		headers.clear();
		Object responses = Util.getField(con, null, "responses", null, true); // sun.net.www.MessageHeader
		if (responses == null) { // con is sun.net.www.protocol.https.HttpsURLConnectionImpl
			Object delegate = Util.getField(con, null, "delegate", null, true);
			if (delegate != null) responses = Util.getField(delegate, null, "responses", null, true);
		}
		String[] keys, values;
		Integer nkeys;
		if (responses != null && (nkeys = (Integer) Util.getField(responses, null, "nkeys", null, true)) != null
				&& (keys = (String[]) Util.getField(responses, null, "keys", null, true)) != null
				&& (values = (String[]) Util.getField(responses, null, "values", null, true)) != null) {
			for (int i = 0; i < nkeys; i++) headers.add(new String[] { keys[i], values[i] });
		} else {
			try { headers.add(new String[] { null, con.getResponseMessage() }); } catch (Exception ignored) {}
			for (int i = 0; ; i++) {
				String k = con.getHeaderFieldKey(i), v = con.getHeaderField(i);
				if (k == null && v == null) break;
				headers.add(new String[] { k, v });
			}
		}
	}
	
	private static void dumpHeader(List<String[]> headers, IO dumpHeader) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		for (String[] kv: headers) 
			bos.write(((kv[0] != null ? kv[0] + ": " : "") + (kv[1] != null ? kv[1] : "") + "\r\n").getBytes());
		bos.write(NEWLINE);
		writeOutput(dumpHeader, bos.toByteArray(), false);
	}
	
	/** 读取IO中的数据，如不适用或无数据则返回空数组 */
	private static byte[] readInput(IO in) {
		InputStream is = in.getInputStream();
		byte[] bb;
		if (is == null || (bb = Util.readStream(is, false)) == null) bb = new byte[0];
		in.close();
		return bb;
	}
	
	/** 把数据输出到IO，如不适用则直接返回。如append为true则向数据IO添加，否则覆盖。*/
	private static void writeOutput(IO out, byte[] bb, boolean append) {
		out.setAppend(append);
		OutputStream os = out.getOutputStream();
		if (os == null) return;
		try {
			os.write(bb);
			os.flush();
		} catch (Exception e) {
			Util.logStderr("CUrl.writeOutput: out=%s,bb=%s,append=%s,ex=%s", out, bb, append, Util.dumpStackTrace(e, true));
		}
		out.close();
	}
	
	private static final HashSet<Class> RECOVERABLES = Util.listAdd(
			new HashSet<Class>(), 
			(Class) Recoverable.class, 
			ConnectException.class,
			HttpRetryException.class,
			SocketException.class,
			SocketTimeoutException.class,
			NoRouteToHostException.class);
	
	private static boolean isRecoverable(Class<? extends Throwable> errCls) {
		if (RECOVERABLES.contains(errCls)) return true;
		for (Class re: RECOVERABLES) if (re.isAssignableFrom(errCls)) return true;
		return false;
	}

	///////////////////////////// Inner Classes & static instances ///////////////////////////////////////
	
	public interface Resolver<T> {
		T resolve(int httpCode, byte[] responseBody) throws Throwable;
	}
	
	public static class ToStringResolver implements Resolver<String> {
		final private String charset;
		public ToStringResolver(String charset) { this.charset = charset; }
		@Override
		public String resolve(int httpCode, byte[] raw) throws Throwable { return new String(raw, charset); }
	}
	
	public static final Resolver<byte[]> RAW = new Resolver<byte[]>() {
		@Override
		public byte[] resolve(int httpCode, byte[] raw) { return raw; }
	};
	
	public static final ToStringResolver UTF8 = new ToStringResolver("UTF-8");
	public static final ToStringResolver GBK = new ToStringResolver("GBK");
	public static final ToStringResolver ISO_8859_1 = new ToStringResolver("ISO-8859-1");
	
	public interface IO {
		InputStream getInputStream();
		OutputStream getOutputStream();
		void setAppend(boolean append);
		void close();
	}
	
	public static final class WrappedIO implements IO {
		final InputStream is;
		final OutputStream os;
		public WrappedIO(String s, String charset) { this(Util.s2b(s, charset)); }
		public WrappedIO(byte[] byteArray) { this(new ByteArrayInputStream(byteArray)); }
		public WrappedIO(InputStream is) { this.is = is; this.os = null; }
		public WrappedIO(OutputStream os) { this.is = null; this.os = os; }
		public InputStream getInputStream() { return is; }
		public OutputStream getOutputStream() { return os; }
		public void setAppend(boolean append) {} // not supported
		public void close() {} // wrapper is not responsible for closing
		public String toString() { return "WrappedIO<" + is + "," + os + ">"; }
	}
	
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static final class FileIO implements IO {
		private File f;
		private transient InputStream is;
		private transient OutputStream os;
		boolean append = false;
		
		public FileIO(File f) {
			this.f = f.getAbsoluteFile();
		}
		
		public FileIO(String path) {
			this(new File(path));
		}
		
		public InputStream getInputStream() {
			if (f.exists() && f.isFile() && f.canRead()) {
				try { return is = new FileInputStream(f); } catch (Exception ignored) {}
			}
			return null;
		} 
		
		public OutputStream getOutputStream() {
			Util.mkdirs(f.getParentFile());
			try { 
				f.createNewFile();
				f.setReadable(true, false);
				f.setWritable(true, false);
				os = new FileOutputStream(f, append); 
			} catch (Exception ignored) {}
			return os;
		}
		
		public void setAppend(boolean append) { 
			this.append = append;
		}
		
		public void close() {
			try { if (is != null) is.close(); } catch (Exception ignored) {}
			try { if (os != null) os.close(); } catch (Exception ignored) {}
		}
		
		public String toString() {
			return "FileIO<" + f + ">";
		}
	}
	
	public static final class MemIO extends ByteArrayOutputStream implements IO {
		public MemIO() { super(0); }
		public InputStream getInputStream() { return new ByteArrayInputStream(buf, 0, count); }
		public OutputStream getOutputStream() { return this; }
		public void setAppend(boolean append) { if (!append) this.reset(); }
		public void close() {} // not needed
		public String toString() { return "MemIO<" + this.hashCode() + ">"; } 
		/**
		 * This is useful when the MemIO was used as the target of --dump-header
		 * @return
		 */
		public Map<String, String> parseDumpedHeader() {
			Map<String, String> result = new LinkedHashMap<String, String>();
			String s = new String(this.toByteArray());
			for (String l: s.split("[\r\n]+")) {
				if (l.trim().length() == 0) continue;
				String[] kv = l.split(":", 2);
				result.put(kv[0], kv.length > 1 ? kv[1].trim() : "");
			}
			return result;
		}
	}

	public static final class CookieStore implements java.net.CookieStore {
		
		private final ThreadLocal<Map<String, List<HttpCookie>>> cookies = new ThreadLocal<Map<String, List<HttpCookie>>>() {
			@Override protected synchronized Map<String, List<HttpCookie>> initialValue() {
				return new HashMap<String, List<HttpCookie>>();
			}
		};

		private CookieStore() { }

		@Override
		public void add(URI uri, HttpCookie cookie) {
			normalize(uri, cookie);
			Map<String, List<HttpCookie>> map = Util.mapListAdd(cookies.get(), ArrayList.class, cookie.getDomain());
			List<HttpCookie> cc = map.get(cookie.getDomain());
			cc.remove(cookie);
			if (cookie.getMaxAge() != 0) cc.add(cookie);
		}
		
		@Override
		public List<HttpCookie> get(URI uri) {
			List<HttpCookie> result = getCookies();
			String host = uri.getHost();
			for (ListIterator<HttpCookie> it = result.listIterator(); it.hasNext();) {
				String domain = it.next().getDomain();
				if (!domainMatches(domain, host)) it.remove();
			}
			return result;
		}

		@Override
		public List<HttpCookie> getCookies() {
			List<HttpCookie> result = new ArrayList<HttpCookie>();
			for (List<HttpCookie> cc: cookies.get().values()) {
				for (ListIterator<HttpCookie> it = cc.listIterator(); it.hasNext();)
					if (it.next().hasExpired()) it.remove();
				result.addAll(cc);
			}
			return result;
		}

		@Override
		public List<URI> getURIs() {
			Set<URI> result = new HashSet<URI>();
			for (HttpCookie cookie: getCookies()) {
				String scheme = cookie.getSecure() ? "https" : "http";
				String domain = cookie.getDomain();
				if (domain.startsWith(".")) domain = domain.substring(1);
				try {
					result.add(new URI(scheme, domain, cookie.getPath(), null));
				} catch (URISyntaxException ignored) {}
			}
			return new ArrayList<URI>(result);
		}

		@Override
		public boolean remove(URI uri, HttpCookie cookie) {
			normalize(uri, cookie);
			List<HttpCookie> cc = cookies.get().get(cookie.getDomain());
			return cc != null && cc.remove(cookie);
		}

		@Override
		public boolean removeAll() {
			cookies.get().clear();
			return true;
		}

		private static void normalize(URI uri, HttpCookie cookie) {
			if (cookie.getDomain() == null && uri != null) cookie.setDomain(uri.getHost());
			if (cookie.getPath() == null && uri != null) cookie.setPath(uri.getPath());
			if (Util.empty(cookie.getDomain())) throw new IllegalArgumentException("illegal cookie domain: " + cookie.getDomain());
			if (Util.empty(cookie.getPath())) cookie.setPath("/");
			cookie.setVersion(0);
		}

		/** Check a string domain-matches a given domain string or not. Refer to section 5.1.3 RFC6265 */
		private static boolean domainMatches(String domain, String host) {
			if (domain == null || host == null) return false;
			if (domain.startsWith(".")) { // it's a suffix
				return host.toLowerCase().endsWith(domain.toLowerCase());
			} else {
				return host.equalsIgnoreCase(domain);
			}
		}

	}
	
	public static final class Recoverable extends Exception {
		private final int httpCode;
		public Recoverable() { this(null, -1); }
		public Recoverable(Throwable cause, int httpCode) { super(cause); this.httpCode = httpCode; }
	}

	@SuppressWarnings({"WeakerAccess", "JavaDoc", "ConstantConditions", "ResultOfMethodCallIgnored", "StatementWithEmptyBody", "UnusedReturnValue", "SuspiciousMethodCalls"})
	final static class Util {

		/**
		 * 判断字符串是否为空，即null或空字符串
		 *
		 * @param s
		 * @return
		 */
		public static boolean empty(String s) {
			return s == null || s.length() == 0;
		}

		/**
		 * 将o转换为List<T>。
		 * 如o为null，返回空列表；如o为Collection或数组，返回使用o的元素填充的列表；否则，返回包含o的单元素列表。
		 *
		 * @param o
		 */
		@SuppressWarnings("unchecked")
		public static <T> List<T> asList(Object o) {
			if (o == null) {
				return new ArrayList<T>(0);
			}
			if (o instanceof Collection) {
				return new ArrayList<T>((Collection<T>) o);
			} else if (o.getClass().isArray()) {
				ArrayList<T> list = new ArrayList<T>();
				for (int i = 0, n = Array.getLength(o); i < n; i++) list.add((T) Array.get(o, i));
				return list;
			} else {
				return listAdd(new ArrayList<T>(1), (T) o);
			}
		}

		/**
		 * 返回给定值的引号表示，如给定null或数值型，则不使用引号。可用于处理SQL值
		 *
		 * @param o
		 * @return
		 */
		public static String qt(Object o) {
			return o == null || o instanceof Boolean || o instanceof Number ?
					"" + o : o instanceof Character ? "'" + o + "'" : "\"" + o + "\"";
		}

		/**
		 * 将给定异常对象转换为字符串
		 *
		 * @param e
		 * @param singleLine 是否单行输出，即将回车、换行、制表转义
		 * @return
		 */
		public static String dumpStackTrace(Throwable e, boolean singleLine) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String s = sw.toString();
			return singleLine ? s.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") : s;
		}

		/**
		 * 向标准错误输出System.err格式化输出一条信息
		 *
		 * @param msg
		 * @param args
		 */
		public static void logStderr(String msg, Object... args) {
			if (args.length > 0) msg = String.format(msg, args);
			System.err.println("[ERR] [" + new Date() + "] " + msg);
		}

		///////////////////////////// Collections Utils /////////////////////////////////

		/**
		 * 安全从Map中获取给定键的对应值，如果map为空，或不存在给定键，则返回默认值
		 *
		 * @param map
		 * @param key
		 * @param fallback
		 * @return
		 */
		public static <K, V> V mapGet(Map<K, V> map, K key, V fallback) {
			V v;
			return map != null && (v = map.get(key)) != null ? v : fallback;
		}

		/**
		 * 向给定MapList中一次性加入0至多个值
		 *
		 * @param map
		 * @param key
		 * @param val
		 */
//	@SafeVarargs
		public static <K, V> Map<K, List<V>> mapListAdd(Map<K, List<V>> map, K key, V... val) {
			return mapListAdd(map, ArrayList.class, key, val);
		}

		/**
		 * 向给定MapCollection中一次性加入0至多个值，如子容器不存在，则使用collectionClass创建之
		 *
		 * @param map
		 * @param collectionClass
		 * @param key
		 * @param val
		 */
		@SuppressWarnings({"rawtypes", "unchecked"})
//	@SafeVarargs
		public static <K, V, L extends Collection<V>> Map<K, L> mapListAdd(Map<K, L> map, Class<? extends Collection> collectionClass, K key, V... val) {
			L l;
			if ((l = map.get(key)) == null) try {
				map.put(key, l = (L) collectionClass.newInstance());
			} catch (Exception ignored) {
			}
			Collections.addAll(l, val);
			return map;
		}

		/**
		 * 从给定MapMap中，根据键，子键获取对应值，如不存在，则返回默认值
		 *
		 * @param map
		 * @param key
		 * @param subkey
		 * @param fallback
		 * @return
		 */
		public static <K, S, V, M extends Map<S, V>> V mapMapGet(Map<K, M> map, K key, S subkey, V fallback) {
			M m;
			V ret;
			return (m = map.get(key)) != null && (ret = m.get(subkey)) != null ? ret : fallback;
		}

		/**
		 * 用于安全遍历一个可能为空的迭代器
		 *
		 * @param iter
		 * @return
		 */
		public static <T> Iterable<T> safeIter(Iterable<T> iter) {
			return iter != null ? iter : new ArrayList<T>(0);
		}

		/**
		 * 用于安全遍历一个可能为空的数组
		 *
		 * @param array
		 * @param componentType
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public static <T> T[] safeArray(T[] array, Class<T> componentType) {
			return array != null ? array : (T[]) Array.newInstance(componentType, 0);
		}

		/**
		 * 创建一个给定键值类型的HashMap，并使用0至多个键值对填充
		 *
		 * @param keyValuePairs
		 * @return
		 */
		public static Map<String, Object> newMap(Object... keyValuePairs) {
			return mapPut(new LinkedHashMap<String, Object>(), keyValuePairs);
		}

		/**
		 * 使用给定的0至多个键值对填充传入的Map
		 *
		 * @param map
		 * @param keyValuePairs
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public static <K, V, M extends Map<K, V>> M mapPut(M map, Object... keyValuePairs) {
			if ((keyValuePairs.length & 1) != 0)
				throw new IllegalArgumentException("the number of keyValuePairs arguments must be odd");
			for (int i = 0, n = keyValuePairs.length; i < n; i += 2) {
				map.put((K) keyValuePairs[i], (V) keyValuePairs[i + 1]);
			}
			return map;
		}

		/**
		 * 使用0至多个值来填充给定List容器
		 *
		 * @param list
		 * @param values
		 * @return
		 */
//	@SafeVarargs
		public static <T, L extends Collection<T>> L listAdd(L list, T... values) {
			list.addAll(Arrays.asList(values));
			return list;
		}

////////////////////////////// Utility Data Structures ///////////////////////

		/**
		 * 单个对象及一个整数的快速引用，
		 * Ref, Ref2, Ref3为简单的容器类，可用于：
		 * * 包装基本类型，与代码闭包（Java匿名内部类）互传数据
		 * * 方法一次返回固定数量的多个返回值，可代替Object[]，类型安全
		 * * 覆盖了equals和hashCode方法，因此可以作为Map/Set的键
		 */
		public static class Ref<T> {
			public int i;
			public T v;

			public Ref(T v) {
				this(0, v);
			}

			public Ref(int i, T v) {
				setInt(i);
				set(v);
			}

			public T get() {
				return v;
			}

			public void set(T v) {
				this.v = v;
			}

			public int getInt() {
				return i;
			}

			public void setInt(int i) {
				this.i = i;
			}

			@SuppressWarnings("unchecked")
			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof Ref)) return false;
				Ref<T> o;
				return (o = (Ref<T>) obj) != null && i == o.i && (v == null ? o.v == null : v.equals(o.v));
			}

			@Override
			public int hashCode() {
				return i + (v == null ? 0 : v.hashCode());
			}

			@Override
			public String toString() {
				return String.format("Ref{%s, %s}", i, qt(v));
			}

		}

		//////////////////////////////// String Utils /////////////////////////////////////

		public static String urlDecode(String s) {
			return urlDecode(s, "UTF-8");
		}

		public static String urlDecode(String s, String enc) {
			if (!empty(s)) try {
				return URLDecoder.decode(s, enc);
			} catch (Exception ignored) {
			}
			return s;
		}

		/**
		 * 使用charset指定的字符编码把字节数组bb转换为字符串
		 *
		 * @param bb
		 * @param charset  如为null则默认UTF-8
		 * @param fallback
		 * @return
		 */
		public static String b2s(byte[] bb, String charset, String fallback) {
			return b2s(bb, 0, bb.length, charset, fallback);
		}

		/**
		 * 使用给定的编码方式将传入的字节数组片段解码为字符串
		 *
		 * @param bb
		 * @param offset   字节数组中的起始偏移量
		 * @param count    字节长度
		 * @param charset  字符串编码，为null则为UTF-8
		 * @param fallback 转换失败的默认值
		 * @return
		 */
		public static String b2s(byte[] bb, int offset, int count, String charset, String fallback) {
			try {
				int start = bb.length - offset >= 3 && bb[offset] == 0xEF && bb[offset + 1] == 0xBB && bb[offset + 2] == 0xBF ? 3 : 0; // deal BOM
				return new String(bb, offset + start, count - start, charset == null ? "UTF-8" : charset);
			} catch (Exception e) {
				return fallback;
			}
		}

		/**
		 * 用给定字符串编码将给定字符串编码为字节数组
		 *
		 * @param s
		 * @param charset 字符串编码，为null则为UTF-8
		 * @return
		 */
		public static byte[] s2b(String s, String charset) {
			try {
				return s.getBytes(charset == null ? "UTF-8" : charset);
			} catch (Exception e) {
				return null;
			}
		}

		/**
		 * 先用delim1分割给定字符串，再用delim2分割每个子串。返回二维数组
		 *
		 * @param s
		 * @param delim1
		 * @param delim2
		 * @return
		 */
		public static String[][] split(String s, String delim1, String delim2) {
			String[] ss = s.split(delim1);
			String[][] result = new String[ss.length][];
			for (int i = ss.length; --i >= 0; result[i] = ss[i].split(delim2));
			return result;
		}

		/**
		 * 先用entryDelim分割给定字符串，再用kvDelim分割每个子串。返回映射
		 *
		 * @param s
		 * @param entryDelim
		 * @param kvDelim
		 * @param toMap      将键值对填充，可为null
		 * @return toMap，如传入toMap为null，则返回新创建并填充的HashMap
		 */
		public static Map<String, String> split(String s, String entryDelim, String kvDelim, Map<String, String> toMap) {
			String[] ss = s.split(entryDelim);
			if (toMap == null) toMap = new HashMap<String, String>(ss.length);
			for (String l : ss) {
				String[] sub = l.split(kvDelim);
				toMap.put(sub[0].trim(), sub.length > 1 ? sub[1].trim() : "");
			}
			return toMap;
		}

		/**
		 * 使用delim和subDelim连接二维数组，二维集合或映射
		 *
		 * @param mapOrColl 可以是Map, Collection<Collection<?>>, Collection<?>[], Collection<?[]>, ?[][]
		 * @param delim     子串间连接符
		 * @param subDelim  子串内连接符
		 * @return
		 */
		public static String join(Object mapOrColl, String delim, String subDelim) {
			List<List<Object>> all = new ArrayList<List<Object>>();
			if (mapOrColl == null) { // do nothing
			} else if (mapOrColl instanceof Map) {
				for (Map.Entry<?, ?> kv : ((Map<?, ?>) mapOrColl).entrySet()) {
					all.add(listAdd(new ArrayList<Object>(2), kv.getKey(), kv.getValue()));
				}
			} else if (mapOrColl instanceof Collection) {
				for (Object o : (Collection<?>) mapOrColl) all.add(asList(o));
			} else if (mapOrColl.getClass().isArray()) {
				for (int i = 0, n = Array.getLength(mapOrColl); i < n; all.add(asList(Array.get(mapOrColl, i++))))
					;
			} else { // plain object
				all.add(asList(mapOrColl));
			}
			StringBuilder sb = new StringBuilder();
			int i = 0, j;
			for (List<Object> sub : all) {
				if (i++ > 0) sb.append(delim);
				j = 0;
				for (Object o : sub) sb.append(j++ > 0 ? subDelim : "").append(o);
			}
			return sb.toString();
		}

		/**
		 * Base64编码。此方法在不同平台上调用平台本地方法进行编码。
		 */
		public static String base64Encode(byte[] bb) {
			Class<?> clz = getClass("java.util.Base64", null);
			if (clz != null) {
				Object encoder = invokeSilent(null, clz, "getEncoder", false, null);
				return (String) invokeSilent(encoder, null, "encodeToString", false, "[B", (Object) bb);
			}
			clz = getClass("sun.misc.BASE64Encoder", null);
			if (clz != null) {
				Object encoder = createInstance(clz, "", true);
				return ((String) invokeSilent(encoder, null, "encode", true, "[B", (Object) bb)).replaceAll("[\r\n]+", "");
			}
			clz = getClass("org.apache.commons.codec.binary.Base64", null);
			if (clz != null) {
				return (String) invokeSilent(null, clz, "encodeBase64String", false, "[B", (Object) bb);
			}
			clz = getClass("android.util.Base64", null);
			if (clz != null) {
				return (String) invokeSilent(null, clz, "encodeToString", false, "[BI", bb, 2); // NO_WRAP
			}
			throw new RuntimeException(new NoSuchMethodException("base64Encode"));
		}

		////////////////////////////////// IO Utils ///////////////////////////////////

		public static byte[] readStream(InputStream is, boolean close) {
			return readStream(is, 0, close);
		}

		/**
		 * 从输入流中读取数据，如果输入流达到文件末(EOF)，则返回null
		 *
		 * @param is
		 * @return null if EOF reached
		 */
		public static byte[] readStream(InputStream is, int interruptOnSize, boolean close) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int count = 0, c;
			while ((c = pipeStream(is, bos)) > 0 && (interruptOnSize <= 0 || count < interruptOnSize)) count += c;
			if (c < 0) count += (c & PIPE_COUNT_MASK);
			byte[] result = c < 0 && count == 0 ? null : bos.toByteArray();
			if (close) try {
				is.close();
			} catch (Exception ignored) {
			}
			return result;
		}

		public static final int PIPE_COUNT_MASK = 0x7FFFFFFF;

		private static final int BUFFER_SIZE = 10000;

		/**
		 * 从输入流直接管道传送到输出流
		 *
		 * @param source
		 * @param destination
		 * @return 如小于0则说明输入流已到达文件末；返回值和PIPE_COUNT_MASK相与的结果为传送数据的字节长度
		 */
		public static int pipeStream(InputStream source, OutputStream destination) {
			byte[] bb = new byte[BUFFER_SIZE];
			int len, count = 0;
			do {
				len = 0;
				try {
					len = source.read(bb);
				} catch (SocketTimeoutException e) { // no data, but the socket connection is still alive
				} catch (SocketException e) { // EOF or socket disconnected
//				LogUtil.error(TaskAllocator.logger, ">>pipeStream.SocketExcetion,len=" + len + ",e=");
//				LogUtil.error(TaskAllocator.logger, Util.stackTraceToString(e));
					len = -1;
				} catch (IOException e) { // unexpected exceptions
//				LogUtil.error(TaskAllocator.logger, Util.stackTraceToString(e));
					throw new RuntimeException(e);
				}
				if (len > 0) {
					try {
						destination.write(bb, 0, len);
//					LogUtil.info(TaskAllocator.debug, new String(bb, 0, len));
					} catch (IOException e) { // unexpected exceptions while writing
//					LogUtil.error(TaskAllocator.logger, Util.stackTraceToString(e));
						throw new RuntimeException(e);
					}
					count += len;
				}
			} while (len == BUFFER_SIZE);
			return len < 0 ? (0x80000000 | count) : count; // len < 0 -> EOF reached
		}

		/**
		 * 递归创建给定目录及其所有父目录。
		 * 和File.mkdirs不同的是，此方法会设定所有创建的目录为可读写
		 *
		 * @param dir
		 */
		public static void mkdirs(File dir) {
			File parent = dir.getAbsoluteFile();
			List<File> mkdir = new ArrayList<File>();
			for (; !parent.exists() || !parent.isDirectory(); parent = parent.getParentFile()) {
				mkdir.add(parent);
			}
			for (int i = mkdir.size(); --i >= 0; ) {
				File d = mkdir.get(i);
				d.mkdir();
				d.setReadable(true, false);
				d.setWritable(true, false);
			}
		}

//////////////////////////////// Reflection Utils ////////////////////////////////////

		/**
		 * 使用传入的classloader加载给定类
		 *
		 * @param className
		 * @param cl        如为空则使用Util类的加载器
		 * @return
		 */
		public static Class<?> getClass(String className, ClassLoader cl) {
			try {
				return (cl != null ? cl : CUrl.class.getClassLoader()).loadClass(className);
			} catch (ClassNotFoundException e) {
				return null;
			}
		}

		/**
		 * 通过反射调用类的构造方法来创建一个该类对象
		 *
		 * @param cls       类，不可为空
		 * @param signature 构造方法的参数签名，可为null，为null则根据实参数量来查找构造方法
		 * @param args      构造函数参数列表
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public static <T> T createInstance(Class<T> cls, String signature, boolean ignoreAccess, Object... args) {
			if (signature == null && args.length == 0) {
				try {
					return cls.newInstance();
				} catch (Exception ex) {
					throw new IllegalArgumentException(ex);
				}
			}
			return (T) invoke(null, cls, "<init>", ignoreAccess, signature, args);
		}

		/**
		 * 获取给定实例或类中给定域的值
		 *
		 * @param thiz         实例域所属实例，如需要获取静态域，此参数必须为null
		 * @param cls          静态域所属类，如需要获取实例域，此参数必须为null
		 * @param fieldName
		 * @param fallback
		 * @param ignoreAccess 是否忽略访问控制，如为true，则可以访问非公有域
		 * @return
		 */
		public static Object getField(Object thiz, Class<?> cls, String fieldName, Object fallback, boolean ignoreAccess) {
			if (thiz == null && cls == null || fieldName == null)
				throw new NullPointerException("inst=" + thiz + ",class=" + cls + ",field=" + fieldName);
			try {
				for (MemberInfo mi : safeIter(getMembers(thiz != null ? thiz.getClass() : cls, fieldName))) {
					if (-1 == mi.numArgs && (ignoreAccess || (mi.member.getModifiers() & Modifier.PUBLIC) != 0)) {
						AccessibleObject acc;
						if (ignoreAccess && !(acc = (AccessibleObject) mi.member).isAccessible()) acc.setAccessible(true);
						return ((Field) mi.member).get(thiz);
					}
				}
			} catch (Exception ignored) {
			}
			return fallback;
		}

		/**
		 * 反射调用一个实例或静态方法，并忽略所有异常
		 *
		 * @param thiz         实例方法所属实例，如需要调用静态方法，此参数必须为null
		 * @param cls          静态方法所属类，如需要调用实例方法，此参数必须为null
		 * @param methodName
		 * @param ignoreAccess 是否忽略访问控制
		 * @param signature    方法签名，如为空则根据实参数量查找方法
		 * @param args         参数列表
		 * @return
		 */
		public static Object invokeSilent(Object thiz, Class<?> cls, String methodName, boolean ignoreAccess, String signature, Object... args) {
			try {
				return invoke(thiz, cls, methodName, ignoreAccess, signature, args);
			} catch (Exception ignored) {
			}
			return null;
		}

		/**
		 * 反射调用一个实例或静态方法
		 *
		 * @param thiz         对象实例，如调用静态方法，则必须为null
		 * @param cls          类，如thiz参数不为null则此参数忽略，直接使用该实例的类
		 * @param methodName   方法名
		 * @param ignoreAccess 忽略访问权限
		 * @param signature    方法参数签名，如"ILjava.lang.String;"，若为null则本方法根据传入的实参数量查找方法；
		 *                     如果存在多个参数数量相同的同名方法，则必须显式指定方法签名，本方法不会自动根据实参类型进行匹配查找
		 *                     内部类格式如"Ljava.util.Map$Entry;"
		 * @param args         被调用方法的实参列表
		 * @return 被调用方法的返回值
		 */
		public static Object invoke(Object thiz, Class<?> cls, String methodName, boolean ignoreAccess, String signature, Object... args) {
			if (thiz == null && cls == null || methodName == null)
				throw new NullPointerException("inst=" + thiz + ",class=" + cls + ",method=" + methodName);
			List<MemberInfo> found = getMembers(thiz != null ? thiz.getClass() : cls, methodName);
			try {
				Member m = null;
				if (found == null) { // do nothing
				} else if (signature == null) {
					int len = args.length;
					for (MemberInfo mi : found) {
						if (len == mi.numArgs && (ignoreAccess || (mi.member.getModifiers() & Modifier.PUBLIC) != 0)) {
							m = mi.member;
							break;
						}
					}
				} else {
					signature = signature.replace('/', '.');
					for (MemberInfo mi : found) {
						if (signature.equals(mi.signature) && (ignoreAccess || (mi.member.getModifiers() & Modifier.PUBLIC) != 0)) {
							m = mi.member;
							break;
						}
					}
				}
				if (m == null) {
					StringBuilder msg = new StringBuilder().append('"').append(methodName).append('"');
					if (signature == null) {
						msg.append(" with ").append(args.length).append(" parameter(s)");
					} else {
						msg.append(" with signature \"").append(signature).append("\"");
					}
					throw new NoSuchMethodException(msg.toString());
				}
				AccessibleObject acc;
				if (ignoreAccess && !(acc = (AccessibleObject) m).isAccessible()) acc.setAccessible(true);
				return m instanceof Method ? ((Method) m).invoke(thiz, args) : ((Constructor<?>) m).newInstance(args);
			} catch (Exception ex) {
				throw new IllegalArgumentException(ex);
			}
		}

		private static final Map<String, Object> primaryTypes = newMap(
				byte.class, 'B',
				char.class, 'C',
				double.class, 'D',
				float.class, 'F',
				int.class, 'I',
				long.class, 'J',
				short.class, 'S',
				void.class, 'V',
				boolean.class, 'Z');

		/**
		 * 将参数列表转换成签名字符串
		 *
		 * @param types
		 * @return
		 */
		public static String getSignature(Class<?>... types) {
			StringBuilder sb = new StringBuilder();
			for (Class<?> t : types) {
				while (t.isArray()) {
					sb.append('[');
					t = t.getComponentType();
				}
				Character c;
				if ((c = (Character) primaryTypes.get(t)) != null) {
					sb.append(c);
				} else {
					sb.append('L').append(t.getName()).append(';');
				}
			}
			return sb.toString();
		}

		private static final Map<Class<?>, Map<String, List<MemberInfo>>> mapClassMembers = new HashMap<Class<?>, Map<String, List<MemberInfo>>>();

		private static synchronized List<MemberInfo> getMembers(Class<?> cls, String name) {
			if (!mapClassMembers.containsKey(cls)) {
				Map<String, List<MemberInfo>> map;
				mapClassMembers.put(cls, map = new LinkedHashMap<String, List<MemberInfo>>());
				Class<?> clss = cls;
				while (clss != null && !Object.class.equals(clss)) {
					for (Constructor<?> c : safeArray(clss.getDeclaredConstructors(), Constructor.class)) {
						Class<?>[] ptypes = c.getParameterTypes();
						mapListAdd(map, "<init>", new MemberInfo(getSignature(ptypes), ptypes.length, c));
					}
					for (Method m : safeArray(clss.getDeclaredMethods(), Method.class)) {
						Class<?>[] ptypes = m.getParameterTypes();
						mapListAdd(map, m.getName(), new MemberInfo(getSignature(ptypes), ptypes.length, m));
					}
					for (Field f : safeArray(clss.getDeclaredFields(), Field.class)) {
						mapListAdd(map, f.getName(), new MemberInfo(null, -1, f));
					}
					clss = clss.getSuperclass();
				}
			}
			return mapMapGet(mapClassMembers, cls, name, null);
		}

		private static class MemberInfo {
			String signature; // null for field
			int numArgs; // -1 for field
			Member member;

			MemberInfo(String sign, int num, Member member) {
				signature = sign;
				numArgs = num;
				this.member = member;
			}

			public final String toString() {
				return member.toString();
			}
		}

	}
	
	public static void main(String[] args) {
		System.out.println(new CUrl().opt(args).exec(null));
	}

}
