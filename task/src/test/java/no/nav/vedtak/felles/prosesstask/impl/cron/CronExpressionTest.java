package no.nav.vedtak.felles.prosesstask.impl.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import no.nav.vedtak.felles.prosesstask.impl.cron.CronExpression.CronFieldType;
import no.nav.vedtak.felles.prosesstask.impl.cron.CronExpression.DayOfMonthField;
import no.nav.vedtak.felles.prosesstask.impl.cron.CronExpression.DayOfWeekField;
import no.nav.vedtak.felles.prosesstask.impl.cron.CronExpression.SimpleField;

class CronExpressionTest {
    private TimeZone original;
    private ZoneId zoneId;

    @BeforeEach
    void setUp() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"));
        zoneId = TimeZone.getDefault().toZoneId();
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(original);
    }

    @Test
    void shall_parse_number() {
        SimpleField field = new SimpleField(CronFieldType.MINUTE, "5");
        assertPossibleValues(field, 5);
    }

    private void assertPossibleValues(SimpleField field, Integer... values) {
        Set<Integer> valid = values == null ? new HashSet<>() : new HashSet<>(Arrays.asList(values));
        for (int i = field.fieldType.from; i <= field.fieldType.to; i++) {
            String errorText = i + ":" + valid;
            if (valid.contains(i)) {
                assertTrue(field.matches(i), errorText);
            } else {
                assertFalse(field.matches(i), errorText);
            }
        }
    }

    @Test
    void shall_parse_number_with_increment() {
        SimpleField field = new SimpleField(CronFieldType.MINUTE, "0/15");
        assertPossibleValues(field, 0, 15, 30, 45);
    }

    @Test
    void shall_parse_range() {
        SimpleField field = new SimpleField(CronFieldType.MINUTE, "5-10");
        assertPossibleValues(field, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void shall_parse_range_with_increment() {
        SimpleField field = new SimpleField(CronFieldType.MINUTE, "20-30/2");
        assertPossibleValues(field, 20, 22, 24, 26, 28, 30);
    }

    @Test
    void shall_parse_asterix() {
        SimpleField field = new SimpleField(CronFieldType.DAY_OF_WEEK, "*");
        assertPossibleValues(field, 1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void shall_parse_asterix_with_increment() {
        SimpleField field = new SimpleField(CronFieldType.DAY_OF_WEEK, "*/2");
        assertPossibleValues(field, 1, 3, 5, 7);
    }

    @Test
    void shall_ignore_field_in_day_of_week() {
        DayOfWeekField field = new DayOfWeekField("?");
        assertTrue(field.matches(ZonedDateTime.now().toLocalDate()));
    }

    @Test
    void shall_ignore_field_in_day_of_month() {
        DayOfMonthField field = new DayOfMonthField("?");
        assertTrue(field.matches(ZonedDateTime.now().toLocalDate()));
    }

    @Test
    void shall_give_error_if_invalid_count_field() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("* 3 *");
        });
    }

    @Test
    void shall_give_error_if_minute_field_ignored() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SimpleField(CronFieldType.MINUTE, "?");
        });
    }

    @Test
    void shall_give_error_if_hour_field_ignored() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SimpleField(CronFieldType.HOUR, "?");
        });
    }

    @Test
    void shall_give_error_if_month_field_ignored() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SimpleField(CronFieldType.MONTH, "?");
        });
    }

    @Test
    void shall_give_last_day_of_month_in_leapyear() {
        DayOfMonthField field = new DayOfMonthField("L");
        assertThat(field.matches(LocalDate.of(2012, 2, 29))).isTrue();
    }

    @Test
    void shall_give_last_day_of_month() {
        DayOfMonthField field = new DayOfMonthField("L");
        YearMonth now = YearMonth.now();
        assertThat(field.matches(LocalDate.of(now.getYear(), now.getMonthValue(), now.lengthOfMonth()))).isTrue();
    }

    @Test
    void check_all() {
        CronExpression cronExpr = new CronExpression("* * * * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 1, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 2, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 2, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 2, 1, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 59, 59, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 14, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_invalid_input() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression(null);
        });
    }

    @Test
    void check_second_number() {
        CronExpression cronExpr = new CronExpression("3 * * * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 1, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 13, 1, 3, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 1, 3, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 2, 3, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 59, 3, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 14, 0, 3, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 23, 59, 3, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 11, 0, 0, 3, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 30, 23, 59, 3, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 1, 0, 0, 3, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_second_increment() {
        CronExpression cronExpr = new CronExpression("5/15 * * * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 5, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 5, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 20, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 20, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 35, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 35, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 50, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 50, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 1, 5, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        // if rolling over minute then reset second (cron rules - increment affects only values in own field)
        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 50, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 1, 10, 0, zoneId);
        assertThat(new CronExpression("10/100 * * * * *").nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 1, 10, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 2, 10, 0, zoneId);
        assertThat(new CronExpression("10/100 * * * * *").nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_second_list() {
        CronExpression cronExpr = new CronExpression("7,19 * * * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 7, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 7, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 19, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 19, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 1, 7, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_second_range() {
        CronExpression cronExpr = new CronExpression("42-45 * * * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 42, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 42, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 43, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 43, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 44, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 44, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 0, 45, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 0, 45, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 1, 42, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_second_invalid_range() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("42-63 * * * * *");
        });
    }

    @Test
    void check_second_invalid_increment_modifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("42#3 * * * * *");
        });
    }

    @Test
    void check_minute_number() {
        CronExpression cronExpr = new CronExpression("0 3 * * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 1, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 13, 3, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 3, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 14, 3, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_minute_increment() {
        CronExpression cronExpr = new CronExpression("0 0/15 * * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 13, 15, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 15, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 30, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 30, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 45, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 45, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 14, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_minute_list() {
        CronExpression cronExpr = new CronExpression("0 7,19 * * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 13, 7, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 13, 7, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 13, 19, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_hour_number() {
        CronExpression cronExpr = new CronExpression("0 * 3 * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 1, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 11, 3, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 11, 3, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 11, 3, 1, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 11, 3, 59, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 12, 3, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_hour_increment() {
        CronExpression cronExpr = new CronExpression("0 * 0/15 * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 15, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 15, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 15, 1, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 15, 59, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 11, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 11, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 11, 0, 1, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 11, 15, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 11, 15, 1, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_hour_list() {
        CronExpression cronExpr = new CronExpression("0 * 7,19 * * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 10, 19, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 19, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 10, 19, 1, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 10, 19, 59, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 11, 7, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_hour_shall_run_25_times_in_DST_change_to_wintertime() {
        CronExpression cron = new CronExpression("0 1 * * * *");
        ZonedDateTime start = ZonedDateTime.of(2011, 10, 30, 0, 0, 0, 0, zoneId);
        ZonedDateTime slutt = start.plusDays(1);
        ZonedDateTime tid = start;

        assertEquals(25, Duration.between(start, slutt).toHours());

        int count = 0;
        ZonedDateTime lastTime = tid;
        while (tid.isBefore(slutt)) {
            ZonedDateTime nextTime = cron.nextTimeAfter(tid);
            assertThat(nextTime).isAfter(lastTime);
            lastTime = nextTime;
            tid = tid.plusHours(1);
            count++;
        }
        assertThat(count).isEqualTo(25);
    }

    @Test
    void check_hour_shall_run_23_times_in_DST_change_to_summertime() {
        CronExpression cron = new CronExpression("0 0 * * * *");
        ZonedDateTime start = ZonedDateTime.of(2011, 03, 27, 1, 0, 0, 0, zoneId);
        ZonedDateTime slutt = start.plusDays(1);
        ZonedDateTime tid = start;

        // throws: Unsupported unit: Seconds
        assertEquals(23, Duration.between(start, slutt).toHours());

        int count = 0;
        ZonedDateTime lastTime = tid;
        while (tid.isBefore(slutt)) {
            ZonedDateTime nextTime = cron.nextTimeAfter(tid);
            assertThat(nextTime).isAfter(lastTime);
            lastTime = nextTime;
            tid = tid.plusHours(1);
            count++;
        }
        assertThat(count).isEqualTo(23);
    }

    @Test
    void check_dayOfMonth_number() {
        CronExpression cronExpr = new CronExpression("0 * * 3 * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 5, 3, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 5, 3, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 3, 0, 1, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 5, 3, 0, 59, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 3, 1, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 5, 3, 23, 59, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 6, 3, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfMonth_increment() {
        CronExpression cronExpr = new CronExpression("0 0 0 1/15 * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 16, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 16, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 1, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 30, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 5, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 16, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfMonth_list() {
        CronExpression cronExpr = new CronExpression("0 0 0 7,19 * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 19, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 19, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 7, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 5, 7, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 19, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 5, 30, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 6, 7, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfMonth_last() {
        CronExpression cronExpr = new CronExpression("0 0 0 L * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 30, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 2, 12, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 2, 29, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfMonth_number_last_L() {
        CronExpression cronExpr = new CronExpression("0 0 0 3L * *");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 10, 13, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 30 - 3, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 2, 12, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 2, 29 - 3, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfMonth_closest_weekday_W() {
        CronExpression cronExpr = new CronExpression("0 0 0 9W * *");

        // 9 - is weekday in may
        ZonedDateTime after = ZonedDateTime.of(2012, 5, 2, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 5, 9, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        // 9 - is weekday in may
        after = ZonedDateTime.of(2012, 5, 8, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        // 9 - saturday, friday closest weekday in june
        after = ZonedDateTime.of(2012, 5, 9, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 6, 8, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        // 9 - sunday, monday closest weekday in september
        after = ZonedDateTime.of(2012, 9, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 9, 10, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfMonth_invalid_modifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("0 0 0 9X * *");
        });
    }

    @Test
    void check_dayOfMonth_invalid_increment_modifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("0 0 0 9#2 * *");
        });
    }

    @Test
    void check_month_number() {
        ZonedDateTime after = ZonedDateTime.of(2012, 2, 12, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 5, 1, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 1 5 *").nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_month_increment() {
        ZonedDateTime after = ZonedDateTime.of(2012, 2, 12, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 5, 1, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 1 5/2 *").nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 5, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 7, 1, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 1 5/2 *").nextTimeAfter(after)).isEqualTo(expected);

        // if rolling over year then reset month field (cron rules - increments only affect own field)
        after = ZonedDateTime.of(2012, 5, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2013, 5, 1, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 1 5/10 *").nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_month_list() {
        CronExpression cronExpr = new CronExpression("0 0 0 1 3,7,12 *");

        ZonedDateTime after = ZonedDateTime.of(2012, 2, 12, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 3, 1, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 3, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 7, 1, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 7, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 12, 1, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_month_list_by_name() {
        CronExpression cronExpr = new CronExpression("0 0 0 1 MAR,JUL,DEC *");

        ZonedDateTime after = ZonedDateTime.of(2012, 2, 12, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 3, 1, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 3, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 7, 1, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 7, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 12, 1, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_month_invalid_modifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("0 0 0 1 ? *");
        });
    }

    @Test
    void check_dayOfWeek_number() {
        CronExpression cronExpr = new CronExpression("0 0 0 * * 3");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 4, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 4, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 11, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 12, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 18, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 18, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 25, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfWeek_increment() {
        CronExpression cronExpr = new CronExpression("0 0 0 * * 3/2");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 4, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 4, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 6, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 6, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 8, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 8, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 11, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfWeek_list() {
        CronExpression cronExpr = new CronExpression("0 0 0 * * 1,5,7");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 2, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 2, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 6, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 6, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 8, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfWeek_list_by_name() {
        CronExpression cronExpr = new CronExpression("0 0 0 * * MON,FRI,SUN");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 2, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 2, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 6, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 6, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 8, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfWeek_last_friday_in_month() {
        CronExpression cronExpr = new CronExpression("0 0 0 * * 5L");

        ZonedDateTime after = ZonedDateTime.of(2012, 4, 1, 1, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 27, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 27, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 25, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 2, 6, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 2, 24, 0, 0, 0, 0, zoneId);
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 2, 6, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 2, 24, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * FRIL").nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfWeek_invalid_modifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("0 0 0 * * 5W");
        });
    }

    @Test
    void check_dayOfWeek_invalid_increment_modifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("0 0 0 * * 5?3");
        });
    }

    @Test
    void check_dayOfWeek_shall_interpret_0_as_sunday() {
        ZonedDateTime after = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 8, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 0").nextTimeAfter(after)).isEqualTo(expected);

        expected = ZonedDateTime.of(2012, 4, 29, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 0L").nextTimeAfter(after)).isEqualTo(expected);

        expected = ZonedDateTime.of(2012, 4, 8, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 0#2").nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfWeek_shall_interpret_7_as_sunday() {
        ZonedDateTime after = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 8, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 7").nextTimeAfter(after)).isEqualTo(expected);

        expected = ZonedDateTime.of(2012, 4, 29, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 7L").nextTimeAfter(after)).isEqualTo(expected);

        expected = ZonedDateTime.of(2012, 4, 8, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 7#2").nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void check_dayOfWeek_nth_day_in_month() {
        ZonedDateTime after = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2012, 4, 20, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 5#3").nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 20, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 18, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 5#3").nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 3, 30, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 7#1").nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 4, 1, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 5, 6, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 7#1").nextTimeAfter(after)).isEqualTo(expected);

        after = ZonedDateTime.of(2012, 2, 6, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 2, 29, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * 3#5").nextTimeAfter(after)).isEqualTo(expected); // leapday

        after = ZonedDateTime.of(2012, 2, 6, 0, 0, 0, 0, zoneId);
        expected = ZonedDateTime.of(2012, 2, 29, 0, 0, 0, 0, zoneId);
        assertThat(new CronExpression("0 0 0 * * WED#5").nextTimeAfter(after)).isEqualTo(expected); // leapday
    }

    @Test
    void shall_not_not_support_rolling_period() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CronExpression("* * 5-1 * * *");
        });
    }

    @Test
    void non_existing_date_throws_exception() {
        var exp = new CronExpression("* * * 30 2 *");
        var nå = ZonedDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> {
            // Will check for the next 4 years - no 30th of February is found so a IAE is thrown.
            exp.nextTimeAfter(nå);
        });
    }

    @Test
    void test_default_barrier() {
        CronExpression cronExpr = new CronExpression("* * * 29 2 *");

        ZonedDateTime after = ZonedDateTime.of(2012, 3, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2016, 2, 29, 0, 0, 0, 0, zoneId);
        // the default barrier is 4 years - so leap years are considered.
        assertThat(cronExpr.nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void test_one_year_barrier() {
        ZonedDateTime after = ZonedDateTime.of(2012, 3, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime barrier = ZonedDateTime.of(2013, 3, 1, 0, 0, 0, 0, zoneId);
        var exp = new CronExpression("* * * 29 2 *");
        assertThrows(IllegalArgumentException.class, () -> {
            // The next leap year is 2016, so an IllegalArgumentException is expected.
            exp.nextTimeAfter(after, barrier);
        });
    }

    @Test
    void test_two_year_barrier() {
        ZonedDateTime after = ZonedDateTime.of(2012, 3, 1, 0, 0, 0, 0, zoneId);
        var exp = new CronExpression("* * * 29 2 *");
        assertThrows(IllegalArgumentException.class, () -> {
            // The next leap year is 2016, so an IllegalArgumentException is expected.
            exp.nextTimeAfter(after, 1000L * 60 * 60 * 24 * 356 * 2);
        });
    }

    @Test
    void test_seconds_specified_but_should_be_omitted() {
        assertThrows(IllegalArgumentException.class, () -> {
            CronExpression.createWithoutSeconds("* * * 29 2 *");
        });
    }

    @Test
    void test_without_seconds() {
        ZonedDateTime after = ZonedDateTime.of(2012, 3, 1, 0, 0, 0, 0, zoneId);
        ZonedDateTime expected = ZonedDateTime.of(2016, 2, 29, 0, 0, 0, 0, zoneId);
        assertThat(CronExpression.createWithoutSeconds("* * 29 2 *").nextTimeAfter(after)).isEqualTo(expected);
    }

    @Test
    void testTriggerProblemSameMonth() {
        assertThat(ZonedDateTime.parse("2020-01-02T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * 1-8 1 *")
                .nextTimeAfter(ZonedDateTime.parse("2020-01-01T23:50:00Z")));
    }

    @Test
    void testTriggerProblemNextMonth() {
        assertThat(ZonedDateTime.parse("2020-02-01T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * 1-8 2 *")
                .nextTimeAfter(ZonedDateTime.parse("2020-01-31T23:50:00Z")));
    }

    @Test
    void testTriggerProblemNextYear() {
        assertThat(ZonedDateTime.parse("2020-01-01T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * 1-8 1 *")
                .nextTimeAfter(ZonedDateTime.parse("2019-12-31T23:50:00Z")));
    }

    @Test
    void testTriggerProblemNextMonthMonthAst() {
        assertThat(ZonedDateTime.parse("2020-02-01T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * 1-8 * *")
                .nextTimeAfter(ZonedDateTime.parse("2020-01-31T23:50:00Z")));
    }

    @Test
    void testTriggerProblemNextYearMonthAst() {
        assertThat(ZonedDateTime.parse("2020-01-01T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * 1-8 * *")
                .nextTimeAfter(ZonedDateTime.parse("2019-12-31T23:50:00Z")));
    }

    @Test
    void testTriggerProblemNextMonthDayAst() {
        assertThat(ZonedDateTime.parse("2020-02-01T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * * 2 *")
                .nextTimeAfter(ZonedDateTime.parse("2020-01-31T23:50:00Z")));
    }

    @Test
    void testTriggerProblemNextYearDayAst() {
        assertThat(ZonedDateTime.parse("2020-01-01T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * * 1 *")
                .nextTimeAfter(ZonedDateTime.parse("2019-12-31T22:50:00Z")));
    }

    @Test
    void testTriggerProblemNextMonthAllAst() {
        assertThat(ZonedDateTime.parse("2020-02-01T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * * * *")
                .nextTimeAfter(ZonedDateTime.parse("2020-01-31T23:50:00Z")));
    }

    @Test
    void testTriggerProblemNextYearAllAst() {
        assertThat(ZonedDateTime.parse("2020-01-01T00:50:00Z")).isEqualTo(
            new CronExpression("00 50 * * * *")
                .nextTimeAfter(ZonedDateTime.parse("2019-12-31T23:50:00Z")));
    }
}
