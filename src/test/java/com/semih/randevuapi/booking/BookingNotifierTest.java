package com.semih.randevuapi.booking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingNotifierTest {

    private static final BookingNotification CONFIRMED = new BookingNotification(
            BookingNotification.Type.CONFIRMED, 5L, "Ayşe", "ayse@example.com", "ali@example.com",
            "Kuaför Ali", "Saç kesimi", null, LocalDateTime.of(2026, 10, 23, 14, 30));

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    private JavaMailSender mailSender;

    private final BookingNotifier notifier =
            new BookingNotifier(null, new NotificationProperties("randevu@example.com"));

    @Test
    void customerAndOwnerBothGetAnEmail() {
        List<SimpleMailMessage> mails = notifier.buildMails(CONFIRMED);

        SimpleMailMessage toCustomer = mails.get(0);
        assertThat(toCustomer.getTo()).containsExactly("ayse@example.com");
        assertThat(toCustomer.getFrom()).isEqualTo("randevu@example.com");
        assertThat(toCustomer.getSubject()).isEqualTo("Randevunuz onaylandı: Kuaför Ali");
        assertThat(toCustomer.getText()).contains("Saç kesimi", "23 Ekim 2026 Cuma, 14:30");

        SimpleMailMessage toOwner = mails.get(1);
        assertThat(toOwner.getTo()).containsExactly("ali@example.com");
        assertThat(toOwner.getReplyTo()).isEqualTo("ayse@example.com");
        assertThat(toOwner.getSubject()).startsWith("Yeni randevu: Ayşe");
    }

    @Test
    void cancellationEmailsSaySo() {
        BookingNotification cancelled = new BookingNotification(BookingNotification.Type.CANCELLED, 5L, "Ayşe",
                "ayse@example.com", "ali@example.com", "Kuaför Ali", "Saç kesimi", null, LocalDateTime.of(2026, 10, 23, 14, 30));

        List<SimpleMailMessage> mails = notifier.buildMails(cancelled);

        assertThat(mails.get(0).getSubject()).isEqualTo("Randevunuz iptal edildi: Kuaför Ali");
        assertThat(mails.get(1).getSubject()).startsWith("Randevu iptal edildi: Ayşe");
    }

    @Test
    void theStaffMemberIsNamedWhenThereIsOne() {
        BookingNotification withStaff = new BookingNotification(BookingNotification.Type.CONFIRMED, 7L, "Ayşe",
                "ayse@example.com", "ali@example.com", "Kuaför Ali", "Saç kesimi", "Mehmet",
                LocalDateTime.of(2026, 10, 23, 14, 30));

        List<SimpleMailMessage> mails = notifier.buildMails(withStaff);

        assertThat(mails).allSatisfy(mail -> assertThat(mail.getText()).contains("Saç kesimi (Mehmet)"));
    }

    @Test
    void aReminderGoesToTheCustomerOnly() {
        BookingNotification reminder = new BookingNotification(BookingNotification.Type.REMINDER, 8L, "Ayşe",
                "ayse@example.com", "ali@example.com", "Kuaför Ali", "Saç kesimi", "Mehmet",
                LocalDateTime.of(2026, 10, 23, 14, 30));

        List<SimpleMailMessage> mails = notifier.buildMails(reminder);

        assertThat(mails).hasSize(1);
        assertThat(mails.getFirst().getTo()).containsExactly("ayse@example.com");
        assertThat(mails.getFirst().getSubject()).isEqualTo("Randevu hatırlatması: Kuaför Ali");
        assertThat(mails.getFirst().getText()).contains("yaklaşıyor", "Saç kesimi (Mehmet)", "14:30");
    }

    @Test
    void oneFailingEmailDoesNotStopTheOtherOrThrow() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        BookingNotifier withSender = new BookingNotifier(mailSenderProvider, new NotificationProperties(null));

        assertThatCode(() -> withSender.onBooking(CONFIRMED)).doesNotThrowAnyException();
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void withoutAMailServerNothingIsSent() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        BookingNotifier withoutSender = new BookingNotifier(mailSenderProvider, new NotificationProperties(null));

        assertThatCode(() -> withoutSender.onBooking(CONFIRMED)).doesNotThrowAnyException();
    }

    @Test
    void lineBreaksInNamesCannotReachTheSubject() {
        BookingNotification sneaky = new BookingNotification(BookingNotification.Type.CONFIRMED, 6L,
                "Ayşe\r\nBcc: herkes@example.com", "ayse@example.com", "ali@example.com",
                "Kuaför Ali", "Saç kesimi", null, LocalDateTime.of(2026, 10, 23, 14, 30));

        assertThat(notifier.buildMails(sneaky)).allSatisfy(mail ->
                assertThat(mail.getSubject()).doesNotContain("\r", "\n"));
    }
}
