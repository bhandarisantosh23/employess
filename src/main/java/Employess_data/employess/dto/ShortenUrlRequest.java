package Employess_data.employess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlRequest {
    @NotBlank(message="original Url is required")
    @Pattern(regexp = "https?://.*",message ="url is incorrect")
    private String originalUrl;
    private String  customAlias;
    private LocalDateTime expireAt;


    public LocalDateTime getExpiresAt() {
        return null;
    }
}
