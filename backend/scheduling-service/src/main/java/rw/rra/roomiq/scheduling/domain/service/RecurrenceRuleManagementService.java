package rw.rra.roomiq.scheduling.domain.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.domain.dto.RecurrenceOccurrencesResponse;
import rw.rra.roomiq.scheduling.domain.dto.RecurrenceRuleListQuery;
import rw.rra.roomiq.scheduling.domain.dto.RecurrenceRulePageResponse;
import rw.rra.roomiq.scheduling.domain.dto.RecurrenceRuleResponse;
import rw.rra.roomiq.scheduling.domain.dto.SetRecurrenceRuleRequest;
import rw.rra.roomiq.scheduling.domain.entity.RecurrenceRule;
import rw.rra.roomiq.scheduling.domain.recurrence.RecurrencePattern;
import rw.rra.roomiq.scheduling.domain.repository.RecurrenceRuleRepository;

import java.time.ZoneId;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RecurrenceRuleManagementService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;

    public RecurrenceRuleManagementService(RecurrenceRuleRepository recurrenceRuleRepository) {
        this.recurrenceRuleRepository = recurrenceRuleRepository;
    }

    public RecurrenceRulePageResponse list(RecurrenceRuleListQuery query) {
        Page<RecurrenceRule> page = recurrenceRuleRepository.findAll(PageRequest.of(
                query.pageNumber(), query.pageSize(),
                Sort.by(Sort.Order.asc("startsOn"), Sort.Order.asc("id"))));
        return new RecurrenceRulePageResponse(page.getContent().stream()
                .map(RecurrenceRuleResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public RecurrenceRuleResponse get(UUID recurrenceRuleId) {
        return RecurrenceRuleResponse.from(requireRule(recurrenceRuleId));
    }

    @Transactional
    public RecurrenceRuleResponse create(SetRecurrenceRuleRequest request, UUID actorUserId) {
        if (actorUserId == null) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "An authenticated creator is required");
        }
        ValidatedRule validated = validate(request);
        return RecurrenceRuleResponse.from(save(new RecurrenceRule(validated.pattern().normalizedRule(),
                request.startsOn(), validated.pattern().until(), validated.pattern().count(),
                validated.timezone(), actorUserId)));
    }

    @Transactional
    public RecurrenceRuleResponse update(UUID recurrenceRuleId, SetRecurrenceRuleRequest request) {
        RecurrenceRule rule = requireRule(recurrenceRuleId);
        ValidatedRule validated = validate(request);
        rule.update(validated.pattern().normalizedRule(), request.startsOn(), validated.pattern().until(),
                validated.pattern().count(), validated.timezone());
        return RecurrenceRuleResponse.from(save(rule));
    }

    public RecurrenceOccurrencesResponse occurrences(UUID recurrenceRuleId) {
        RecurrenceRule rule = requireRule(recurrenceRuleId);
        RecurrencePattern pattern = RecurrencePattern.parse(rule.getRrule(), rule.getStartsOn());
        return new RecurrenceOccurrencesResponse(rule.getId(), rule.getTimezone(), pattern.occurrences());
    }

    @Transactional
    public void delete(UUID recurrenceRuleId) {
        recurrenceRuleRepository.delete(requireRule(recurrenceRuleId));
    }

    private ValidatedRule validate(SetRecurrenceRuleRequest request) {
        String timezone = request.timezone().trim();
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE",
                    "timezone must be a recognized IANA zone ID");
        }
        RecurrencePattern pattern = RecurrencePattern.parse(request.rrule(), request.startsOn());
        return new ValidatedRule(pattern, timezone);
    }

    private RecurrenceRule requireRule(UUID recurrenceRuleId) {
        return recurrenceRuleRepository.findById(recurrenceRuleId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "RECURRENCE_RULE_NOT_FOUND",
                        "Recurrence rule was not found"));
    }

    private RecurrenceRule save(RecurrenceRule rule) {
        try {
            return recurrenceRuleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "RECURRENCE_RULE_CONFLICT",
                    "The recurrence rule conflicts with persisted constraints");
        }
    }

    private record ValidatedRule(RecurrencePattern pattern, String timezone) {
    }
}
