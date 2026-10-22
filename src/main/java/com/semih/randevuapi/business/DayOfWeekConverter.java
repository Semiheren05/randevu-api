package com.semih.randevuapi.business;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;

/**
 * Stores {@link DayOfWeek} as ISO numbers (Monday = 1 ... Sunday = 7) in a SMALLINT column.
 * JPA's built-in enum mapping would store 0-6 by position, which is easy to misread in SQL.
 */
@Converter
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek day) {
        return day == null ? null : (short) day.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short value) {
        return value == null ? null : DayOfWeek.of(value);
    }
}
