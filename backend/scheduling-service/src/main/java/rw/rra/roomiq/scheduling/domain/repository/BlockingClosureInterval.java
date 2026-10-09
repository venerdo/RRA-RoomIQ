package rw.rra.roomiq.scheduling.domain.repository;

import java.time.Instant;

public interface BlockingClosureInterval {
    Instant getStartsAt();

    Instant getEndsAt();
}
