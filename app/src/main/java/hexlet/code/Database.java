package hexlet.code;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.stream.Collectors;

public final class Database {
    private Database() {
    }

    public static void applyMigrations(DataSource dataSource) {
        var resource = Database.class.getClassLoader().getResourceAsStream("schema.sql");
        if (resource == null) {
            throw new IllegalStateException("schema.sql not found");
        }
        String sql;
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            sql = reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema.sql", e);
        }

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to apply migrations", e);
        }
    }
}
