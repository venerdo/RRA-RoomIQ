package rw.rra.roomiq.scheduling.domain.dto;

import rw.rra.roomiq.scheduling.domain.entity.ClosurePeriod;

import java.util.UUID;

public record ClosurePeriodResponse(
        UUID id,
        UUID officeBuildingId,
        String period,
        String reason,
        boolean blocksBooking) {
    public static ClosurePeriodResponse from(ClosurePeriod closurePeriod) {
        return new ClosurePeriodResponse(closurePeriod.getId(), closurePeriod.getOfficeBuildingId(),
                closurePeriod.getPeriod().getValue(), closurePeriod.getReason(), closurePeriod.isBlocksBooking());
    }
}