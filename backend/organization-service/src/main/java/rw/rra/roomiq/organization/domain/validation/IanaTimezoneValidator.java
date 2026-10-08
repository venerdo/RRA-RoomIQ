package rw.rra.roomiq.organization.domain.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

public class IanaTimezoneValidator implements ConstraintValidator<IanaTimezone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank()
                || ZoneId.getAvailableZoneIds().contains(value.trim());
    }
}