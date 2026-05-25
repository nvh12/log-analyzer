package com.nvh12.log_processing.infrastructure.persistence.mapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Converter
public class JsonStringMapConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null) return null;
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return MAPPER.readValue(dbData, TYPE_REF);
    }
}
