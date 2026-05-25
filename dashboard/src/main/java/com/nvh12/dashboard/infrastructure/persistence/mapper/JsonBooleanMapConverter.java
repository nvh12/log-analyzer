package com.nvh12.dashboard.infrastructure.persistence.mapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Converter
public class JsonBooleanMapConverter implements AttributeConverter<Map<String, Boolean>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Boolean>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Boolean> attribute) {
        if (attribute == null) return null;
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public Map<String, Boolean> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return MAPPER.readValue(dbData, TYPE_REF);
    }
}
