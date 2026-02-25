package hexlet.code.repository;

import hexlet.code.model.UrlCheck;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class UrlCheckRepository extends BaseRepository {
    private static final int PARAM_URL_ID = 1;
    private static final int PARAM_STATUS_CODE = 2;
    private static final int PARAM_H1 = 3;
    private static final int PARAM_TITLE = 4;
    private static final int PARAM_DESCRIPTION = 5;
    private static final int PARAM_CREATED_AT = 6;
    private static final String SQL_INSERT = """
        INSERT INTO url_checks (url_id, status_code, h1, title, description, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """;
    private static final String SQL_FIND_BY_URL_ID = """
        SELECT id, url_id, status_code, h1, title, description, created_at
        FROM url_checks
        WHERE url_id = ?
        ORDER BY id DESC
        """;

    public UrlCheckRepository(DataSource dataSource) {
        super(dataSource);
    }

    public UrlCheck save(UrlCheck urlCheck) throws SQLException {
        var createdAt = urlCheck.getCreatedAt() == null ? LocalDateTime.now() : urlCheck.getCreatedAt();
        var createdAtTimestamp = Timestamp.valueOf(createdAt);

        try (var connection = getConnection();
             var statement = connection.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(PARAM_URL_ID, urlCheck.getUrlId());
            statement.setInt(PARAM_STATUS_CODE, urlCheck.getStatusCode());
            statement.setString(PARAM_H1, urlCheck.getH1());
            statement.setString(PARAM_TITLE, urlCheck.getTitle());
            statement.setString(PARAM_DESCRIPTION, urlCheck.getDescription());
            statement.setTimestamp(PARAM_CREATED_AT, createdAtTimestamp);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    urlCheck.setId(keys.getLong(1));
                }
            }
        }

        urlCheck.setCreatedAt(createdAt);
        return urlCheck;
    }

    public List<UrlCheck> findByUrlId(Long urlId) throws SQLException {
        var checks = new ArrayList<UrlCheck>();
        try (var connection = getConnection();
             var statement = connection.prepareStatement(SQL_FIND_BY_URL_ID)) {
            statement.setLong(1, urlId);
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    checks.add(buildUrlCheck(resultSet));
                }
            }
        }
        return checks;
    }

    private UrlCheck buildUrlCheck(ResultSet resultSet) throws SQLException {
        var urlCheck = new UrlCheck();
        urlCheck.setId(resultSet.getLong("id"));
        urlCheck.setUrlId(resultSet.getLong("url_id"));
        urlCheck.setStatusCode(resultSet.getInt("status_code"));
        urlCheck.setH1(resultSet.getString("h1"));
        urlCheck.setTitle(resultSet.getString("title"));
        urlCheck.setDescription(resultSet.getString("description"));

        var createdAt = resultSet.getTimestamp("created_at");
        if (createdAt != null) {
            urlCheck.setCreatedAt(createdAt.toLocalDateTime());
        }

        return urlCheck;
    }
}
