package Employess_data.employess.dto;

import Employess_data.employess.model.ClickEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor

public class UrlAnalyticsResponse {
    private String shortCode;
    private String originalUrl;
    private int totalClicks;
    private LocalDateTime createdAt;
    private LocalDateTime expireAt;
    private List<ClickEvent> recentClicks;
    private Map<String,Integer> clickByReferrer;
    private Map<String,Integer> clickByHour;
    private Map<String,Integer> clickByDay;

}
