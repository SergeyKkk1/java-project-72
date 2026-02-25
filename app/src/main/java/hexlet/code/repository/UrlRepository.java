package hexlet.code.repository;

import hexlet.code.model.Url;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UrlRepository extends BaseRepository {
    private static final String SQL_INSERT = "INSERT INTO urls (name, created_at) VALUES (?, ?)";
    private static final String SQL_FIND_BY_ID = "SELECT id, name, created_at FROM urls WHERE id = ?";
    private static final String SQL_FIND_BY_NAME = "SELECT id, name, created_at FROM urls WHERE name = ?";
    private static final String SQL_FIND_ALL = """
        SELECT u.id, u.name, u.created_at,
               c.status_code AS last_check_status_code,
               c.created_at AS last_check_created_at
        FROM urls u
        LEFT JOIN (
            SELECT uc1.url_id, uc1.status_code, uc1.created_at
            FROM url_checks uc1
            JOIN (
                SELECT url_id, MAX(id) AS max_id
                FROM url_checks
                GROUP BY url_id
            ) latest ON latest.max_id = uc1.id
        ) c ON c.url_id = u.id
        ORDER BY u.id DESC
        """;

    public UrlRepository(DataSource dataSource) {
        super(dataSource);
    }

    public Url save(Url url) throws SQLException {
        var createdAt = url.getCreatedAt() == null ? LocalDateTime.now() : url.getCreatedAt();
        var createdAtTimestamp = Timestamp.valueOf(createdAt);

        try (var connection = getConnection();
             var statement = connection.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, url.getName());
            statement.setTimestamp(2, createdAtTimestamp);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    url.setId(keys.getLong(1));
                }
            }
        }
        url.setCreatedAt(createdAt);
        return url;
    }

    public Optional<Url> find(Long id) throws SQLException {
        try (var connection = getConnection();
             var statement = connection.prepareStatement(SQL_FIND_BY_ID)) {
            statement.setLong(1, id);
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(buildUrl(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Url> findByName(String name) throws SQLException {
        try (var connection = getConnection();
             var statement = connection.prepareStatement(SQL_FIND_BY_NAME)) {
            statement.setString(1, name);
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(buildUrl(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<Url> getEntities() throws SQLException {
        var entities = new ArrayList<Url>();
        try (var connection = getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(SQL_FIND_ALL)) {
            while (resultSet.next()) {
                entities.add(buildUrlWithLastCheck(resultSet));
            }
        }
        return entities;
    }

    private Url buildUrl(ResultSet resultSet) throws SQLException {
        var url = new Url();
        url.setId(resultSet.getLong("id"));
        url.setName(resultSet.getString("name"));
        var createdAt = resultSet.getTimestamp("created_at");
        if (createdAt != null) {
            url.setCreatedAt(createdAt.toLocalDateTime());
        }
        return url;
    }

    private Url buildUrlWithLastCheck(ResultSet resultSet) throws SQLException {
        var url = buildUrl(resultSet);
        url.setLastCheckStatusCode(resultSet.getObject("last_check_status_code", Integer.class));

        var lastCheckCreatedAt = resultSet.getTimestamp("last_check_created_at");
        if (lastCheckCreatedAt != null) {
            url.setLastCheckCreatedAt(lastCheckCreatedAt.toLocalDateTime());
        }
        return url;
    }
}
