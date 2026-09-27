package Employess_data.employess.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class UrlData {
    private String originalUrl;
    private String shortCode ;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private int clickCount;
    private String createdBy;
    private boolean isActive;
    private List<ClickEvent>clickEvents;
}
