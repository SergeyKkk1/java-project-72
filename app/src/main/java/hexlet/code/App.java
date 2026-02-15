package hexlet.code;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class App {
    private static final String JDBC_DATABASE_URL_ENV = "JDBC_DATABASE_URL";
    private static final String PORT_ENV = "PORT";
    private static final String DEFAULT_PORT = "7000";
    private static final String H2_JDBC_URL = "jdbc:h2:mem:project;DB_CLOSE_DELAY=-1";
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_USERNAME = "sa";
    private static final String H2_PASSWORD = "";
    private static final int DB_MAX_POOL_SIZE = 10;
    private static final int DB_MIN_IDLE = 1;
    private static final String ROOT_PATH = "/";
    private static final String ROOT_RESPONSE = "Hello World";

    private static HikariDataSource dataSource;

    private App() {
    }

    public static Javalin getApp() {
        getDataSource();
        var app = Javalin.create();
        app.get(ROOT_PATH, ctx -> ctx.result(ROOT_RESPONSE));
        return app;
    }

    public static HikariDataSource getDataSource() {
        if (dataSource == null) {
            var config = new HikariConfig();
            var databaseUrl = System.getenv(JDBC_DATABASE_URL_ENV);
            if (databaseUrl == null || databaseUrl.isBlank()) {
                config.setJdbcUrl(H2_JDBC_URL);
                config.setDriverClassName(H2_DRIVER);
                config.setUsername(H2_USERNAME);
                config.setPassword(H2_PASSWORD);
            } else {
                config.setJdbcUrl(databaseUrl);
            }
            config.setMaximumPoolSize(DB_MAX_POOL_SIZE);
            config.setMinimumIdle(DB_MIN_IDLE);
            dataSource = new HikariDataSource(config);
            Database.applyMigrations(dataSource);
        }
        return dataSource;
    }

    public static void main(String[] args) {
        var app = getApp();
        var port = Integer.parseInt(System.getenv().getOrDefault(PORT_ENV, DEFAULT_PORT));
        app.start(port);
    }
}
