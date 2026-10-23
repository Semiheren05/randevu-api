package com.semih.randevuapi.business;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StaffHoursTest {

    private static final OpeningHours FRIDAY_9_TO_18 = hours(DayOfWeek.FRIDAY, "09:00", "18:00");

    @ParameterizedTest
    @CsvSource(textBlock = """
            10:00, 14:00, 10:00, 14:00
            08:00, 13:00, 09:00, 13:00
            17:00, 20:00, 17:00, 18:00
            07:00, 20:00, 09:00, 18:00
            """)
    void ownHoursAreCutToTheBusinessHours(String opens, String closes, String expectedOpens, String expectedCloses) {
        OpeningHours own = hours(DayOfWeek.FRIDAY, opens, closes);

        // OpeningHours.equals compares the day only, so the times are checked one by one
        assertThat(own.intersect(FRIDAY_9_TO_18))
                .hasValueSatisfying(h -> {
                    assertThat(h.getOpensAt()).isEqualTo(LocalTime.parse(expectedOpens));
                    assertThat(h.getClosesAt()).isEqualTo(LocalTime.parse(expectedCloses));
                });
    }

    @Test
    void hoursThatOnlyTouchOrFallOnAnotherDayDoNotOverlap() {
        assertThat(hours(DayOfWeek.FRIDAY, "18:00", "20:00").intersect(FRIDAY_9_TO_18)).isEmpty();
        assertThat(hours(DayOfWeek.MONDAY, "09:00", "18:00").intersect(FRIDAY_9_TO_18)).isEmpty();
    }

    @Test
    void staffWithoutOwnHoursFollowTheBusiness() {
        Staff staff = new Staff(null, "Mehmet", List.of());

        assertThat(staff.followsBusinessHours()).isTrue();
        assertThat(staff.availableHours(FRIDAY_9_TO_18)).contains(FRIDAY_9_TO_18);
    }

    @Test
    void staffWithOwnHoursWorkOnlyOnTheirDays() {
        Staff staff = new Staff(null, "Mehmet", List.of());
        staff.replaceWorkingHours(List.of(
                hours(DayOfWeek.FRIDAY, "08:00", "12:00"),
                hours(DayOfWeek.MONDAY, "13:00", "17:00")));

        assertThat(staff.availableHours(FRIDAY_9_TO_18))
                .hasValueSatisfying(h -> assertThat(h.getOpensAt()).isEqualTo(LocalTime.of(9, 0)));
        assertThat(staff.availableHours(hours(DayOfWeek.TUESDAY, "09:00", "18:00"))).isEmpty();
    }

    @Test
    void anEmptyWeekGoesBackToTheBusinessHours() {
        Staff staff = new Staff(null, "Mehmet", List.of());
        staff.replaceWorkingHours(List.of(hours(DayOfWeek.MONDAY, "13:00", "17:00")));

        staff.replaceWorkingHours(List.of());

        assertThat(staff.followsBusinessHours()).isTrue();
        assertThat(staff.availableHours(FRIDAY_9_TO_18)).contains(FRIDAY_9_TO_18);
    }

    private static OpeningHours hours(DayOfWeek day, String opens, String closes) {
        return new OpeningHours(day, LocalTime.parse(opens), LocalTime.parse(closes));
    }
}
