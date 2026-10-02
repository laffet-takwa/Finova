package com.finova.notification.mapper;

import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationPreference;
import com.finova.notification.dto.NotificationResponse;
import com.finova.notification.dto.PreferenceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "type", expression = "java(entity.getType() == null ? null : entity.getType().name())")
    @Mapping(target = "category", expression = "java(entity.getCategory() == null ? null : entity.getCategory().value())")
    @Mapping(target = "severity", expression = "java(entity.getSeverity() == null ? null : entity.getSeverity().name())")
    NotificationResponse toResponse(Notification entity);

    PreferenceResponse toResponse(NotificationPreference entity);
}