package com.semih.randevuapi.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Emails the customer and the business owner when a booking is made or cancelled.
 *
 * <ul>
 *   <li>{@code @TransactionalEventListener}: only after the booking is really committed.</li>
 *   <li>{@code @Async}: on another thread, so nobody waits for the mail server.</li>
 *   <li>A mail failure is logged, never thrown: the booking itself already succeeded.</li>
 * </ul>
 *
 * <p>Spring only creates a {@link JavaMailSender} when {@code spring.mail.host} is set,
 * so without mail settings this listener does nothing.
 */
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMMM yyyy EEEE, HH:mm", Locale.forLanguageTag("tr-TR"));

    private final ObjectProvider<JavaMailSender> mailSender;
    private final NotificationProperties properties;

    public BookingNotifier(ObjectProvider<JavaMailSender> mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    public void onBooking(BookingNotification notification) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return;
        }
        for (SimpleMailMessage mail : buildMails(notification)) {
            try {
                sender.send(mail);
            } catch (MailException e) {
                log.warn("Could not send booking email for booking {}: {}", notification.bookingId(), e.getMessage());
            }
        }
    }

    List<SimpleMailMessage> buildMails(BookingNotification n) {
        String when = n.startsAt().format(WHEN);
        boolean confirmed = n.type() == BookingNotification.Type.CONFIRMED;

        SimpleMailMessage toCustomer = mail(n.customerEmail(),
                (confirmed ? "Randevunuz onaylandı: " : "Randevunuz iptal edildi: ") + n.businessName(),
                """
                Merhaba %s,

                %s randevunuz %s.

                İşletme: %s
                Hizmet: %s
                Tarih: %s
                """.formatted(n.customerName(), n.businessName(), confirmed ? "onaylandı" : "iptal edildi",
                        n.businessName(), n.offeringName(), when));

        SimpleMailMessage toOwner = mail(n.ownerEmail(),
                (confirmed ? "Yeni randevu: " : "Randevu iptal edildi: ") + n.customerName() + ", " + when,
                """
                Müşteri: %s <%s>
                Hizmet: %s
                Tarih: %s
                Durum: %s
                """.formatted(n.customerName(), n.customerEmail(), n.offeringName(), when,
                        confirmed ? "Onaylandı" : "İptal edildi"));
        // The owner can answer the customer directly from the email
        toOwner.setReplyTo(n.customerEmail());

        return List.of(toCustomer, toOwner);
    }

    private SimpleMailMessage mail(String to, String subject, String text) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        if (properties.mailFrom() != null && !properties.mailFrom().isBlank()) {
            mail.setFrom(properties.mailFrom());
        }
        // Names come from users; line breaks in a header could inject extra headers
        mail.setSubject(subject.replaceAll("[\\r\\n]+", " "));
        mail.setText(text);
        return mail;
    }
}
