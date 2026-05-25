package com.nvh12.dashboard.infrastructure.persistence.mapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Converter
public class JsonDoubleMapConverter implements AttributeConverter<Map<String, Double>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Double>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Double> attribute) {
        if (attribute == null) return null;
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public Map<String, Double> convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return MAPPER.readValue(dbData, TYPE_REF);
    }
}
