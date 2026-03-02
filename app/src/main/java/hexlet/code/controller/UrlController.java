package hexlet.code.controller;

import hexlet.code.model.Url;
import hexlet.code.model.UrlCheck;
import hexlet.code.repository.UrlCheckRepository;
import hexlet.code.repository.UrlRepository;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.sql.DataSource;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public final class UrlController {
    private static final String FLASH_KEY = "flash";
    private static final String URLS_PATH = "/urls";
    private static final String PATH_PARAM_ID = "id";
    private static final String PAGE_NOT_FOUND_MESSAGE = "Page not found";
    private static final long DEFAULT_URL_ID = 0L;

    private final UrlRepository urlRepository;
    private final UrlCheckRepository urlCheckRepository;

    public UrlController(DataSource dataSource) {
        this.urlRepository = new UrlRepository(dataSource);
        this.urlCheckRepository = new UrlCheckRepository(dataSource);
    }

    public void index(Context ctx) {
        ctx.render("index.jte", buildModel(ctx));
    }

    public void create(Context ctx) throws MalformedURLException, URISyntaxException {
        var inputUrl = ctx.formParam("url");
        normalizeUrl(inputUrl).ifPresentOrElse(urlName -> {
            try {
                var existingUrl = urlRepository.findByName(urlName);
                if (existingUrl.isPresent()) {
                    ctx.sessionAttribute(FLASH_KEY, "Страница уже существует");
                    ctx.redirect(URLS_PATH + "/" + existingUrl.get().getId());
                    return;
                }

                var url = new Url();
                url.setName(urlName);
                var savedUrl = urlRepository.save(url);
                ctx.sessionAttribute(FLASH_KEY, "Страница успешно добавлена");
                ctx.redirect(URLS_PATH + "/" + savedUrl.getId());
            } catch (SQLException e) {
                log.error("Failed to create URL {}", urlName, e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Internal Server Error");
            }
        }, () -> {
            log.debug("Invalid URL: {}", inputUrl);
            ctx.sessionAttribute(FLASH_KEY, "Некорректный URL");
            ctx.redirect("/");
        });
    }

    public void indexUrls(Context ctx) throws SQLException {
        var model = buildModel(ctx);
        model.put("urls", urlRepository.getEntities());
        ctx.render("urls/index.jte", model);
    }

    public void show(Context ctx) throws SQLException {
        var id = ctx.pathParamAsClass(PATH_PARAM_ID, Long.class).getOrDefault(DEFAULT_URL_ID);
        var url = urlRepository.find(id);
        if (url.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result(PAGE_NOT_FOUND_MESSAGE);
            return;
        }

        var model = buildModel(ctx);
        model.put("url", url.get());
        model.put("checks", urlCheckRepository.findByUrlId(id));
        ctx.render("urls/show.jte", model);
    }

    public void createCheck(Context ctx) throws SQLException {
        var id = ctx.pathParamAsClass(PATH_PARAM_ID, Long.class).getOrDefault(DEFAULT_URL_ID);
        var url = urlRepository.find(id);
        if (url.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result(PAGE_NOT_FOUND_MESSAGE);
            return;
        }

        try {
            var response = Unirest.get(url.get().getName()).asString();
            var urlCheck = new UrlCheck();
            urlCheck.setUrlId(id);
            urlCheck.setStatusCode(response.getStatus());
            fillSeoData(urlCheck, response.getBody());
            urlCheckRepository.save(urlCheck);
            ctx.sessionAttribute(FLASH_KEY, "Страница успешно проверена");
        } catch (UnirestException e) {
            log.error("Failed to check URL {}", url.get().getName(), e);
            ctx.sessionAttribute(FLASH_KEY, "Не удалось проверить страницу");
        }

        ctx.redirect(URLS_PATH + "/" + id);
    }

    private Optional<String> normalizeUrl(String rawUrl) throws URISyntaxException, MalformedURLException {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

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
    }

    private void fillSeoData(UrlCheck urlCheck, String html) {
        if (html == null || html.isBlank()) {
            return;
        }

        Document document = Jsoup.parse(html);
        urlCheck.setTitle(sanitizeText(document.title()));

        var h1Element = document.selectFirst("h1");
        if (h1Element != null) {
            urlCheck.setH1(sanitizeText(h1Element.text()));
        }

        var metaDescriptionElement = document.selectFirst("meta[name=description]");
        if (metaDescriptionElement != null) {
            urlCheck.setDescription(sanitizeText(metaDescriptionElement.attr("content")));
        }
    }

    private String sanitizeText(String text) {
        if (text == null) {
            return null;
        }

        var normalizedWhitespaceText = text.trim().replaceAll("\\s+", " ");
        return normalizedWhitespaceText.isBlank() ? null : normalizedWhitespaceText;
    }

    private Map<String, Object> buildModel(Context ctx) {
        var model = new HashMap<String, Object>();
        model.put("flash", ctx.consumeSessionAttribute(FLASH_KEY));
        return model;
    }
}
