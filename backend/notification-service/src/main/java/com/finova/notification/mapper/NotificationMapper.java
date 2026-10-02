package com.finova.notification.mapper;

import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationPreference;
import com.finova.notification.dto.AdminNotificationResponse;
import com.finova.notification.dto.NotificationResponse;
import com.finova.notification.dto.PreferenceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /**
     * Customer view. Never carries the owner: the caller already knows it, and the id
     * would be one more field an inbox screen could misuse.
     */
    @Mapping(target = "type", expression = "java(entity.getType() == null ? null : entity.getType().name())")
    @Mapping(target = "category", expression = "java(entity.getCategory() == null ? null : entity.getCategory().value())")
    @Mapping(target = "severity", expression = "java(entity.getSeverity() == null ? null : entity.getSeverity().name())")
    NotificationResponse toResponse(Notification entity);

    /**
     * Administrator view. Adds the owner, the masked owner hint and the correlation
     * id needed to trace a row back through the audit trail.
     */
    @Mapping(target = "userDisplayHint", expression = "java(AdminHints.maskUserId(entity.getUserId()))")
    @Mapping(target = "currency", expression = "java(AdminHints.toCurrency(entity.getCurrency()))")
    AdminNotificationResponse toAdminResponse(Notification entity);

    PreferenceResponse toResponse(NotificationPreference entity);
}