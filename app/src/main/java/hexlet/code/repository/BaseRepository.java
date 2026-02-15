package hexlet.code.repository;

import lombok.Getter;
import lombok.Setter;

import java.sql.Connection;

@Getter
@Setter
public abstract class BaseRepository {
    private Connection connection;

    protected BaseRepository(Connection connection) {
        this.connection = connection;
    }
}
