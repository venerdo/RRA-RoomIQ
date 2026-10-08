package rw.rra.roomiq.scheduling.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import rw.rra.roomiq.scheduling.domain.entity.Holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID>, JpaSpecificationExecutor<Holiday> {
    List<Holiday> findAllByHolidayDateAndActiveTrue(LocalDate holidayDate);
    boolean existsByWorkingCalendar_Id(UUID workingCalendarId);
}