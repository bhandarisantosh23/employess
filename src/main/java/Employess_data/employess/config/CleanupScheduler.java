package Employess_data.employess.config;



import Employess_data.employess.service.UrlShortenerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {

    private final UrlShortenerService urlShortenerService;

    @Scheduled(
            fixedRateString = "#{${employess.cleanup.interval-minutes} * 60 * 1000}"
    )
    public void cleanupExpiredUrls() {

        try {
            log.debug("Running scheduled cleanup of expired URLs");

            urlShortenerService.cleanupExpiredUrls();

            log.debug("Scheduled cleanup completed successfully");

        } catch (Exception e) {
            log.error("Error during scheduled cleanup", e);
        }
    }
}