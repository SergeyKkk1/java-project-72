package hexlet.code.controller;

import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import hexlet.code.repository.UrlCheckRepository;
import hexlet.code.repository.UrlRepository;
import io.javalin.http.Context;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
public final class UrlController {
    private static final String FORM_URL_PARAM = "url";
    private static final String FLASH_KEY = "flash";
    private static final String INVALID_URL_MESSAGE = "Некорректный URL";
    private static final String URL_ALREADY_EXISTS_MESSAGE = "Страница уже существует";
    private static final String URL_CREATED_MESSAGE = "Страница успешно добавлена";
    private static final String URL_CHECKED_MESSAGE = "Страница успешно проверена";
    private static final String URL_CHECK_FAILED_MESSAGE = "Не удалось проверить страницу";
    private static final String ROOT_PATH = "/";
    private static final String URLS_PATH = "/urls";
    private static final String URLS_LIST_TEMPLATE = "urls/index.jte";
    private static final String URLS_SHOW_TEMPLATE = "urls/show.jte";
    private static final String ROOT_TEMPLATE = "index.jte";
    private static final String URLS_KEY = "urls";
    private static final String URL_KEY = "url";
    private static final String CHECKS_KEY = "checks";
    private static final String PATH_PARAM_ID = "id";
    private static final String PAGE_NOT_FOUND_MESSAGE = "Page not found";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    private static final long DEFAULT_URL_ID = 0L;
    private static final int URL_DEFAULT_PORT = -1;
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern H1_PATTERN = Pattern.compile("(?is)<h1[^>]*>(.*?)</h1>");
    private static final Pattern META_TAG_PATTERN = Pattern.compile("(?is)<meta\\s+[^>]*>");
    private static final Pattern HTML_TAGS_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final UrlRepository urlRepository;
    private final UrlCheckRepository urlCheckRepository;

    public UrlController(DataSource dataSource) {
        this.urlRepository = new UrlRepository(dataSource);
        this.urlCheckRepository = new UrlCheckRepository(dataSource);
    }

    public void index(Context ctx) {
        ctx.render(ROOT_TEMPLATE, buildModel(ctx));
    }

    public void create(Context ctx) {
        var inputUrl = ctx.formParam(FORM_URL_PARAM);
        var normalizedUrl = normalizeUrl(inputUrl);
        if (normalizedUrl.isEmpty()) {
            log.debug("Invalid URL: {}", inputUrl);
            ctx.sessionAttribute(FLASH_KEY, INVALID_URL_MESSAGE);
            ctx.redirect(ROOT_PATH);
            return;
        }

        var urlName = normalizedUrl.get();

        try {
            var existingUrl = urlRepository.findByName(urlName);
            if (existingUrl.isPresent()) {
                ctx.sessionAttribute(FLASH_KEY, URL_ALREADY_EXISTS_MESSAGE);
                ctx.redirect(URLS_PATH + "/" + existingUrl.get().getId());
                return;
            }

            var url = new Url();
            url.setName(urlName);
            var savedUrl = urlRepository.save(url);

            ctx.sessionAttribute(FLASH_KEY, URL_CREATED_MESSAGE);
            ctx.redirect(URLS_PATH + "/" + savedUrl.getId());
        } catch (SQLException e) {
            log.error("Failed to create URL {}", urlName, e);
            ctx.status(STATUS_INTERNAL_SERVER_ERROR).result(INTERNAL_SERVER_ERROR_MESSAGE);
        }
    }

    public void indexUrls(Context ctx) {
        try {
            var model = buildModel(ctx);
            model.put(URLS_KEY, urlRepository.getEntities());
            ctx.render(URLS_LIST_TEMPLATE, model);
        } catch (SQLException e) {
            log.error("Failed to get URLs list", e);
            ctx.status(STATUS_INTERNAL_SERVER_ERROR).result(INTERNAL_SERVER_ERROR_MESSAGE);
        }
    }

    public void show(Context ctx) {
        var id = ctx.pathParamAsClass(PATH_PARAM_ID, Long.class).getOrDefault(DEFAULT_URL_ID);
        try {
            var url = urlRepository.find(id);
            if (url.isEmpty()) {
                ctx.status(STATUS_NOT_FOUND).result(PAGE_NOT_FOUND_MESSAGE);
                return;
            }

            var model = buildModel(ctx);
            model.put(URL_KEY, url.get());
            model.put(CHECKS_KEY, urlCheckRepository.findByUrlId(id));
            ctx.render(URLS_SHOW_TEMPLATE, model);
        } catch (SQLException e) {
            log.error("Failed to get URL with id={}", id, e);
            ctx.status(STATUS_INTERNAL_SERVER_ERROR).result(INTERNAL_SERVER_ERROR_MESSAGE);
        }
    }

