package hexlet.code;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.ResourceCodeResolver;
import hexlet.code.controller.UrlController;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.rendering.template.JavalinJte;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Map;

@Slf4j
public final class App {
    private static final String APP_ENV_PROPERTY = "app.env";
    private static final String TEST_ENV = "test";
    private static final String JDBC_DATABASE_URL_ENV = "JDBC_DATABASE_URL";
    private static final String PORT_ENV = "PORT";
    private static final String DEFAULT_PORT = "7000";
    private static final String H2_JDBC_URL = "jdbc:h2:mem:project;DB_CLOSE_DELAY=-1";
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_USERNAME = "sa";
    private static final String H2_PASSWORD = "";
    private static final int DB_MAX_POOL_SIZE = 10;
    private static final int DB_MIN_IDLE = 1;
    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";
    private static final String INDEX_TEMPLATE = "index.jte";
    private static final String FLASH_KEY = "flash";
    private static final String INVALID_URL_MESSAGE = "Некорректный URL";
    private static final int STATUS_UNPROCESSABLE_ENTITY = 422;

    private static HikariDataSource dataSource;

    private App() {
    }

    public static Javalin getApp() {
        getDataSource();
        var app = Javalin.create(config -> config.fileRenderer(new JavalinJte(createTemplateEngine())));
        app.exception(URISyntaxException.class, App::handleInvalidUrlException);
        app.exception(MalformedURLException.class, App::handleInvalidUrlException);
        app.exception(SQLException.class, (e, ctx) -> {
            log.error("Database error while processing {} {}", ctx.method(), ctx.path(), e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result(INTERNAL_SERVER_ERROR_MESSAGE);
        });
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled exception while processing {} {}", ctx.method(), ctx.path(), e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result(INTERNAL_SERVER_ERROR_MESSAGE);
        });

        var urlController = new UrlController(getDataSource());
        app.get("/", urlController::index);
        app.post("/urls", urlController::create);
        app.get("/urls", urlController::indexUrls);
        app.get("/urls/{id}", urlController::show);
        app.post("/urls/{id}/checks", urlController::createCheck);
        return app;
    }

    public static HikariDataSource getDataSource() {
        if (dataSource == null) {
            var config = new HikariConfig();
            var databaseUrl = System.getenv(JDBC_DATABASE_URL_ENV);
            var appEnv = System.getProperty(APP_ENV_PROPERTY);
            var isTestEnvironment = TEST_ENV.equals(appEnv);
            if (isTestEnvironment || databaseUrl == null || databaseUrl.isBlank()) {
                log.info("Using H2 database");
                config.setJdbcUrl(H2_JDBC_URL);
                config.setDriverClassName(H2_DRIVER);
                config.setUsername(H2_USERNAME);
                config.setPassword(H2_PASSWORD);
            } else {
                log.info("Using PostgreSQL database from {}", JDBC_DATABASE_URL_ENV);
                config.setJdbcUrl(databaseUrl);
                config.setDriverClassName(POSTGRES_DRIVER);
            }
            config.setMaximumPoolSize(DB_MAX_POOL_SIZE);
            config.setMinimumIdle(DB_MIN_IDLE);
            dataSource = new HikariDataSource(config);
            Database.applyMigrations(dataSource);
        }
        return dataSource;
    }

    static void resetDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    public static void main(String[] args) {
        var app = getApp();
        var port = Integer.parseInt(System.getenv().getOrDefault(PORT_ENV, DEFAULT_PORT));
        app.start(port);
    }

    private static void handleInvalidUrlException(Exception e, Context ctx) {
        log.debug("Invalid URL while processing {} {}", ctx.method(), ctx.path(), e);
        ctx.status(STATUS_UNPROCESSABLE_ENTITY).render(INDEX_TEMPLATE, Map.of(FLASH_KEY, INVALID_URL_MESSAGE));
    }

    private static TemplateEngine createTemplateEngine() {
        ClassLoader classLoader = App.class.getClassLoader();
        ResourceCodeResolver codeResolver = new ResourceCodeResolver("templates", classLoader);
        return TemplateEngine.create(codeResolver, ContentType.Html);
    }
}
