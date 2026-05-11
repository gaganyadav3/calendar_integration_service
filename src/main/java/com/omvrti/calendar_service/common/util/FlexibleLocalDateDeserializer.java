package com.omvrti.calendar_service.common.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class FlexibleLocalDateDeserializer extends StdDeserializer<LocalDate> {

    public FlexibleLocalDateDeserializer() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String value = p.getText().trim();

        if (value.length() == 10) {
            // "2026-04-23" → plain date
            return LocalDate.parse(value);
        } else {
            // "2026-04-23T00:00:00.000Z" → parse as OffsetDateTime, extract date
            return OffsetDateTime.parse(value).toLocalDate();
        }
    }
}

