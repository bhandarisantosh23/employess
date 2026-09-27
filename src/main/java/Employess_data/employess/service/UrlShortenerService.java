package Employess_data.employess.service;

import Employess_data.employess.dto.ShortenUrlRequest;
import Employess_data.employess.dto.ShortenUrlResponse;
import Employess_data.employess.dto.UrlAnalyticsResponse;
import Employess_data.employess.dto.UrlStatsResponse;
import Employess_data.employess.model.ClickEvent;
import Employess_data.employess.model.UrlData;
import io.lettuce.core.Limit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<String, UrlData> urlMapping =
            new ConcurrentHashMap<>();

    private final Map<String, List<ClickEvent>> clickAnalytics =
            new ConcurrentHashMap<>();

    @Value("${employess.base-url}")
    private String baseUrl;

    @Value("${employess.short-code-length}")
    private int shortCodeLength;

    @Value("${employess.max-generation-attempts}")
    private int maxGenerationAttempts;

    @Value("${employess.cache-ttl-minutes}")
    private int cacheTtlMinutes;

    private static final String BASE_62_CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public  void cleanupExpiredUrls() {

        int cleanedCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, UrlData>  entity : urlMapping.entrySet()){
            UrlData urlData = entity.getValue();

            if (urlData.getExpiresAt() != null
                    && urlData.getExpiresAt().isBefore(now)
                    && urlData.isActive()) {

                urlData.setActive(false);

                deleteCacheUrl(entity.getKey());
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            log.info("Cleaned up {} expired URLs", cleanedCount);
        }
    }

    public ShortenUrlResponse shortenUrl(
            ShortenUrlRequest request,
            String clientIp
    ) {

        String shortCode = request.getCustomAlias();

        if (shortCode == null || shortCode.trim().isEmpty()) {

            shortCode = generateUniqueShortCode();

        } else {

            shortCode = shortCode.trim();

            if (shortCodeExists(shortCode)) {
                throw new IllegalArgumentException(
                        "Custom alias already exists: " + shortCode
                );
            }
        }

        UrlData urlData = UrlData.builder()
                .originalUrl(request.getOriginalUrl())
                .shortCode(shortCode)
                .expiresAt(request.getExpiresAt())
                .createdAt(LocalDateTime.now())
                .createdBy(clientIp)
                .clickCount(0)
                .isActive(true)
                .clickEvents(new ArrayList<>())
                .build();

        urlMapping.put(shortCode, urlData);

        clickAnalytics.put(
                shortCode,
                new ArrayList<>()
        );

        cacheUrl(
                shortCode,
                request.getOriginalUrl()
        );

        log.info(
                "Created short URL: {} -> {}",
                shortCode,
                request.getOriginalUrl()
        );

        return ShortenUrlResponse.builder()
                .shortUrl(buildShortUrl(shortCode))
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl())
                .createdAt(urlData.getCreatedAt())
                .expiresAt(urlData.getExpiresAt())
                .build();
    }

    private String generateUniqueShortCode() {

        for (int attempt = 0;
             attempt < maxGenerationAttempts;
             attempt++) {

            String code = generateRandomBase62();

            if (!shortCodeExists(code)) {
                return code;
            }
        }

        throw new RuntimeException(
                "Failed to generate unique short code after "
                        + maxGenerationAttempts
                        + " attempts"
        );
    }

    private String generateRandomBase62() {

        StringBuilder sb =
                new StringBuilder(shortCodeLength);

        for (int i = 0;
             i < shortCodeLength;
             i++) {

            int index = ThreadLocalRandom.current()
                    .nextInt(BASE_62_CHARS.length());

            sb.append(BASE_62_CHARS.charAt(index));
        }

        return sb.toString();
    }

    private boolean shortCodeExists(String code) {
        return urlMapping.containsKey(code);
    }

    private String buildShortUrl(String shortCode) {

        String normalizedBaseUrl =
                baseUrl.endsWith("/")
                        ? baseUrl.substring(0, baseUrl.length() - 1)
                        : baseUrl;

        return normalizedBaseUrl + "/" + shortCode;
    }

    private void cacheUrl(
            String shortCode,
            String originalUrl
    ) {

        try {

            redisTemplate.opsForValue().set(
                    "url:" + shortCode,
                    originalUrl,
                    cacheTtlMinutes,
                    TimeUnit.MINUTES
            );

        } catch (Exception e) {

            log.warn(
                    "Failed to cache URL for {}: {}",
                    shortCode,
                    e.getMessage()
            );
        }
    }

    public Optional<String> getOriginalUrl(
            String shortCode
    ) {

        String cachedUrl = getCacheUrl(shortCode);

        if (cachedUrl != null) {
            return Optional.of(cachedUrl);
        }

        UrlData urlData = urlMapping.get(shortCode);

        if (urlData != null && urlData.isActive()) {

            if (isExpired(urlData)) {
                urlData.setActive(false);
                return Optional.empty();
            }

            cacheUrl(
                    shortCode,
                    urlData.getOriginalUrl()
            );

            return Optional.of(
                    urlData.getOriginalUrl()
            );
        }

        return Optional.empty();
    }

    private boolean isExpired(UrlData urlData) {

        return urlData.getExpiresAt() != null
                && urlData.getExpiresAt()
                .isBefore(LocalDateTime.now());
    }

    private String getCacheUrl(String shortCode) {

        try {

            return (String) redisTemplate
                    .opsForValue()
                    .get("url:" + shortCode);

        } catch (Exception e) {

            log.warn(
                    "Failed to get cached URL for {}: {}",
                    shortCode,
                    e.getMessage()
            );

            return null;
        }
    }

    public void recordClick(
            String shortCode,
            String clientIp,
            String userAgent,
            String referrer
    ) {

        UrlData urlData = urlMapping.get(shortCode);

        if (urlData != null && urlData.isActive()) {

            urlData.setClickCount(
                    urlData.getClickCount() + 1
            );

            ClickEvent clickEvent = ClickEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .ipAddress(clientIp)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .build();

            clickAnalytics
                    .computeIfAbsent(
                            shortCode,
                            key -> new ArrayList<>()
                    )
                    .add(clickEvent);

            log.debug(
                    "Recorded click for code: {}",
                    shortCode
            );
        }
    }

    public Optional<UrlStatsResponse> getUrlStats(String shortCode) {
        UrlData urlData=urlMapping.get(shortCode);
        if (urlData==null){
            return Optional.empty();
        }
        return Optional.of(
                UrlStatsResponse.builder()
                        .shortCode(shortCode)
                        .originalUrl(urlData.getOriginalUrl())
                        .clickCount(urlData.getClickCount())
                        .createdAt(urlData.getCreatedAt())
                        .expiresAt(urlData.getExpiresAt())
                        .isActive(urlData.isActive())
                        .build()
        );

    }

    public Optional<UrlAnalyticsResponse> getUrlAnalystics(String shortCode) {
        UrlData urlData=urlMapping.get(shortCode);

        if (urlData==null){
            return Optional.empty();
        }
        List<ClickEvent>clicks=clickAnalytics.getOrDefault(shortCode,new ArrayList<>());
        Map<String,Integer>clicksByReferrer=clicks.stream()
                .filter(click-> click. getReferrer()!=null)
                .collect(Collectors.groupingBy(
                        ClickEvent::getReferrer,Collectors.summingInt(event->1)
                ));
        Map<String,Integer>clicksByHours=clicks.stream()
                .collect(Collectors.groupingBy(
                        clickEvent -> clickEvent.getTimestamp().getHour()
                        +"00", Collectors.summingInt(e -> 1)
                ));


        Map<String,Integer>clicksByDay=clicks.stream()
                .collect(Collectors.groupingBy(
                ClickEvent::getReferrer,
                Collectors.summingInt(e -> 1)
        ));



        List<ClickEvent> recentClicks = clicks.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(10)
                .collect(Collectors.toList());

        return Optional.of(
                UrlAnalyticsResponse.builder()
                        .shortCode(shortCode)
                        .originalUrl(urlData.getOriginalUrl())
                        .totalClicks(urlData.getClickCount())
                        .createdAt(urlData.getCreatedAt())
                        .expireAt(urlData.getExpiresAt())      // ✅ matches "expireAt"
                        .recentClicks(recentClicks)            // ✅ List, plural
                        .clickByReferrer(clicksByReferrer)     // ✅ no "s"
                        .clickByHour(clicksByHours)            // ✅ no "s"
                        .clickByDay(clicksByDay)               // ✅ no "s"
                        .build()                                // ✅ don't forget!

        );


    }

    public boolean deleteUrl(String shortCode) {
        UrlData urlData = urlMapping.get(shortCode);


        if (urlData != null) {
            urlData.setActive(false);
            deleteCacheUrl(shortCode);
            log.info("Deleted this data: {} ", shortCode);
            return true;
        }
        return false;
    }




    private void deleteCacheUrl(String shortCode) {
        try {
            redisTemplate.delete("url:"+shortCode);
        }catch (Exception e){
            log.warn("Failed to delete cache url for {}:{}",shortCode,e.getMessage());

        }
    }
}
