package com.doova.ktab.interactivestorytelling.util;

import com.doova.ktab.interactivestorytelling.model.SessionState;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SessionStateConverter implements AttributeConverter<SessionState, String> {

    @Override
    public String convertToDatabaseColumn(SessionState attribute) {
        return JsonUtil.write(attribute);
    }

    @Override
    public SessionState convertToEntityAttribute(String dbData) {
        return JsonUtil.read(dbData, SessionState.class);
    }
}
