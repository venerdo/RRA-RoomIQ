package rw.rra.roomiq.room.domain.mapper;

import org.springframework.stereotype.Component;
import rw.rra.roomiq.room.domain.dto.FacilityTypeDto;
import rw.rra.roomiq.room.domain.dto.MaintenancePeriodDto;
import rw.rra.roomiq.room.domain.dto.RoomDto;
import rw.rra.roomiq.room.domain.dto.RoomFacilityDto;
import rw.rra.roomiq.room.domain.dto.RoomPhotoDto;
import rw.rra.roomiq.room.domain.dto.RoomRuleAllowedDepartmentDto;
import rw.rra.roomiq.room.domain.dto.RoomRuleDto;
import rw.rra.roomiq.room.domain.dto.RoomStatusHistoryDto;
import rw.rra.roomiq.room.domain.dto.RoomTypeDto;
import rw.rra.roomiq.room.domain.entity.FacilityType;
import rw.rra.roomiq.room.domain.entity.MaintenancePeriod;
import rw.rra.roomiq.room.domain.entity.Room;
import rw.rra.roomiq.room.domain.entity.RoomFacility;
import rw.rra.roomiq.room.domain.entity.RoomPhoto;
import rw.rra.roomiq.room.domain.entity.RoomRule;
import rw.rra.roomiq.room.domain.entity.RoomRuleAllowedDepartment;
import rw.rra.roomiq.room.domain.entity.RoomStatusHistory;
import rw.rra.roomiq.room.domain.entity.RoomType;

import java.util.List;
import java.util.UUID;

@Component
public class RoomMapper {
    public RoomTypeDto toDto(RoomType roomType) {
        return new RoomTypeDto(roomType.getId(), roomType.getCode(), roomType.getName(), roomType.isActive());
    }

    public FacilityTypeDto toDto(FacilityType facilityType) {
        return new FacilityTypeDto(facilityType.getId(), facilityType.getCode(), facilityType.getName(),
                facilityType.getCategory(), facilityType.isActive());
    }

    public RoomDto toDto(Room room) {
        return new RoomDto(room.getId(), room.getFloorId(), room.getOfficeBuildingId(), room.getRoomType().getId(),
                room.getName(), room.getCode(), room.getDescription(), room.getCapacity(), room.getRoomClass(),
                room.getStatus(), room.getVersion(), room.getCreatedAt(), room.getDeletedAt());
    }

    public RoomFacilityDto toDto(RoomFacility facility) {
        return new RoomFacilityDto(facility.getId(), facility.getRoom().getId(), facility.getFacilityType().getId(),
                facility.getQuantity(), facility.getState(), facility.getLastServicedAt());
    }

    public RoomPhotoDto toDto(RoomPhoto photo) {
        return new RoomPhotoDto(photo.getId(), photo.getRoom().getId(), photo.getCloudinaryPublicId(),
                photo.getSecureUrl(), photo.getSortOrder(), photo.isPrimary(), photo.isApprovedForPublic(),
                photo.getCreatedAt());
    }

    public RoomRuleDto toDto(RoomRule rule) {
        return toDto(rule, List.of());
    }

    public RoomRuleDto toDto(RoomRule rule, List<UUID> allowedDepartmentIds) {
        return new RoomRuleDto(rule.getId(), rule.getRoom() == null ? null : rule.getRoom().getId(),
                rule.getOfficeBuildingId(), rule.getMinDurationMinutes(), rule.getMaxDurationMinutes(),
                rule.getMinAdvanceMinutes(), rule.getMaxAdvanceDays(), rule.getCancellationDeadlineMinutes(),
                rule.isRecurringAllowed(), rule.isExternalGuestsAllowed(), rule.isApprovalRequired(),
                rule.isOutsideHoursAllowed(), rule.getReleaseBufferMinutes(), rule.isActive(), rule.getEffectiveFrom(),
                List.copyOf(allowedDepartmentIds));
    }

    public RoomRuleAllowedDepartmentDto toDto(RoomRuleAllowedDepartment allowedDepartment) {
        return new RoomRuleAllowedDepartmentDto(allowedDepartment.getId(),
                allowedDepartment.getRoomRule().getId(), allowedDepartment.getDepartmentId());
    }

    public RoomStatusHistoryDto toDto(RoomStatusHistory history) {
        return new RoomStatusHistoryDto(history.getId(), history.getRoom().getId(), history.getOldStatus(),
                history.getNewStatus(), history.getChangedByUserId(), history.getReason(), history.getChangedAt());
    }

    public MaintenancePeriodDto toDto(MaintenancePeriod maintenancePeriod) {
        return new MaintenancePeriodDto(maintenancePeriod.getId(), maintenancePeriod.getRoom().getId(),
                maintenancePeriod.getPeriod().getValue(), maintenancePeriod.getReason(),
                maintenancePeriod.getCreatedByUserId());
    }
}