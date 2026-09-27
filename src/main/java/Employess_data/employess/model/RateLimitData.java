package Employess_data.employess.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RateLimitData {
    private int minuteCount;
    private  int hourCount;
    private LocalDateTime minuteWindowStart;
    private LocalDateTime hourWindowStart;
}
