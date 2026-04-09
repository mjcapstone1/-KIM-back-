package depth.finvibe.shared.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private TimeUtil() {
    }

    public static ZonedDateTime nowSeoul() {
        return ZonedDateTime.now(SEOUL);
    }

    public static String nowSeoulIso() {
        return ISO.format(nowSeoul());
    }
}
