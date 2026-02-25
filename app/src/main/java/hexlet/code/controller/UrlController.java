package hexlet.code.controller;

import hexlet.code.model.Url;
import hexlet.code.repository.UrlRepository;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;

@Slf4j
public final class UrlController {
    private static final String FORM_URL_PARAM = "url";
    private static final String FLASH_KEY = "flash";
    private static final String INVALID_URL_MESSAGE = "Некорректный URL";
    private static final String URL_ALREADY_EXISTS_MESSAGE = "Страница уже существует";
    private static final String URL_CREATED_MESSAGE = "Страница успешно добавлена";
    private static final String ROOT_PATH = "/";
    private static final String URLS_PATH = "/urls";
    private static final String URLS_LIST_TEMPLATE = "urls/index.jte";
    private static final String URLS_SHOW_TEMPLATE = "urls/show.jte";
    private static final String ROOT_TEMPLATE = "index.jte";
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;

    private final UrlRepository urlRepository;

    public UrlController(DataSource dataSource) {
        this.urlRepository = new UrlRepository(dataSource);
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
            ctx.status(STATUS_INTERNAL_SERVER_ERROR).result("Internal Server Error");
        }
    }

    public void indexUrls(Context ctx) {
        try {
            var model = buildModel(ctx);
            model.put("urls", urlRepository.getEntities());
            ctx.render(URLS_LIST_TEMPLATE, model);
        } catch (SQLException e) {
            log.error("Failed to get URLs list", e);
            ctx.status(STATUS_INTERNAL_SERVER_ERROR).result("Internal Server Error");
        }
    }

    public void show(Context ctx) {
        var id = ctx.pathParamAsClass("id", Long.class).getOrDefault(0L);
        try {
            var url = urlRepository.find(id);
            if (url.isEmpty()) {
                ctx.status(STATUS_NOT_FOUND).result("Page not found");
                return;
            }

            var model = buildModel(ctx);
            model.put("url", url.get());
            ctx.render(URLS_SHOW_TEMPLATE, model);
        } catch (SQLException e) {
            log.error("Failed to get URL with id={}", id, e);
            ctx.status(STATUS_INTERNAL_SERVER_ERROR).result("Internal Server Error");
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

            if (parsedUrl.getPort() != -1) {
                normalizedUrl.append(":").append(parsedUrl.getPort());
            }

            return Optional.of(normalizedUrl.toString());
        } catch (URISyntaxException | IllegalArgumentException | java.net.MalformedURLException e) {
            return Optional.empty();
        }
    }

    private HashMap<String, Object> buildModel(Context ctx) {
        var model = new HashMap<String, Object>();
        model.put("flash", ctx.consumeSessionAttribute(FLASH_KEY));
        return model;
    }
}
