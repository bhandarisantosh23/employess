package Employess_data.employess.controller;

import Employess_data.employess.dto.ShortenUrlRequest;
import Employess_data.employess.dto.ShortenUrlResponse;
import Employess_data.employess.dto.UrlAnalyticsResponse;
import Employess_data.employess.dto.UrlStatsResponse;
import Employess_data.employess.service.RateLimitService;
import Employess_data.employess.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
@RequiredArgsConstructor

public class UrlShortenerController {
     private final UrlShortenerService urlShortenerService;
             private final RateLimitService rateLimitService;

             @PostMapping("/shorten")
    public ResponseEntity< ?>shortUrl(
                     @Valid @RequestBody ShortenUrlRequest request,
                     HttpServletRequest httpRequest


                     ){
                 String clientIp=getClientIp(httpRequest);
                 if(!rateLimitService.isAllowed(clientIp)){
                     return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                             .body(Map.of(
                                    "error","Rate limit exceeded",
                                     "remaininRequest",rateLimitService.getRemainingRequests(clientIp),
                     "timeUntilReset",rateLimitService.getTimeUntilReset(clientIp)

                             ));

                 }
                 try{
                     ShortenUrlResponse response= urlShortenerService.shortenUrl(request,clientIp);
                     return ResponseEntity.ok(response);

                 } catch(IllegalArgumentException e) {
                     return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));

                 }catch (Exception e){
                     return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(Map.of("error","internal Server error "));
                 }
             }
             @GetMapping("/{shortCode}")
             public ResponseEntity<Void>redirectToUrl(
                     @PathVariable String shortCode,
                     HttpServletRequest request,
                     HttpServletResponse response
             ){
                 String clientIp=getClientIp(request);
                 String userAgent =request.getHeader("User-Agent");
                 String referrer =request.getHeader("Referrer");
                 Optional<String> originalUrl=urlShortenerService.getOriginalUrl(shortCode);
                 if (originalUrl.isPresent()){
                     urlShortenerService.recordClick(shortCode,clientIp,userAgent,referrer);
                     response.setHeader("Location",originalUrl.get());
                     return ResponseEntity.status(HttpStatus.FOUND).build();

                 }else {
                     return ResponseEntity.notFound().build();
                 }

             }
             @GetMapping("/stats/{shortCode}")
             public ResponseEntity<?>getUrlStats(@PathVariable String shortCode){
                 Optional<UrlStatsResponse>stats=urlShortenerService.getUrlStats(shortCode);
                 if (stats.isPresent()){
                     return ResponseEntity.ok(stats.get());


                 }else {
                     return ResponseEntity.status(HttpStatus.NOT_FOUND)
                             .body(Map.of("error","Short code not found"));
                 }

             }
             @GetMapping("/analytics/{shortcode}")
             public ResponseEntity<?>getUrlAnalytics(@PathVariable String shortCode){
                 Optional<UrlAnalyticsResponse>analytics=urlShortenerService.getUrlAnalystics(shortCode);
                 if (analytics.isPresent()){
                     return ResponseEntity.ok(analytics.get());
                 }else {
                     return ResponseEntity.status(HttpStatus.NOT_FOUND)
                             .body(Map.of("error","short code not found"));
                 }
             }
             @DeleteMapping("/{shortCode}")
             public ResponseEntity<?>deleteUrl(@PathVariable String shortCode){
                boolean deleted=urlShortenerService.deleteUrl(shortCode);
                if (deleted){
                    return ResponseEntity.ok(Map.of("message ","Url deleted Successfully "));

                } else
                   return  ResponseEntity.status(HttpStatus.NOT_FOUND)
                           .body(Map.of("error","short code not found"));

             }


    private String getClientIp(HttpServletRequest httpRequest) {
                 String xForwardedFor=httpRequest.getHeader("x-Forwarded-For");
                 if(xForwardedFor !=null && !xForwardedFor.isEmpty()){
                     return xForwardedFor.split(",")[0].trim();

                 }
                 String xRealIp=httpRequest.getHeader("x-Real-Ip");
                 if(xRealIp!=null && !xRealIp.isEmpty() ){
                     return  xRealIp;
                 }
                 return httpRequest.getRemoteAddr();
    }
}
