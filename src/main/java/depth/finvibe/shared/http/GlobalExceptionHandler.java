package depth.finvibe.shared.http;

import depth.finvibe.shared.util.Maps;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(Maps.of(
                "code", exception.code(),
                "message", exception.getMessage(),
                "status", exception.status(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Maps.of(
                "code", "BAD_REQUEST",
                "message", exception.getMessage(),
                "status", 400,
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnknown(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Maps.of(
                "code", "INTERNAL_SERVER_ERROR",
                "message", exception.getMessage() == null || exception.getMessage().isBlank() ? "서버 내부 오류가 발생했습니다." : exception.getMessage(),
                "status", 500,
                "timestamp", Instant.now().toString()
        ));
    }
}
