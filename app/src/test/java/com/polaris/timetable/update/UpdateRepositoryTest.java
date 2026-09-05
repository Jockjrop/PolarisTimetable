package com.polaris.timetable.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

/**
 * 清单网络层测试：状态码分类、重定向边界（5 次成功 / 第 6 次拒绝）、
 * 空 Location、相对重定向、HTTP 重定向、超大响应透传 INVALID_METADATA、超时映射。
 * 全部使用注入的假连接，不发起真实网络请求。
 */
public class UpdateRepositoryTest {

    private static final String MANIFEST_URL =
            "https://github.com/Jockjrop/PolarisTimetable/releases/latest/download/latest.json";

    private static final class FakeConnection extends HttpURLConnection {
        private final int responseCode;
        private final String location;
        private final byte[] body;

        FakeConnection(URL url, int responseCode, String location, byte[] body) {
            super(url);
            this.responseCode = responseCode;
            this.location = location;
            this.body = body;
        }

        @Override
        public void connect() {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public String getHeaderField(String name) {
            return "Location".equals(name) ? location : null;
        }

        @Override
        public long getContentLengthLong() {
            return body == null ? -1L : body.length;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(body == null ? new byte[0] : body);
        }
    }

    private interface ConnectionSpec {
        HttpURLConnection open(URL url, int callIndex);
    }

    private UpdateRepository repository(final ConnectionSpec spec) {
        return new UpdateRepository((URL url) -> spec.open(url, 0));
    }

    private String validManifestBody() {
        return "{\"schemaVersion\":1,\"channel\":\"stable\","
                + "\"packageName\":\"com.polaris.timetable\",\"versionCode\":12700,"
                + "\"versionName\":\"1.27.0\",\"minSdk\":23,\"minSupportedVersionCode\":12601,"
                + "\"publishedAt\":\"2026-09-10T12:00:00Z\","
                + "\"apk\":{\"fileName\":\"Polaris-1.27.0-release.apk\","
                + "\"url\":\"https://github.com/x/releases/download/v1.27.0/Polaris-1.27.0-release.apk\","
                + "\"size\":16,\"sha256\":\""
                + "5c99f983378f6c9d5a4ed50670f8e64207075b5051825c922ad98af9f2112c24"
                + "\"},"
                + "\"releaseNotes\":[\"n\"],"
                + "\"releaseNotesUrl\":\"https://github.com/x/releases/tag/v1.27.0\","
                + "\"required\":false}";
    }

    private String fetch(UpdateRepository repo) throws UpdateRepository.FetchException {
        return repo.fetchManifest(MANIFEST_URL, "1.27.0");
    }

    private UpdateRepository.FetchException fetchFailure(UpdateRepository repo) {
        try {
            fetch(repo);
            fail("expected FetchException");
        } catch (UpdateRepository.FetchException exception) {
            return exception;
        }
        throw new AssertionError("unreachable");
    }

    @Test
    public void http200ReturnsBody() throws Exception {
        UpdateRepository repo = repository((url, index) ->
                new FakeConnection(url, 200, null, validManifestBody().getBytes()));
        assertEquals(validManifestBody(), fetch(repo));
    }

    @Test
    public void httpErrorsMapToHttpError() {
        for (int code : new int[]{404, 429, 500, 503}) {
            UpdateRepository repo = repository((url, index) ->
                    new FakeConnection(url, code, null, new byte[0]));
            assertSame(UpdateError.HTTP, fetchFailure(repo).error);
        }
    }

    @Test
    public void exactlyFiveRedirectsSucceed() throws Exception {
        final int[] calls = {0};
        UpdateRepository repo = new UpdateRepository((URL url) -> {
            calls[0]++;
            if (calls[0] <= 5) {
                return new FakeConnection(url, 302,
                        "https://github.com/hop" + calls[0] + "/latest.json", null);
            }
            return new FakeConnection(url, 200, null, validManifestBody().getBytes());
        });
        assertEquals(validManifestBody(), fetch(repo));
        assertEquals(6, calls[0]);
    }

    @Test
    public void sixthRedirectIsRejected() {
        UpdateRepository repo = repository((url, index) ->
                new FakeConnection(url, 302,
                        "https://github.com/hop" + System.nanoTime() + "/latest.json", null));
        assertSame(UpdateError.NETWORK, fetchFailure(repo).error);
    }

