package hexlet.code;

import hexlet.code.model.Url;
import hexlet.code.repository.UrlRepository;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppTest {
    private static final String APP_ENV_PROPERTY = "app.env";
    private static final String TEST_ENV = "test";
    private static final String BASE_URL_TEMPLATE = "http://localhost:";
    private static final String ROOT_PATH = "/";
    private static final String URLS_PATH = "/urls";
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
    private static final String URL_PARAM_NAME = "name=\"url\"";
    private static final String URLS_TITLE = "Сайты";
    private static final String ROOT_PAGE_TITLE = "Анализатор страниц";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String URLS_SHOW_PATH_PREFIX = "/urls/";
    private static final long UNKNOWN_URL_ID = 999_999L;
    private static final int RANDOM_PORT = 0;
    private static final int STATUS_OK = 200;
    private static final int STATUS_NOT_FOUND = 404;

    private Javalin app;
    private HttpClient client;
    private String baseUrl;
    private UrlRepository urlRepository;

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
        app = App.getApp();
        app.start(RANDOM_PORT);
        baseUrl = BASE_URL_TEMPLATE + app.port();
        client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        urlRepository = new UrlRepository(App.getDataSource());
        clearUrls();
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.stop();
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
        var response = sendGetRequest(URLS_PATH + "/" + savedUrl.getId());

        assertEquals(STATUS_OK, response.statusCode());
        assertTrue(response.body().contains(savedUrl.getName()));
        assertTrue(response.body().contains(savedUrl.getId().toString()));
    }

    @Test
    void testUrlPageReturnsNotFoundForUnknownId() throws Exception {
        var response = sendGetRequest(URLS_PATH + "/" + UNKNOWN_URL_ID);

        assertEquals(STATUS_NOT_FOUND, response.statusCode());
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

    private Url saveUrl(String name) throws SQLException {
        var url = new Url();
        url.setName(name);
        return urlRepository.save(url);
    }

    private void clearUrls() throws SQLException {
        try (var connection = App.getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM urls");
        }
    }
}
