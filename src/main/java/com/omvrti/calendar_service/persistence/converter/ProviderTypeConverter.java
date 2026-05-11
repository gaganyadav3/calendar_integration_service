package com.omvrti.calendar_service.persistence.converter;

import com.omvrti.calendar_service.common.enums.ProviderType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Converter(autoApply = true)
@Slf4j
public class ProviderTypeConverter implements AttributeConverter<ProviderType, String> {

    @Override
    public String convertToDatabaseColumn(ProviderType attribute) {
        if (attribute == null) {
            return null;
        }

        String dbValue = attribute.toDatabaseValue();
        log.debug("Provider converter - storing provider enum {} as DB value {}", attribute, dbValue);
        return dbValue;
    }

    @Override
    public ProviderType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        ProviderType parsed = ProviderType.parse(dbData);
        log.debug("Provider converter - parsed DB provider value {} into enum {}", dbData, parsed);
        return parsed;
    }
}
