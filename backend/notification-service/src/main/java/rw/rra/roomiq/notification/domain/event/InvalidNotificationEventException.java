package rw.rra.roomiq.notification.domain.event;

public class InvalidNotificationEventException extends RuntimeException {
    public InvalidNotificationEventException(String message) {
        super(message);
    }

    public InvalidNotificationEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
