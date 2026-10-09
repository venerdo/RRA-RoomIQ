package rw.rra.roomiq.scheduling.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

public interface RoomAvailabilityClient {
    List<RoomReference> rooms(UUID officeBuildingId);

    List<RoomRuleReference> roomRules(UUID roomId);

    List<RoomRuleReference> buildingRules(UUID officeBuildingId);

    List<RoomFacilityReference> facilities(UUID roomId);

    List<MaintenancePeriodReference> maintenancePeriods(UUID roomId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoomReference(UUID id, UUID officeBuildingId, String name, String code, int capacity, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoomRuleReference(UUID id, UUID roomId, UUID officeBuildingId, Integer minDurationMinutes,
                             Integer maxDurationMinutes, Integer minAdvanceMinutes, Integer maxAdvanceDays,
                             boolean recurringAllowed, boolean outsideHoursAllowed,
                             int releaseBufferMinutes, boolean active, java.time.Instant effectiveFrom,
                             List<UUID> allowedDepartmentIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoomFacilityReference(UUID facilityTypeId, String state) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MaintenancePeriodReference(String period) {
    }
}
