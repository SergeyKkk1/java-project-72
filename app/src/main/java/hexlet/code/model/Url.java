package hexlet.code.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public final class Url {
    private Long id;
    private String name;
    private LocalDateTime createdAt = LocalDateTime.now();
}
