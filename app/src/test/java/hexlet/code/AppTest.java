package hexlet.code;

import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import hexlet.code.repository.UrlCheckRepository;
import hexlet.code.repository.UrlRepository;
import io.javalin.Javalin;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppTest {
    private static final String APP_ENV_PROPERTY = "app.env";
    private static final String TEST_ENV = "test";
    private static final String BASE_URL_TEMPLATE = "http://localhost:";
    private static final String ROOT_PATH = "/";
    private static final String URLS_PATH = "/urls";
    private static final String URLS_CHECKS_PATH = "/checks";
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String FORM_URL_PARAM_PREFIX = "url=";
    private static final String URL_WITH_PATH = "https://some-domain.org/example/path";
    private static final String NORMALIZED_URL = "https://some-domain.org";
    private static final String URL_WITH_PORT_AND_PATH = "https://some-domain.org:8080/example/path";
    private static final String NORMALIZED_URL_WITH_PORT = "https://some-domain.org:8080";
    private static final String SECOND_URL = "https://second.example.com";
    private static final String INVALID_URL = "ht tp://broken";
    private static final String ADD_SUCCESS_MESSAGE = "Страница успешно добавлена";
    private static final String URL_ALREADY_EXISTS_MESSAGE = "Страница уже существует";
    private static final String INVALID_URL_MESSAGE = "Некорректный URL";
    private static final String URL_CHECKED_MESSAGE = "Страница успешно проверена";
    private static final String URL_CHECK_FAILED_MESSAGE = "Не удалось проверить страницу";
    private static final String URL_PARAM_NAME = "name=\"url\"";
    private static final String URLS_TITLE = "Сайты";
    private static final String ROOT_PAGE_TITLE = "Анализатор страниц";
    private static final String URL_DETAILS_TABLE_TEST_ATTR = "data-test=\"url\"";
    private static final String URL_CHECKS_TABLE_TEST_ATTR = "data-test=\"checks\"";
    private static final String ELLIPSIS = "...";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String URLS_SHOW_PATH_PREFIX = "/urls/";
    private static final String MOCK_SERVER_HOST_URL = "http://localhost:";
    private static final String MOCK_HTML_TITLE = "Mock title";
    private static final String MOCK_HTML_H1 = "Mock h1";
    private static final String MOCK_HTML_DESCRIPTION = "Mock description";
    private static final String MOCK_HTML_BODY = """
        <html>
          <head>
            <title>Mock title</title>
            <meta name="description" content="Mock description">
          </head>
          <body>
            <h1>Mock h1</h1>
          </body>
        </html>
        """;
    private static final LocalDateTime FIRST_CHECK_DATE = LocalDateTime.parse("2026-01-01T10:00:00");
    private static final LocalDateTime SECOND_CHECK_DATE = LocalDateTime.parse("2026-01-01T11:00:00");
    private static final long UNKNOWN_URL_ID = 999_999L;
    private static final int SEO_TEXT_LIMIT = 255;
    private static final int TITLE_OVERFLOW_EXTRA = 10;
    private static final int H1_OVERFLOW_EXTRA = 20;
    private static final int DESCRIPTION_OVERFLOW_EXTRA = 30;
    private static final int UNAVAILABLE_PORT = 1;
    private static final int RANDOM_PORT = 0;
    private static final int STATUS_OK = 200;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_CREATED = 201;
    private static final int STATUS_NO_CONTENT = 204;

    private Javalin app;
    private HttpClient client;
    private String baseUrl;
    private UrlRepository urlRepository;
    private UrlCheckRepository urlCheckRepository;
    private MockWebServer mockWebServer;

    @BeforeAll
    static void beforeAll() {
        System.setProperty(APP_ENV_PROPERTY, TEST_ENV);
        App.resetDataSource();
    }

    @AfterAll
    static void afterAll() {
        App.resetDataSource();
        System.clearProperty(APP_ENV_PROPERTY);
    }

    @BeforeEach
    void setUp() throws SQLException {
        App.resetDataSource();
        app = App.getApp();
        app.start(RANDOM_PORT);
        baseUrl = BASE_URL_TEMPLATE + app.port();
        client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        urlRepository = new UrlRepository(App.getDataSource());
        urlCheckRepository = new UrlCheckRepository(App.getDataSource());
        clearDatabase();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (app != null) {
            app.stop();
        }
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    void testRootPageContainsUrlForm() throws Exception {
        var response = sendGetRequest(ROOT_PATH);

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.body().contains(ROOT_PAGE_TITLE));
        assertTrue(response.body().contains(URL_PARAM_NAME));
    }

    @Test
    void testCreateUrlSavesNormalizedEntityAndShowsItOnPage() throws Exception {
        var response = sendCreateUrlRequest(URL_WITH_PATH);
        var savedUrl = urlRepository.findByName(NORMALIZED_URL).orElseThrow();

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.uri().getPath().startsWith(URLS_SHOW_PATH_PREFIX));
        assertTrue(response.body().contains(ADD_SUCCESS_MESSAGE));
        assertTrue(response.body().contains(savedUrl.getName()));
        assertTrue(response.body().contains(savedUrl.getId().toString()));
    }

    @Test
    void testCreateUrlKeepsExplicitPort() throws Exception {
        sendCreateUrlRequest(URL_WITH_PORT_AND_PATH);

        var savedUrl = urlRepository.findByName(NORMALIZED_URL_WITH_PORT);
        assertTrue(savedUrl.isPresent());
    }

    @Test
    void testCreateInvalidUrlShowsFlashMessage() throws Exception {
        var response = sendCreateUrlRequest(INVALID_URL);

        assertEquals(STATUS_OK, response.statusCode());
        assertEquals(ROOT_PATH, response.uri().getPath());
        assertTrue(response.body().contains(INVALID_URL_MESSAGE));
        assertTrue(urlRepository.getEntities().isEmpty());
    }

    @Test
    void testCreateDuplicateUrlShowsFlashMessage() throws Exception {
        sendCreateUrlRequest(URL_WITH_PATH);
        var response = sendCreateUrlRequest(URL_WITH_PATH);

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.body().contains(URL_ALREADY_EXISTS_MESSAGE));
        assertEquals(1L, urlRepository.getEntities().size());
    }

    @Test
    void testUrlsPageShowsAllStoredUrls() throws Exception {
        saveUrl(NORMALIZED_URL);
        saveUrl(SECOND_URL);

        var response = sendGetRequest(URLS_PATH);

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.body().contains(URLS_TITLE));
        assertTrue(response.body().contains(NORMALIZED_URL));
        assertTrue(response.body().contains(SECOND_URL));
    }

    @Test
    void testUrlPageShowsStoredUrlById() throws Exception {
        var savedUrl = saveUrl(NORMALIZED_URL);
        var savedCheck = saveUrlCheck(savedUrl.getId(), STATUS_OK, FIRST_CHECK_DATE);
        var response = sendGetRequest(URLS_PATH + "/" + savedUrl.getId());

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.body().contains(URL_DETAILS_TABLE_TEST_ATTR));
        assertTrue(response.body().contains(URL_CHECKS_TABLE_TEST_ATTR));
        assertTrue(response.body().contains(savedUrl.getName()));
        assertTrue(response.body().contains(savedUrl.getId().toString()));
        assertTrue(response.body().contains(savedCheck.getId().toString()));
        assertTrue(response.body().contains(savedCheck.getCreatedAt().toString()));
    }

    @Test
    void testUrlPageReturnsNotFoundForUnknownId() throws Exception {
        var response = sendGetRequest(URLS_PATH + "/" + UNKNOWN_URL_ID);

        assertEquals(STATUS_NOT_FOUND, response.statusCode());
    }

    @Test
    void testCreateCheckSavesPageAvailabilityInfo() throws Exception {
        startMockWebServer();
        mockWebServer.enqueue(new MockResponse().setResponseCode(STATUS_OK).setBody(MOCK_HTML_BODY));
        var savedUrl = saveUrl(MOCK_SERVER_HOST_URL + mockWebServer.getPort());

        var response = sendCreateCheckRequest(savedUrl.getId());
        var checks = urlCheckRepository.findByUrlId(savedUrl.getId());
        var savedCheck = checks.getFirst();

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.uri().getPath().startsWith(URLS_SHOW_PATH_PREFIX));
        assertTrue(response.body().contains(URL_CHECKED_MESSAGE));
        assertTrue(response.body().contains(MOCK_HTML_TITLE));
        assertTrue(response.body().contains(MOCK_HTML_H1));
        assertTrue(response.body().contains(MOCK_HTML_DESCRIPTION));
        assertEquals(1, checks.size());
        assertEquals(STATUS_OK, savedCheck.getStatusCode());
        assertEquals(MOCK_HTML_TITLE, savedCheck.getTitle());
        assertEquals(MOCK_HTML_H1, savedCheck.getH1());
        assertEquals(MOCK_HTML_DESCRIPTION, savedCheck.getDescription());
    }

    @Test
    void testCreateCheckTruncatesSeoFieldsToDatabaseLimits() throws Exception {
        var longTitle = "t".repeat(SEO_TEXT_LIMIT + TITLE_OVERFLOW_EXTRA);
        var longH1 = "h".repeat(SEO_TEXT_LIMIT + H1_OVERFLOW_EXTRA);
        var longDescription = "d".repeat(SEO_TEXT_LIMIT + DESCRIPTION_OVERFLOW_EXTRA);
        var html = """
            <html>
              <head>
                <title>%s</title>
                <meta name="description" content="%s">
              </head>
              <body>
                <h1>%s</h1>
              </body>
            </html>
            """.formatted(longTitle, longDescription, longH1);

        startMockWebServer();
        mockWebServer.enqueue(new MockResponse().setResponseCode(STATUS_OK).setBody(html));
        var savedUrl = saveUrl(MOCK_SERVER_HOST_URL + mockWebServer.getPort());

        var response = sendCreateCheckRequest(savedUrl.getId());
        var savedCheck = urlCheckRepository.findByUrlId(savedUrl.getId()).getFirst();
        var expectedTitle = longTitle.substring(0, SEO_TEXT_LIMIT - ELLIPSIS.length()) + ELLIPSIS;
        var expectedH1 = longH1.substring(0, SEO_TEXT_LIMIT - ELLIPSIS.length()) + ELLIPSIS;
        var expectedDescription = longDescription.substring(0, SEO_TEXT_LIMIT - ELLIPSIS.length()) + ELLIPSIS;

        assertEquals(STATUS_OK, response.statusCode());
        assertEquals(expectedTitle, savedCheck.getTitle());
        assertEquals(expectedH1, savedCheck.getH1());
        assertEquals(expectedDescription, savedCheck.getDescription());
    }

    @Test
    void testCreateCheckReturnsNotFoundForUnknownUrl() throws Exception {
        var response = sendCreateCheckRequest(UNKNOWN_URL_ID);

        assertEquals(STATUS_NOT_FOUND, response.statusCode());
    }

    @Test
    void testCreateCheckShowsFailureFlashWhenSiteUnavailable() throws Exception {
        var savedUrl = saveUrl(MOCK_SERVER_HOST_URL + UNAVAILABLE_PORT);
        var response = sendCreateCheckRequest(savedUrl.getId());

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.body().contains(URL_CHECK_FAILED_MESSAGE));
        assertTrue(urlCheckRepository.findByUrlId(savedUrl.getId()).isEmpty());
    }

    @Test
    void testCreateReturnsInternalServerErrorOnDatabaseFailure() throws Exception {
        dropTable("urls");
        var response = sendCreateUrlRequest(URL_WITH_PATH);

        assertEquals(STATUS_INTERNAL_SERVER_ERROR, response.statusCode());
    }

    @Test
    void testIndexUrlsReturnsInternalServerErrorOnDatabaseFailure() throws Exception {
        dropTable("url_checks");
        var response = sendGetRequest(URLS_PATH);

        assertEquals(STATUS_INTERNAL_SERVER_ERROR, response.statusCode());
    }

    @Test
    void testShowReturnsInternalServerErrorOnDatabaseFailure() throws Exception {
        var savedUrl = saveUrl(NORMALIZED_URL);
        dropTable("url_checks");
        var response = sendGetRequest(URLS_PATH + "/" + savedUrl.getId());

        assertEquals(STATUS_INTERNAL_SERVER_ERROR, response.statusCode());
    }

    @Test
    void testCreateCheckReturnsInternalServerErrorOnDatabaseFailure() throws Exception {
        startMockWebServer();
        mockWebServer.enqueue(new MockResponse().setResponseCode(STATUS_OK).setBody(MOCK_HTML_BODY));
        var savedUrl = saveUrl(MOCK_SERVER_HOST_URL + mockWebServer.getPort());
        dropTable("url_checks");
        var response = sendCreateCheckRequest(savedUrl.getId());

        assertEquals(STATUS_INTERNAL_SERVER_ERROR, response.statusCode());
    }

    @Test
    void testUrlsPageShowsLastCheckDateAndStatusCode() throws Exception {
        var savedUrl = saveUrl(NORMALIZED_URL);
        saveUrlCheck(savedUrl.getId(), STATUS_CREATED, FIRST_CHECK_DATE);
        saveUrlCheck(savedUrl.getId(), STATUS_NO_CONTENT, SECOND_CHECK_DATE);
        var response = sendGetRequest(URLS_PATH);

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.body().contains(SECOND_CHECK_DATE.toString()));
        assertTrue(response.body().contains(String.valueOf(STATUS_NO_CONTENT)));
    }

    private void startMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    private HttpResponse<String> sendGetRequest(String path) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendCreateUrlRequest(String rawUrl) throws Exception {
        var encodedUrl = URLEncoder.encode(rawUrl, StandardCharsets.UTF_8);
        var requestBody = FORM_URL_PARAM_PREFIX + encodedUrl;
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + URLS_PATH))
            .header(CONTENT_TYPE_HEADER, FORM_CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendCreateCheckRequest(Long urlId) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + URLS_PATH + "/" + urlId + URLS_CHECKS_PATH))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Url saveUrl(String name) throws SQLException {
        var url = new Url();
        url.setName(name);
        return urlRepository.save(url);
    }

    private UrlCheck saveUrlCheck(Long urlId, int statusCode, LocalDateTime createdAt) throws SQLException {
        var check = new UrlCheck();
        check.setUrlId(urlId);
        check.setStatusCode(statusCode);
        check.setCreatedAt(createdAt);
        return urlCheckRepository.save(check);
    }

    private void clearDatabase() throws SQLException {
        try (var connection = App.getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM url_checks");
            statement.executeUpdate("DELETE FROM urls");
        }
    }

    private void dropTable(String tableName) throws SQLException {
        try (var connection = App.getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE " + tableName + " CASCADE");
        }
    }
}