    public void createCheck(Context ctx) {
        var id = ctx.pathParamAsClass(PATH_PARAM_ID, Long.class).getOrDefault(DEFAULT_URL_ID);
        try {
            var url = urlRepository.find(id);
            if (url.isEmpty()) {
                ctx.status(STATUS_NOT_FOUND).result(PAGE_NOT_FOUND_MESSAGE);
                return;
            }

            try {
                var response = Unirest.get(url.get().getName()).asString();
                var urlCheck = new UrlCheck();
                urlCheck.setUrlId(id);
                urlCheck.setStatusCode(response.getStatus());
                urlCheck.setTitle(extractTextByPattern(TITLE_PATTERN, response.getBody()).orElse(null));
                urlCheck.setH1(extractTextByPattern(H1_PATTERN, response.getBody()).orElse(null));
                urlCheck.setDescription(extractMetaDescription(response.getBody()).orElse(null));
                urlCheckRepository.save(urlCheck);
                ctx.sessionAttribute(FLASH_KEY, URL_CHECKED_MESSAGE);
            } catch (UnirestException e) {
                log.error("Failed to check URL {}", url.get().getName(), e);
                ctx.sessionAttribute(FLASH_KEY, URL_CHECK_FAILED_MESSAGE);
            }

            ctx.redirect(URLS_PATH + "/" + id);
        } catch (SQLException e) {
            log.error("Failed to save URL check for id={}", id, e);
            ctx.status(STATUS_INTERNAL_SERVER_ERROR).result(INTERNAL_SERVER_ERROR_MESSAGE);
        }
    }

    private Optional<String> normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            var uri = new URI(rawUrl.trim());
            var parsedUrl = uri.toURL();

            var scheme = parsedUrl.getProtocol();
            var host = parsedUrl.getHost();

            if (scheme == null || scheme.isBlank() || host == null || host.isBlank()) {
                return Optional.empty();
            }

            var normalizedUrl = new StringBuilder()
                .append(scheme)
                .append("://")
                .append(host);

            if (parsedUrl.getPort() != URL_DEFAULT_PORT) {
                normalizedUrl.append(":").append(parsedUrl.getPort());
            }

            return Optional.of(normalizedUrl.toString());
        } catch (URISyntaxException | IllegalArgumentException | java.net.MalformedURLException e) {
            return Optional.empty();
        }
    }

    private Optional<String> extractTextByPattern(Pattern pattern, String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        var matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return Optional.empty();
        }

        var noTagsText = HTML_TAGS_PATTERN.matcher(matcher.group(1)).replaceAll(" ");
        return Optional.ofNullable(sanitizeText(noTagsText));
    }

    private Optional<String> extractMetaDescription(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        var metaMatcher = META_TAG_PATTERN.matcher(html);
        while (metaMatcher.find()) {
            var metaTag = metaMatcher.group();
            var name = extractAttribute(metaTag, "name");
            if (name.isPresent() && "description".equalsIgnoreCase(name.get())) {
                return extractAttribute(metaTag, "content").map(this::sanitizeText);
            }
        }

        return Optional.empty();
    }

    private Optional<String> extractAttribute(String tag, String attribute) {
        var quotedAttribute = Pattern.compile("(?i)\\b" + Pattern.quote(attribute) + "\\s*=\\s*(['\"])(.*?)\\1");
        var quotedAttributeMatcher = quotedAttribute.matcher(tag);
        if (quotedAttributeMatcher.find()) {
            return Optional.ofNullable(quotedAttributeMatcher.group(2).trim());
        }

        var unquotedAttribute = Pattern.compile("(?i)\\b" + Pattern.quote(attribute) + "\\s*=\\s*([^\\s\"'>/]+)");
        var unquotedAttributeMatcher = unquotedAttribute.matcher(tag);
        if (unquotedAttributeMatcher.find()) {
            return Optional.ofNullable(unquotedAttributeMatcher.group(1).trim());
        }

        return Optional.empty();
    }

    private String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        var normalizedWhitespaceText = WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
        return normalizedWhitespaceText.isBlank() ? null : normalizedWhitespaceText;
    }

    private HashMap<String, Object> buildModel(Context ctx) {
        var model = new HashMap<String, Object>();
        model.put("flash", ctx.consumeSessionAttribute(FLASH_KEY));
        return model;
    }
}