    @Test
    public void emptyLocationIsRejected() {
        UpdateRepository repo = repository((url, index) ->
                new FakeConnection(url, 302, null, null));
        assertSame(UpdateError.NETWORK, fetchFailure(repo).error);
    }

    @Test
    public void relativeRedirectIsRejected() {
        UpdateRepository repo = repository((url, index) ->
                new FakeConnection(url, 302, "/latest.json", null));
        assertSame(UpdateError.NETWORK, fetchFailure(repo).error);
    }

    @Test
    public void plainHttpRedirectIsRejected() {
        UpdateRepository repo = repository((url, index) ->
                new FakeConnection(url, 302,
                        "http://objects.githubusercontent.com/latest.json", null));
        assertSame(UpdateError.NETWORK, fetchFailure(repo).error);
    }

    @Test
    public void redirectNonAllowedHostIsRejected() {
        UpdateRepository repo = repository((url, index) ->
                new FakeConnection(url, 302,
                        "https://evil.example.com/latest.json", null));
        assertSame(UpdateError.NETWORK, fetchFailure(repo).error);
    }

    @Test
    public void oversizedBodyIsRejectedAsInvalidMetadata() {
        // 超过 256 KiB 的响应必须透传 INVALID_METADATA，不得被重映射为 NETWORK。
        StringBuilder giant = new StringBuilder("{");
        while (giant.length() <= UpdateJsonParser.MAX_MANIFEST_BYTES + 10) {
            giant.append(" ");
        }
        UpdateRepository repo = repository((url, index) ->
                new FakeConnection(url, 200, null, giant.toString().getBytes()));
        assertSame(UpdateError.INVALID_METADATA, fetchFailure(repo).error);
    }

    @Test
    public void factoryTimeoutMapsToTimeout() {
        UpdateRepository repo = new UpdateRepository((URL url) -> {
            throw new SocketTimeoutException("fake");
        });
        assertSame(UpdateError.TIMEOUT, fetchFailure(repo).error);
    }

    @Test
    public void factoryIoFailureMapsToNetwork() {
        UpdateRepository repo = new UpdateRepository((URL url) -> {
            throw new java.io.IOException("fake dns");
        });
        assertSame(UpdateError.NETWORK, fetchFailure(repo).error);
    }

    @Test
    public void manifestUrlIsHttpsAndAllowed() throws Exception {
        assertTrue(UpdateJsonParser.isAllowedHost(new URL(MANIFEST_URL).getHost()));
        assertTrue(UpdateJsonParser.isAllowedHost("gitee.com"));
        assertTrue(UpdateJsonParser.isAllowedHost("foruda.gitee.com"));
        // 触发一次 day 查询参数拼接（缓存穿透），确保 URL 仍合法。
        UpdateRepository repo = repository((url, index) ->
                new FakeConnection(url, 200, null, validManifestBody().getBytes()));
        assertEquals(validManifestBody(), fetch(repo));
    }

    @Test
    public void giteeReleaseApiFindsPublicLatestJsonAsset() throws Exception {
        String assetUrl = "https://gitee.com/Jockjrop/polaris-course-schedule/"
                + "releases/download/v1.27.0/latest.json";
        String release = "{\"tag_name\":\"v1.27.0\",\"prerelease\":false,"
                + "\"assets\":[{\"name\":\"latest.json\","
                + "\"browser_download_url\":\"" + assetUrl + "\"}]}";
        UpdateRepository repo = new UpdateRepository(url -> {
            byte[] body = url.getPath().endsWith("/releases/latest")
                    ? release.getBytes() : validManifestBody().getBytes();
            return new FakeConnection(url, 200, null, body);
        });
        assertEquals(validManifestBody(), repo.fetchGiteeManifest(
                UpdateCoordinator.GITEE_LATEST_RELEASE_URL, "1.27.0"));
    }

    @Test
    public void giteeReleaseWithoutLatestJsonIsInvalidMetadata() {
        String release = "{\"tag_name\":\"v1.27.0\",\"prerelease\":false,"
                + "\"assets\":[]}";
        UpdateRepository repo = new UpdateRepository(url ->
                new FakeConnection(url, 200, null, release.getBytes()));
        try {
            repo.fetchGiteeManifest(UpdateCoordinator.GITEE_LATEST_RELEASE_URL, "1.27.0");
            fail("expected FetchException");
        } catch (UpdateRepository.FetchException exception) {
            assertSame(UpdateError.INVALID_METADATA, exception.error);
        }
    }
}
