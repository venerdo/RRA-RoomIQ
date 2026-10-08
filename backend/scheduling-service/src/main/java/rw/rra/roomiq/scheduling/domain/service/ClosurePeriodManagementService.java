package rw.rra.roomiq.scheduling.domain.service;

import jakarta.persistence.criteria.Predicate;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.domain.dto.ClosurePeriodListQuery;
import rw.rra.roomiq.scheduling.domain.dto.ClosurePeriodPageResponse;
import rw.rra.roomiq.scheduling.domain.dto.ClosurePeriodResponse;
import rw.rra.roomiq.scheduling.domain.dto.SetClosurePeriodRequest;
import rw.rra.roomiq.scheduling.domain.entity.ClosurePeriod;
import rw.rra.roomiq.scheduling.domain.repository.ClosurePeriodRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClosurePeriodManagementService {
    private final ClosurePeriodRepository closureRepository;

    public ClosurePeriodManagementService(ClosurePeriodRepository closureRepository) {
        this.closureRepository = closureRepository;
    }

    public ClosurePeriodPageResponse list(ClosurePeriodListQuery query) {
        if (Boolean.TRUE.equals(query.nationwide()) && query.officeBuildingId() != null) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_CLOSURE_SCOPE",
                    "Nationwide filtering cannot include an office-building ID");
        }
        Specification<ClosurePeriod> specification = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.officeBuildingId() != null) {
                predicates.add(builder.equal(root.get("officeBuildingId"), query.officeBuildingId()));
            }
            if (Boolean.TRUE.equals(query.nationwide())) {
                predicates.add(builder.isNull(root.get("officeBuildingId")));
            } else if (Boolean.FALSE.equals(query.nationwide()) && query.officeBuildingId() == null) {
                predicates.add(builder.isNotNull(root.get("officeBuildingId")));
            }
            if (query.blocksBooking() != null) {
                predicates.add(builder.equal(root.get("blocksBooking"), query.blocksBooking()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        String direction = query.sortDirection() == null ? "ASC" : query.sortDirection().toUpperCase(Locale.ROOT);
        Page<ClosurePeriod> page = closureRepository.findAll(specification, PageRequest.of(
                query.pageNumber(), query.pageSize(), Sort.by(Sort.Direction.valueOf(direction), "id")));
        return new ClosurePeriodPageResponse(page.getContent().stream().map(ClosurePeriodResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), "id", direction);
    }

    public ClosurePeriodResponse get(UUID closurePeriodId) {
        return ClosurePeriodResponse.from(requireClosure(closurePeriodId));
    }

    @Transactional
    public ClosurePeriodResponse create(SetClosurePeriodRequest request) {
        return ClosurePeriodResponse.from(save(new ClosurePeriod(request.officeBuildingId(),
                range(request), normalize(request.reason()), request.blocksBooking())));
    }

    @Transactional
    public ClosurePeriodResponse update(UUID closurePeriodId, SetClosurePeriodRequest request) {
        ClosurePeriod closure = requireClosure(closurePeriodId);
        closure.update(request.officeBuildingId(), range(request), normalize(request.reason()), request.blocksBooking());
        return ClosurePeriodResponse.from(save(closure));
    }

    @Transactional
    public void delete(UUID closurePeriodId) {
        closureRepository.delete(requireClosure(closurePeriodId));
    }

    private ClosurePeriod requireClosure(UUID closurePeriodId) {
        return closureRepository.findById(closurePeriodId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "CLOSURE_PERIOD_NOT_FOUND",
                        "Closure period was not found"));
    }

    private ClosurePeriod save(ClosurePeriod closure) {
        try {
            return closureRepository.saveAndFlush(closure);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "CLOSURE_PERIOD_CONFLICT",
                    "The closure period conflicts with persisted constraints");
        }
    }

    private static PGobject range(SetClosurePeriodRequest request) {
        if (request.startsAt() == null || request.endsAt() == null || !request.endsAt().isAfter(request.startsAt())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_CLOSURE_PERIOD",
                    "endsAt must be later than startsAt");
        }
        try {
            PGobject period = new PGobject();
            period.setType("tstzrange");
            period.setValue("[" + request.startsAt() + "," + request.endsAt() + ")");
            return period;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to represent the closure period", exception);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}