package com.semih.randevuapi;

import com.jayway.jsonpath.JsonPath;
import com.semih.randevuapi.booking.BookingReminderService;
import com.semih.randevuapi.payment.FakePaymentGateway;
import com.semih.randevuapi.payment.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole booking flow against a real PostgreSQL (Flyway migrations included).
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
class BookingFlowIntegrationTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final String ALL_WEEK_9_TO_18 = """
            [{"day": "MONDAY", "opensAt": "09:00", "closesAt": "18:00"},
             {"day": "TUESDAY", "opensAt": "09:00", "closesAt": "18:00"},
             {"day": "WEDNESDAY", "opensAt": "09:00", "closesAt": "18:00"},
             {"day": "THURSDAY", "opensAt": "09:00", "closesAt": "18:00"},
             {"day": "FRIDAY", "opensAt": "09:00", "closesAt": "18:00"},
             {"day": "SATURDAY", "opensAt": "09:00", "closesAt": "18:00"},
             {"day": "SUNDAY", "opensAt": "09:00", "closesAt": "18:00"}]
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private BookingReminderService reminderService;
    @Autowired
    private PaymentGateway paymentGateway;

    private String ownerToken;
    private String customerToken;
    private long businessId;
    private long offeringId;
    /** Two days ahead: always in the future, always inside the booking window. */
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("TRUNCATE bookings, closures, staff_hours, staff_offerings, staff, offerings, "
                + "business_hours, businesses, users RESTART IDENTITY CASCADE");
        day = LocalDate.now(ISTANBUL).plusDays(2);

        ownerToken = register("ali@example.com", "Ali", "OWNER");
        customerToken = register("ayse@example.com", "Ayşe", "CUSTOMER");

        String business = json(perform(post("/api/owner/businesses"), ownerToken, """
                {"name": "Kuaför Ali", "city": "Istanbul", "description": "Saç ve sakal"}
                """).andExpect(status().isCreated()));
        businessId = ((Number) JsonPath.read(business, "$.id")).longValue();

        perform(put("/api/owner/businesses/{id}/hours", businessId), ownerToken, ALL_WEEK_9_TO_18)
                .andExpect(status().isOk());
        String offering = json(perform(post("/api/owner/businesses/{id}/offerings", businessId), ownerToken, """
                {"name": "Saç kesimi", "durationMinutes": 30, "price": 250.00}
                """).andExpect(status().isCreated()));
        offeringId = ((Number) JsonPath.read(offering, "$.id")).longValue();
    }

    @Test
    void anyoneCanBrowseBusinessesAndFreeSlots() throws Exception {
        mockMvc.perform(get("/api/businesses").param("city", "istanbul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("kuafor-ali"));

        mockMvc.perform(get("/api/businesses/kuafor-ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingHours.length()").value(7))
                .andExpect(jsonPath("$.openingHours[0].opensAt").value("09:00"))
                .andExpect(jsonPath("$.offerings[0].name").value("Saç kesimi"));

        availability()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0]").value("09:00"))
                // 17:30 is the last start that still ends by 18:00
                .andExpect(jsonPath("$.slots[-1]").value("17:30"));
    }

    @Test
    void bookCancelAndSeeItInTheStats() throws Exception {
        String booking = json(book(customerToken, "10:00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price").value(250.00)));
        long bookingId = ((Number) JsonPath.read(booking, "$.id")).longValue();

        // 10:00 and 09:45 (which would run into 10:00) are gone, 10:30 is still free
        availability()
                .andExpect(jsonPath("$.slots", not(hasItem("10:00"))))
                .andExpect(jsonPath("$.slots", not(hasItem("09:45"))))
                .andExpect(jsonPath("$.slots", hasItem("10:30")));

        perform(get("/api/owner/businesses/{id}/bookings", businessId).param("date", day.toString()), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("Ayşe"));

        perform(post("/api/bookings/{id}/cancel", bookingId), customerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        availability().andExpect(jsonPath("$.slots", hasItem("10:00")));

        perform(get("/api/owner/businesses/{id}/stats", businessId)
                .param("from", day.toString()).param("to", day.toString()), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedBookings").value(0))
                .andExpect(jsonPath("$.cancelledBookings").value(1));
    }

    @Test
    void statsCountRevenueAndTopServices() throws Exception {
        book(customerToken, "10:00").andExpect(status().isCreated());
        book(customerToken, "11:00").andExpect(status().isCreated());

        perform(get("/api/owner/businesses/{id}/stats", businessId)
                .param("from", day.toString()).param("to", day.toString()), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedBookings").value(2))
                .andExpect(jsonPath("$.revenue").value(500.00))
                .andExpect(jsonPath("$.topOfferings[0].name").value("Saç kesimi"))
                .andExpect(jsonPath("$.topOfferings[0].bookings").value(2));
    }

    @Test
    void aTakenSlotCannotBeBookedAgain() throws Exception {
        String otherCustomer = register("deniz@example.com", "Deniz", "CUSTOMER");
        book(customerToken, "14:00").andExpect(status().isCreated());

        book(otherCustomer, "14:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Slot not available"));
        // 14:15 would overlap the first half hour too
        book(otherCustomer, "14:15").andExpect(status().isConflict());
    }

    @Test
    void twoCustomersRacingForTheSameSlotOnlyOneWins() throws Exception {
        String otherCustomer = register("deniz@example.com", "Deniz", "CUSTOMER");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String token : List.of(customerToken, otherCustomer)) {
                Callable<Integer> attempt = () -> {
                    start.await();
                    return book(token, "15:00").andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        Integer confirmed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE status = 'CONFIRMED'", Integer.class);
        assertThat(confirmed).isEqualTo(1);
    }

    @Test
    void theDatabaseItselfRejectsOverlappingBookings() {
        Long customerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'ayse@example.com'", Long.class);
        String insert = """
                INSERT INTO bookings (business_id, offering_id, customer_id, starts_at, ends_at, price, status)
                VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, 250, 'CONFIRMED')
                """;
        jdbcTemplate.update(insert, businessId, offeringId, customerId, "2030-01-01 10:00+03", "2030-01-01 10:30+03");

        assertThatThrownBy(() -> jdbcTemplate.update(insert, businessId, offeringId, customerId,
                "2030-01-01 10:15+03", "2030-01-01 10:45+03"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Touching ranges are fine: [10:00, 10:30) and [10:30, 11:00)
        jdbcTemplate.update(insert, businessId, offeringId, customerId, "2030-01-01 10:30+03", "2030-01-01 11:00+03");
    }

    @Test
    void rolesAndOwnershipAreEnforced() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        perform(post("/api/owner/businesses"), customerToken, """
                {"name": "Benim İşletmem"}
                """).andExpect(status().isForbidden());
        perform(post("/api/bookings"), ownerToken, bookingJson("10:00")).andExpect(status().isForbidden());

        String otherOwner = register("veli@example.com", "Veli", "OWNER");
        perform(get("/api/owner/businesses/{id}", businessId), otherOwner, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void sameBusinessNameGetsANumberedSlug() throws Exception {
        perform(post("/api/owner/businesses"), ownerToken, """
                {"name": "Kuaför Ali"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("kuafor-ali-2"));
    }

    @Test
    void invalidInputGetsClearErrors() throws Exception {
        perform(post("/api/bookings"), customerToken, """
                {"businessSlug": "", "offeringId": null}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.businessSlug").exists())
                .andExpect(jsonPath("$.errors.startsAt").exists());

        perform(put("/api/owner/businesses/{id}/hours", businessId), ownerToken, """
                [{"day": "MONDAY", "opensAt": "09:00", "closesAt": "18:00"},
                 {"day": "MONDAY", "opensAt": "10:00", "closesAt": "12:00"}]
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    // --- Staff ---

    @Test
    void withTwoStaffTwoCustomersCanBookTheSameTime() throws Exception {
        long mehmet = addStaff("Mehmet", "[]");
        long zeynep = addStaff("Zeynep", "[]");
        String deniz = register("deniz@example.com", "Deniz", "CUSTOMER");
        String can = register("can@example.com", "Can", "CUSTOMER");

        book(customerToken, "14:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffName").value("Mehmet"));
        // Mehmet is busy now, but Zeynep is still free at 14:00
        availability(offeringId, mehmet).andExpect(jsonPath("$.slots", not(hasItem("14:00"))));
        availability().andExpect(jsonPath("$.slots", hasItem("14:00")));

        book(deniz, "14:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffName").value("Zeynep"));
        availability().andExpect(jsonPath("$.slots", not(hasItem("14:00"))));
        book(can, "14:00").andExpect(status().isConflict());

        perform(get("/api/owner/businesses/{id}/bookings", businessId)
                .param("date", day.toString()).param("staffId", String.valueOf(zeynep)), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Deniz"));
    }

    @Test
    void customersCanPickAStaffMemberAndStaffOnlyDoTheirServices() throws Exception {
        String beard = json(perform(post("/api/owner/businesses/{id}/offerings", businessId), ownerToken, """
                {"name": "Sakal", "durationMinutes": 20, "price": 150.00}
                """).andExpect(status().isCreated()));
        long beardId = ((Number) JsonPath.read(beard, "$.id")).longValue();
        long mehmet = addStaff("Mehmet", "[" + offeringId + "]");
        long zeynep = addStaff("Zeynep", "[]");

        // Mehmet only cuts hair
        availability(beardId, mehmet).andExpect(status().isNotFound());

        perform(post("/api/bookings"), customerToken, """
                {"businessSlug": "kuafor-ali", "offeringId": %d, "startsAt": "%sT11:00", "staffId": %d}
                """.formatted(offeringId, day, zeynep))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffName").value("Zeynep"));
        availability(offeringId, zeynep).andExpect(jsonPath("$.slots", not(hasItem("11:00"))));
        availability(offeringId, mehmet).andExpect(jsonPath("$.slots", hasItem("11:00")));

        mockMvc.perform(get("/api/businesses/kuafor-ali"))
                .andExpect(jsonPath("$.staff.length()").value(2))
                .andExpect(jsonPath("$.staff[0].name").value("Mehmet"))
                .andExpect(jsonPath("$.staff[0].everyService").value(false))
                .andExpect(jsonPath("$.staff[0].offeringIds[0]").value((int) offeringId))
                .andExpect(jsonPath("$.staff[1].everyService").value(true));

        // A deactivated staff member leaves the public page and takes no new bookings
        perform(delete("/api/owner/businesses/{id}/staff/{staffId}", businessId, zeynep), ownerToken, null)
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/businesses/kuafor-ali")).andExpect(jsonPath("$.staff.length()").value(1));
        availability(offeringId, zeynep).andExpect(status().isNotFound());
    }

    @Test
    void staffServicesMustBelongToTheBusiness() throws Exception {
        perform(post("/api/owner/businesses/{id}/staff", businessId), ownerToken, """
                {"name": "Mehmet", "offeringIds": [999999]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void theDatabaseRejectsOverlapsPerStaffMember() throws Exception {
        long mehmet = addStaff("Mehmet", "[]");
        long zeynep = addStaff("Zeynep", "[]");
        Long customerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'ayse@example.com'", Long.class);
        String insert = """
                INSERT INTO bookings (business_id, offering_id, customer_id, staff_id, starts_at, ends_at, price, status)
                VALUES (?, ?, ?, ?, ?::timestamptz, ?::timestamptz, 250, 'CONFIRMED')
                """;
        jdbcTemplate.update(insert, businessId, offeringId, customerId, mehmet, "2030-01-01 10:00+03", "2030-01-01 10:30+03");

        // Same person, overlapping time: rejected
        assertThatThrownBy(() -> jdbcTemplate.update(insert, businessId, offeringId, customerId, mehmet,
                "2030-01-01 10:15+03", "2030-01-01 10:45+03"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Another person at the same time: fine
        jdbcTemplate.update(insert, businessId, offeringId, customerId, zeynep, "2030-01-01 10:00+03", "2030-01-01 10:30+03");
    }

    @Test
    void staffOwnHoursLimitTheirSlotsAndAnEmptyWeekResetsThem() throws Exception {
        long mehmet = addStaff("Mehmet", "[]");

        perform(put("/api/owner/businesses/{id}/staff/{staffId}/hours", businessId, mehmet), ownerToken,
                weekOf("13:00", "16:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followsBusinessHours").value(false))
                .andExpect(jsonPath("$.workingHours.length()").value(7))
                .andExpect(jsonPath("$.workingHours[0].opensAt").value("13:00"));

        // The business is open 09:00-18:00, but Mehmet (the only staff member) starts at 13:00
        availability()
                .andExpect(jsonPath("$.slots[0]").value("13:00"))
                .andExpect(jsonPath("$.slots[-1]").value("15:30"));
        book(customerToken, "10:00").andExpect(status().isConflict());

        perform(put("/api/owner/businesses/{id}/staff/{staffId}/hours", businessId, mehmet), ownerToken, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followsBusinessHours").value(true));
        availability().andExpect(jsonPath("$.slots[0]").value("09:00"));
    }

    @Test
    void staffHoursAreValidatedLikeOpeningHours() throws Exception {
        long mehmet = addStaff("Mehmet", "[]");

        perform(put("/api/owner/businesses/{id}/staff/{staffId}/hours", businessId, mehmet), ownerToken, """
                [{"day": "MONDAY", "opensAt": "13:00", "closesAt": "16:00"},
                 {"day": "MONDAY", "opensAt": "17:00", "closesAt": "18:00"}]
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void bookingsAreSpreadEvenlyAcrossStaff() throws Exception {
        addStaff("Mehmet", "[]");
        addStaff("Zeynep", "[]");
        String deniz = register("deniz@example.com", "Deniz", "CUSTOMER");
        String can = register("can@example.com", "Can", "CUSTOMER");

        // Everyone is free at each of these times, so the least busy person gets the booking
        book(customerToken, "10:00").andExpect(jsonPath("$.staffName").value("Mehmet"));
        book(deniz, "11:00").andExpect(jsonPath("$.staffName").value("Zeynep"));
        book(can, "12:00").andExpect(jsonPath("$.staffName").value("Mehmet"));

        perform(get("/api/owner/businesses/{id}/stats", businessId)
                .param("from", day.toString()).param("to", day.toString()), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStaff.length()").value(2))
                .andExpect(jsonPath("$.byStaff[0].name").value("Mehmet"))
                .andExpect(jsonPath("$.byStaff[0].bookings").value(2))
                .andExpect(jsonPath("$.byStaff[1].name").value("Zeynep"))
                .andExpect(jsonPath("$.byStaff[1].bookings").value(1));
    }

    @Test
    void withTwoStaffTwoCustomersRacingForTheSameTimeBothGetABooking() throws Exception {
        addStaff("Mehmet", "[]");
        addStaff("Zeynep", "[]");
        String otherCustomer = register("deniz@example.com", "Deniz", "CUSTOMER");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String token : List.of(customerToken, otherCustomer)) {
                Callable<Integer> attempt = () -> {
                    start.await();
                    return book(token, "15:00").andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            // Both may be given Mehmet at first; the one the database turns away retries and gets Zeynep
            assertThat(statuses).containsExactly(201, 201);
        } finally {
            pool.shutdownNow();
        }
        List<String> staff = jdbcTemplate.queryForList("""
                SELECT s.name FROM bookings b JOIN staff s ON s.id = b.staff_id
                WHERE b.status = 'CONFIRMED' ORDER BY s.name
                """, String.class);
        assertThat(staff).containsExactly("Mehmet", "Zeynep");
    }

    private static String weekOf(String opensAt, String closesAt) {
        StringBuilder week = new StringBuilder("[");
        for (DayOfWeek day : DayOfWeek.values()) {
            if (week.length() > 1) {
                week.append(", ");
            }
            week.append("""
                    {"day": "%s", "opensAt": "%s", "closesAt": "%s"}""".formatted(day, opensAt, closesAt));
        }
        return week.append("]").toString();
    }

    // --- Online payment (fake provider, see src/test/resources/config/application.yml) ---

    @Test
    void payOnlineThenCancelAndGetARefund() throws Exception {
        long bookingId = ((Number) JsonPath.read(json(book(customerToken, "10:00")
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))), "$.id")).longValue();

        perform(post("/api/bookings/{id}/payment", bookingId), customerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checkoutUrl").value(startsWith("https://payments.example.test/checkout/")));
        String checkoutId = jdbcTemplate.queryForObject(
                "SELECT payment_checkout_id FROM bookings WHERE id = ?", String.class, bookingId);
        String paid = """
                {"checkoutId": "%s", "paymentId": "pay_123"}
                """.formatted(checkoutId);

        // Anyone can call the webhook URL; without the right signature nothing happens
        webhook(paid, "wrong-secret")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid webhook"));
        webhook(paid, null).andExpect(status().isBadRequest());

        webhook(paid, "test-webhook-secret").andExpect(status().isOk());
        // Providers resend webhooks; the second one changes nothing
        webhook(paid, "test-webhook-secret").andExpect(status().isOk());
        perform(get("/api/bookings"), customerToken, null)
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PAID"));
        perform(post("/api/bookings/{id}/payment", bookingId), customerToken, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Already paid"));

        perform(post("/api/bookings/{id}/cancel", bookingId), customerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        assertThat(((FakePaymentGateway) paymentGateway).refunds()).contains("pay_123");
    }

    @Test
    void customersCanOnlyPayTheirOwnBookings() throws Exception {
        String otherCustomer = register("deniz@example.com", "Deniz", "CUSTOMER");
        long bookingId = ((Number) JsonPath.read(json(book(customerToken, "10:00")), "$.id")).longValue();

        perform(post("/api/bookings/{id}/payment", bookingId), otherCustomer, null)
                .andExpect(status().isNotFound());
        perform(post("/api/bookings/{id}/payment", bookingId), ownerToken, null)
                .andExpect(status().isForbidden());
    }

    private ResultActions webhook(String body, String signature) throws Exception {
        var request = post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request.header("Fake-Signature", signature);
        }
        return mockMvc.perform(request);
    }

    // --- Reminders ---

    @Test
    void remindersGoOutOnceAndOnlyForBookingsMadeWellAhead() {
        insertBooking("20 hours", "CONFIRMED", "3 days");
        insertBooking("5 hours", "CONFIRMED", "1 hour");
        insertBooking("30 hours", "CONFIRMED", "3 days");
        insertBooking("21 hours", "CANCELLED", "3 days");

        assertThat(reminderService.sendDueReminders()).isEqualTo(1);
        // Only the one starting in 20 hours, booked 3 days ago; the 5-hour one was booked too late,
        // the 30-hour one is not due yet and the cancelled one needs no reminder
        List<Long> reminded = jdbcTemplate.queryForList("""
                SELECT round(extract(epoch FROM starts_at - now()) / 3600)::bigint FROM bookings
                WHERE reminder_sent_at IS NOT NULL
                """, Long.class);
        assertThat(reminded).containsExactly(20L);

        assertThat(reminderService.sendDueReminders()).isZero();
    }

    /** A booking for Ayşe starting {@code startsIn} from now (e.g. "20 hours"), made {@code madeAgo} ago. */
    private void insertBooking(String startsIn, String status, String madeAgo) {
        Long customerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'ayse@example.com'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO bookings (business_id, offering_id, customer_id, starts_at, ends_at, price, status,
                                      cancelled_at, created_at)
                VALUES (?, ?, ?, now() + ?::interval, now() + ?::interval + interval '30 minutes', 250, ?,
                        CASE WHEN ? = 'CANCELLED' THEN now() END, now() - ?::interval)
                """, businessId, offeringId, customerId, startsIn, startsIn, status, status, madeAgo);
    }

    // --- Holidays and days off ---

    @Test
    void aHolidayClosesTheDayUntilItIsRemoved() throws Exception {
        String closure = json(perform(post("/api/owner/businesses/{id}/closures", businessId), ownerToken, """
                {"startsOn": "%s", "endsOn": "%s", "reason": "Bayram"}
                """.formatted(day, day.plusDays(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffId").doesNotExist())
                .andExpect(jsonPath("$.reason").value("Bayram")));
        long closureId = ((Number) JsonPath.read(closure, "$.id")).longValue();

        availability()
                .andExpect(jsonPath("$.slots.length()").value(0))
                .andExpect(jsonPath("$.closedReason").value("Bayram"));
        book(customerToken, "10:00").andExpect(status().isConflict());
        perform(get("/api/owner/businesses/{id}/closures", businessId), ownerToken, null)
                .andExpect(jsonPath("$.length()").value(1));

        perform(delete("/api/owner/businesses/{id}/closures/{closureId}", businessId, closureId), ownerToken, null)
                .andExpect(status().isNoContent());
        availability()
                .andExpect(jsonPath("$.slots[0]").value("09:00"))
                .andExpect(jsonPath("$.closedReason").doesNotExist());
    }

    @Test
    void daysWithBookingsCannotBeClosed() throws Exception {
        book(customerToken, "10:00").andExpect(status().isCreated());

        perform(post("/api/owner/businesses/{id}/closures", businessId), ownerToken, """
                {"startsOn": "%s", "endsOn": "%s"}
                """.formatted(day.minusDays(1), day))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Bookings in this period"));
    }

    @Test
    void aStaffMemberOnLeaveTakesNoBookings() throws Exception {
        long mehmet = addStaff("Mehmet", "[]");
        addStaff("Zeynep", "[]");

        perform(post("/api/owner/businesses/{id}/closures", businessId), ownerToken, """
                {"startsOn": "%s", "endsOn": "%s", "staffId": %d, "reason": "Yıllık izin"}
                """.formatted(day, day, mehmet))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffName").value("Mehmet"));

        // The business stays open; only Zeynep works that day
        availability().andExpect(jsonPath("$.closedReason").doesNotExist());
        availability(offeringId, mehmet).andExpect(jsonPath("$.slots.length()").value(0));
        book(customerToken, "10:00").andExpect(jsonPath("$.staffName").value("Zeynep"));
    }

    @Test
    void closuresAreValidated() throws Exception {
        perform(post("/api/owner/businesses/{id}/closures", businessId), ownerToken, """
                {"startsOn": "%s", "endsOn": "%s"}
                """.formatted(day, day.minusDays(1)))
                .andExpect(status().isBadRequest());
        perform(post("/api/owner/businesses/{id}/closures", businessId), ownerToken, """
                {"startsOn": "2020-01-01", "endsOn": "2020-01-02"}
                """)
                .andExpect(status().isBadRequest());
        perform(post("/api/owner/businesses/{id}/closures", businessId), ownerToken, """
                {"startsOn": "%s", "endsOn": "%s", "staffId": 999999}
                """.formatted(day, day))
                .andExpect(status().isNotFound());
    }

    private long addStaff(String name, String offeringIdsJson) throws Exception {
        String staff = json(perform(post("/api/owner/businesses/{id}/staff", businessId), ownerToken, """
                {"name": "%s", "offeringIds": %s}
                """.formatted(name, offeringIdsJson)).andExpect(status().isCreated()));
        return ((Number) JsonPath.read(staff, "$.id")).longValue();
    }

    private ResultActions availability(long offering, long staffId) throws Exception {
        return mockMvc.perform(get("/api/businesses/kuafor-ali/availability")
                .param("offeringId", String.valueOf(offering))
                .param("date", day.toString())
                .param("staffId", String.valueOf(staffId)));
    }

    private ResultActions availability() throws Exception {
        return mockMvc.perform(get("/api/businesses/kuafor-ali/availability")
                .param("offeringId", String.valueOf(offeringId))
                .param("date", day.toString()));
    }

    private ResultActions book(String token, String time) throws Exception {
        return perform(post("/api/bookings"), token, bookingJson(time));
    }

    private String bookingJson(String time) {
        return """
                {"businessSlug": "kuafor-ali", "offeringId": %d, "startsAt": "%sT%s"}
                """.formatted(offeringId, day, time);
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                  String token, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private String register(String email, String name, String role) throws Exception {
        String response = json(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123", "displayName": "%s", "role": "%s"}
                                """.formatted(email, name, role)))
                .andExpect(status().isCreated()));
        return JsonPath.read(response, "$.accessToken");
    }

    private static String json(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
