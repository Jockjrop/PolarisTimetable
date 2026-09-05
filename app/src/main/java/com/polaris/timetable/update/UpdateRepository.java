package com.polaris.timetable.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * latest.json 网络获取：仅 HTTPS、主机白名单、最多 5 次重定向、256 KiB 上限。
 * 仅接受 HTTP 200；请求头带明确的 Accept 与 User-Agent。
 * 不记录完整下载 URL 查询参数、响应正文或设备信息。
 */
public final class UpdateRepository {

    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 15_000;
    public static final int GITEE_CONNECT_TIMEOUT_MS = 4_000;
    public static final int GITEE_READ_TIMEOUT_MS = 6_000;
    public static final int MAX_REDIRECTS = 5;
    public static final String USER_AGENT_PREFIX = "PolarisTimetable/";

    /** 连接工厂可注入，供单元测试以本地假流覆盖下载/重定向行为。 */
    public interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    /** 获取失败时携带错误种类；不携带原始异常细节以避免泄露内部信息。 */
    public static final class FetchException extends IOException {
        public final UpdateError error;

        FetchException(UpdateError error) {
            super(error.name());
            this.error = error;
        }
    }

    private final ConnectionFactory connectionFactory;

    public UpdateRepository() {
        this(null);
    }

    public UpdateRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory != null
                ? connectionFactory
                : new ConnectionFactory() {
                    @Override
                    public HttpURLConnection open(URL url) throws IOException {
                        return (HttpsURLConnection) url.openConnection();
                    }
                };
    }

    /**
     * 拉取清单正文。manifestUrl 为固定稳定入口；本方法自动追加按天变化的
     * 查询参数以降低中间缓存返回陈旧清单的概率。
     */
    public String fetchManifest(String manifestUrl, String versionName) throws FetchException {
        return fetchJson(manifestUrl, versionName, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, true);
    }

    /**
     * 通过公开 Gitee Release API 找到 latest.json 附件，再读取其真实下载地址。
     * 客户端请求不包含 access_token、Cookie 或任何用户数据。
     */
    public String fetchGiteeManifest(String latestReleaseUrl, String versionName)
            throws FetchException {
        String releaseBody = fetchJson(latestReleaseUrl, versionName,
                GITEE_CONNECT_TIMEOUT_MS, GITEE_READ_TIMEOUT_MS, false);
        final String manifestUrl;
        try {
            JSONObject release = new JSONObject(releaseBody);
            Object prerelease = release.opt("prerelease");
            Object tagValue = release.opt("tag_name");
            Object assetsValue = release.opt("assets");
            if (!(prerelease instanceof Boolean) || (Boolean) prerelease
                    || !(tagValue instanceof String) || !(assetsValue instanceof JSONArray)) {
                throw new FetchException(UpdateError.INVALID_METADATA);
            }
            String tag = (String) tagValue;
            if (!tag.matches("^v\\d+\\.\\d+\\.\\d+$")) {
                throw new FetchException(UpdateError.INVALID_METADATA);
            }
            JSONArray assets = (JSONArray) assetsValue;
            String found = null;
            for (int index = 0; index < assets.length(); index++) {
                Object item = assets.get(index);
                if (!(item instanceof JSONObject)) {
                    throw new FetchException(UpdateError.INVALID_METADATA);
                }
                JSONObject asset = (JSONObject) item;
                if (!"latest.json".equals(asset.opt("name"))) {
                    continue;
                }
                Object urlValue = asset.opt("browser_download_url");
                if (!(urlValue instanceof String) || found != null) {
                    throw new FetchException(UpdateError.INVALID_METADATA);
                }
                URL url = requireAllowedUrl((String) urlValue);
                String expectedSuffix = "/releases/download/" + tag + "/latest.json";
                if (!"gitee.com".equalsIgnoreCase(url.getHost())
                        || !url.getPath().endsWith(expectedSuffix)) {
                    throw new FetchException(UpdateError.INVALID_METADATA);
                }
                found = url.toExternalForm();
            }
            if (found == null) {
                throw new FetchException(UpdateError.INVALID_METADATA);
            }
            manifestUrl = found;
        } catch (JSONException exception) {
            throw new FetchException(UpdateError.INVALID_METADATA);
        }
        String manifestBody = fetchJson(manifestUrl, versionName,
                GITEE_CONNECT_TIMEOUT_MS, GITEE_READ_TIMEOUT_MS, true);
        try {
            Object manifestVersion = new JSONObject(manifestBody).opt("versionName");
            if (!(manifestVersion instanceof String)
                    || !manifestUrl.contains("/releases/download/v" + manifestVersion + "/")) {
                throw new FetchException(UpdateError.INVALID_METADATA);
            }
        } catch (JSONException exception) {
            throw new FetchException(UpdateError.INVALID_METADATA);
        }
        return manifestBody;
    }

    private String fetchJson(String resourceUrl, String versionName, int connectTimeoutMs,
                             int readTimeoutMs, boolean cacheBust) throws FetchException {
        String day = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(new java.util.Date());
        String requestUrl = cacheBust
                ? resourceUrl + (resourceUrl.contains("?") ? "&" : "?") + "day=" + day
                : resourceUrl;
        URL current = requireAllowedUrl(requestUrl);
        String userAgent = USER_AGENT_PREFIX + versionName + " Android/"
                + android.os.Build.VERSION.SDK_INT;

        for (int redirects = 0; ; ) {
            HttpURLConnection connection = null;
            try {
                connection = connectionFactory.open(current);
                connection.setConnectTimeout(connectTimeoutMs);
                connection.setReadTimeout(readTimeoutMs);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", userAgent);
                int code = connection.getResponseCode();
                if (code == 200) {
                    return readBodyCapped(connection);
                }
                if (code >= 300 && code < 400) {
                    // 收到重定向响应时即计数：恰好 MAX_REDIRECTS 次允许，第 6 次拒绝。
                    if (redirects >= MAX_REDIRECTS) {
                        throw new FetchException(UpdateError.NETWORK);
                    }
                    redirects++;
                    String location = connection.getHeaderField("Location");
                    current = requireAllowedUrl(location);
                    continue;
                }
                throw new FetchException(UpdateError.HTTP);
            } catch (java.net.SocketTimeoutException exception) {
                throw new FetchException(UpdateError.TIMEOUT);
            } catch (MalformedURLException exception) {
                throw new FetchException(UpdateError.NETWORK);
            } catch (IOException exception) {
                if (exception instanceof FetchException) {
                    throw (FetchException) exception;
                }
                throw new FetchException(UpdateError.NETWORK);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    /** 读取正文并执行 256 KiB 上限；超出上限按清单非法处理。 */
    private static String readBodyCapped(HttpURLConnection connection) throws FetchException {
        InputStream stream;
        try {
            stream = connection.getInputStream();
        } catch (IOException exception) {
            throw new FetchException(UpdateError.NETWORK);
        }
        if (stream == null) {
            throw new FetchException(UpdateError.NETWORK);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int read;
            while ((read = stream.read(chunk)) != -1) {
                if (buffer.size() + read > UpdateJsonParser.MAX_MANIFEST_BYTES) {
                    throw new FetchException(UpdateError.INVALID_METADATA);
                }
                buffer.write(chunk, 0, read);
            }
        } catch (IOException exception) {
            // 超大响应等内部 FetchException 必须原样透传，不得被重映射为网络错误。
            if (exception instanceof FetchException) {
                throw (FetchException) exception;
            }
            throw new FetchException(UpdateError.NETWORK);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // 关闭失败不影响结果。
            }
        }
        return new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static URL requireAllowedUrl(String spec) throws FetchException {
        try {
            return UpdateJsonParser.toAllowedHttpsUrl(spec);
        } catch (MalformedURLException exception) {
            throw new FetchException(UpdateError.NETWORK);
        }
    }
}
