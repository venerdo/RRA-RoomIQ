package rw.rra.roomiq.scheduling.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.scheduling.domain.entity.WorkingDayWindow;

import java.util.List;
import java.util.UUID;

public interface WorkingDayWindowRepository extends JpaRepository<WorkingDayWindow, UUID> {
    List<WorkingDayWindow> findAllByWorkingCalendar_IdOrderByDayOfWeek(UUID workingCalendarId);
    boolean existsByWorkingCalendar_Id(UUID workingCalendarId);
    java.util.Optional<WorkingDayWindow> findByIdAndWorkingCalendar_Id(UUID id, UUID workingCalendarId);
}