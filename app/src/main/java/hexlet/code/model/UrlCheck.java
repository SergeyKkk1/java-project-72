package hexlet.code.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public final class UrlCheck {
    private Long id;
    private Integer statusCode;
    private String title;
    private String h1;
    private String description;
    private Long urlId;
    private LocalDateTime createdAt = LocalDateTime.now();
}
