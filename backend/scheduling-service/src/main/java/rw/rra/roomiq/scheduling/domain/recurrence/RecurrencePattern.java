package rw.rra.roomiq.scheduling.domain.recurrence;

import org.springframework.http.HttpStatus;
import rw.rra.roomiq.common.web.DomainException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RecurrencePattern {
    public static final int MAX_OCCURRENCES = 365;
    private static final DateTimeFormatter UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY, "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY, "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY);

    private final String normalizedRule;
    private final Frequency frequency;
    private final int interval;
    private final Set<DayOfWeek> byDays;
    private final Set<Integer> byMonthDays;
    private final Set<Integer> byMonths;
    private final DayOfWeek weekStart;
    private final LocalDate until;
    private final Integer count;
    private final LocalDate startsOn;
    private final LocalDate horizonEnd;

    private RecurrencePattern(String normalizedRule, Frequency frequency, int interval,
                              Set<DayOfWeek> byDays, Set<Integer> byMonthDays, Set<Integer> byMonths,
                              DayOfWeek weekStart, LocalDate until, Integer count, LocalDate startsOn) {
        this.normalizedRule = normalizedRule;
        this.frequency = frequency;
        this.interval = interval;
        this.byDays = Set.copyOf(byDays);
        this.byMonthDays = Set.copyOf(byMonthDays);
        this.byMonths = Set.copyOf(byMonths);
        this.weekStart = weekStart;
        this.until = until;
        this.count = count;
        this.startsOn = startsOn;
        this.horizonEnd = startsOn.plusYears(1);
    }

    public static RecurrencePattern parse(String value, LocalDate startsOn) {
        if (value == null || value.isBlank() || startsOn == null) {
            throw invalid("INVALID_RRULE", "An RRULE and start date are required");
        }
        if (value.length() > 1000 || value.chars().anyMatch(Character::isWhitespace)) {
            throw invalid("INVALID_RRULE", "RRULE must be a compact value no longer than 1000 characters");
        }

        Map<String, String> parts = new java.util.LinkedHashMap<>();
        for (String component : value.split(";", -1)) {
            int separator = component.indexOf('=');
            if (separator <= 0 || separator == component.length() - 1
                    || component.indexOf('=', separator + 1) >= 0) {
                throw invalid("INVALID_RRULE", "Every RRULE component must have one non-empty key and value");
            }
            String key = component.substring(0, separator).toUpperCase(Locale.ROOT);
            String componentValue = component.substring(separator + 1).toUpperCase(Locale.ROOT);
            if (!Set.of("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST", "UNTIL", "COUNT")
                    .contains(key)) {
                throw invalid("UNSUPPORTED_RRULE_COMPONENT", "Unsupported RRULE component: " + key);
            }
            if (parts.putIfAbsent(key, componentValue) != null) {
                throw invalid("INVALID_RRULE", "RRULE components must not be repeated");
            }
        }

        String frequencyValue = parts.get("FREQ");
        if (frequencyValue == null) {
            throw invalid("INVALID_RRULE", "RRULE requires a FREQ component");
        }
        Frequency frequency = Frequency.parse(frequencyValue);
        int interval = parsePositiveInteger(parts.getOrDefault("INTERVAL", "1"), "INTERVAL", Integer.MAX_VALUE);
        Set<DayOfWeek> byDays = parseWeekdays(parts.get("BYDAY"));
        Set<Integer> byMonthDays = parseIntegerSet(parts.get("BYMONTHDAY"), -31, 31, "BYMONTHDAY", true);
        Set<Integer> byMonths = parseIntegerSet(parts.get("BYMONTH"), 1, 12, "BYMONTH", false);
        DayOfWeek weekStart = parts.containsKey("WKST") ? parseWeekday(parts.get("WKST"), "WKST") : DayOfWeek.MONDAY;
        LocalDate until = parseUntil(parts.get("UNTIL"));
        Integer count = parts.containsKey("COUNT")
                ? parsePositiveInteger(parts.get("COUNT"), "COUNT", MAX_OCCURRENCES) : null;

        if (until == null && count == null) {
            throw invalid("RRULE_END_BOUND_REQUIRED", "RRULE must specify UNTIL, COUNT, or both");
        }
        if (byMonthDays.size() > 0 && frequency == Frequency.WEEKLY) {
            throw invalid("INVALID_RRULE_COMPONENT", "BYMONTHDAY is not supported with WEEKLY frequency");
        }

        RecurrencePattern pattern = new RecurrencePattern(normalize(parts), frequency, interval, byDays,
                byMonthDays, byMonths, weekStart, until, count, startsOn);
        if (until != null && until.isBefore(startsOn)) {
            throw invalid("INVALID_RRULE_DATE_RANGE", "UNTIL must not be earlier than startsOn");
        }
        if (until != null && until.isAfter(pattern.horizonEnd)) {
            throw invalid("RECURRENCE_HORIZON_EXCEEDED",
                    "UNTIL must be within one year of startsOn");
        }
        if (!pattern.matches(startsOn)) {
            throw invalid("RECURRENCE_START_DATE_MISMATCH",
                    "startsOn must be included by the RRULE pattern");
        }
        pattern.expand();
        return pattern;
    }

    public String normalizedRule() {
        return normalizedRule;
    }

    public LocalDate until() {
        return until;
    }

    public Integer count() {
        return count;
    }

    public List<LocalDate> occurrences() {
        return expand();
    }

    private List<LocalDate> expand() {
        LocalDate end = until == null ? horizonEnd : until;
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = startsOn; !date.isAfter(end); date = date.plusDays(1)) {
            if (!matches(date)) {
                continue;
            }
            dates.add(date);
            if (dates.size() > MAX_OCCURRENCES) {
                throw invalid("RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED",
                        "RRULE produces more than 365 occurrences");
            }
            if (count != null && dates.size() == count) {
                break;
            }
        }
        if (count != null && until == null && dates.size() < count) {
            throw invalid("RECURRENCE_HORIZON_EXCEEDED",
                    "COUNT cannot be reached within one year of startsOn");
        }
        return List.copyOf(dates);
    }

    private boolean matches(LocalDate date) {
        if (!byMonths.isEmpty() && !byMonths.contains(date.getMonthValue())) {
            return false;
        }
        if (!byMonthDays.isEmpty() && byMonthDays.stream().noneMatch(value -> matchesMonthDay(date, value))) {
            return false;
        }
        if (!byDays.isEmpty() && !byDays.contains(date.getDayOfWeek())) {
            return false;
        }

        return switch (frequency) {
            case DAILY -> ChronoUnit.DAYS.between(startsOn, date) % interval == 0;
            case WEEKLY -> {
                long weeks = ChronoUnit.DAYS.between(startOfWeek(startsOn), startOfWeek(date)) / 7;
                yield weeks % interval == 0
                        && (byDays.isEmpty() ? date.getDayOfWeek() == startsOn.getDayOfWeek()
                        : byDays.contains(date.getDayOfWeek()));
            }
            case MONTHLY -> {
                long months = ChronoUnit.MONTHS.between(startsOn.withDayOfMonth(1), date.withDayOfMonth(1));
                yield months % interval == 0
                        && matchesMonthlyDay(date);
            }
            case YEARLY -> ChronoUnit.YEARS.between(startsOn.withDayOfYear(1), date.withDayOfYear(1)) % interval == 0
                    && matchesYearlyDay(date);
        };
    }

    private boolean matchesMonthlyDay(LocalDate date) {
        if (byMonthDays.isEmpty() && byDays.isEmpty()) {
            return date.getDayOfMonth() == startsOn.getDayOfMonth();
        }
        return true;
    }

    private boolean matchesYearlyDay(LocalDate date) {
        if (byMonthDays.isEmpty() && byDays.isEmpty()) {
            return (byMonths.isEmpty() ? date.getMonthValue() == startsOn.getMonthValue()
                    : byMonths.contains(date.getMonthValue()))
                    && date.getDayOfMonth() == startsOn.getDayOfMonth();
        }
        return true;
    }

    private LocalDate startOfWeek(LocalDate date) {
        return date.minusDays(Math.floorMod(date.getDayOfWeek().getValue() - weekStart.getValue(), 7));
    }

    private static boolean matchesMonthDay(LocalDate date, int monthDay) {
        int day = monthDay > 0 ? monthDay : date.lengthOfMonth() + monthDay + 1;
        return date.getDayOfMonth() == day;
    }

    private static Set<DayOfWeek> parseWeekdays(String value) {
        if (value == null) {
            return Set.of();
        }
        Set<DayOfWeek> values = new HashSet<>();
        for (String token : value.split(",", -1)) {
            DayOfWeek day = parseWeekday(token, "BYDAY");
            if (!values.add(day)) {
                throw invalid("INVALID_RRULE_COMPONENT", "BYDAY values must not be repeated");
            }
        }
        return values;
    }

    private static DayOfWeek parseWeekday(String value, String component) {
        DayOfWeek day = WEEKDAYS.get(value);
        if (day == null) {
            throw invalid("INVALID_RRULE_COMPONENT", component + " must contain RFC weekday tokens");
        }
        return day;
    }

    private static Set<Integer> parseIntegerSet(String value, int minimum, int maximum,
                                                String component, boolean allowNegative) {
        if (value == null) {
            return Set.of();
        }
        Set<Integer> values = new HashSet<>();
        for (String token : value.split(",", -1)) {
            int parsed;
            try {
                parsed = Integer.parseInt(token);
            } catch (NumberFormatException exception) {
                throw invalid("INVALID_RRULE_COMPONENT", component + " contains an invalid integer");
            }
            if (parsed < minimum || parsed > maximum || (allowNegative && parsed == 0)
                    || (!allowNegative && parsed < 0) || !values.add(parsed)) {
                throw invalid("INVALID_RRULE_COMPONENT", component + " contains an unsupported value");
            }
        }
        return values;
    }

    private static int parsePositiveInteger(String value, String component, int maximum) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0 && parsed <= maximum) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // The validation error below is the stable API outcome for invalid numeric RRULE values.
        }
        String code = "COUNT".equals(component) ? "RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED"
                : "INVALID_RRULE_COMPONENT";
        throw invalid(code, component + " must be a positive integer no greater than " + maximum);
    }

    private static LocalDate parseUntil(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, UNTIL_FORMAT);
        } catch (DateTimeParseException exception) {
            throw invalid("INVALID_RRULE_UNTIL", "UNTIL must be a valid iCalendar DATE value (YYYYMMDD)");
        }
    }

    private static String normalize(Map<String, String> parts) {
        List<String> orderedKeys = List.of("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH",
                "WKST", "UNTIL", "COUNT");
        return orderedKeys.stream().filter(parts::containsKey)
                .map(key -> key + "=" + parts.get(key)).collect(java.util.stream.Collectors.joining(";"));
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }

    private enum Frequency {
        DAILY, WEEKLY, MONTHLY, YEARLY;

        private static Frequency parse(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw invalid("INVALID_RRULE_FREQUENCY", "FREQ must be DAILY, WEEKLY, MONTHLY, or YEARLY");
            }
        }
    }
}
