package com.finova.user.mapper;

import com.finova.user.domain.AuditLog;
import com.finova.user.domain.User;
import com.finova.user.dto.AuthUserResponse;
import com.finova.user.dto.AuditLogResponse;
import com.finova.user.dto.UserSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity to DTO projection.
 * <p>
 * Both user projections are declared explicitly rather than letting MapStruct infer
 * them, because the fields that must never leave the service layer
 * ({@code passwordHash}) are simply absent from the target records. A mapper cannot
 * invent a field the DTO does not have, which is what keeps the hash out of every
 * response body.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "firstName", expression = "java(MappingSupport.trimToNull(entity.getFirstName()))")
    @Mapping(target = "lastName", expression = "java(MappingSupport.trimToNull(entity.getLastName()))")
    @Mapping(target = "phone", expression = "java(MappingSupport.trimToNull(entity.getPhone()))")
    AuthUserResponse toAuthUser(User entity);

    @Mapping(target = "firstName", expression = "java(MappingSupport.trimToNull(entity.getFirstName()))")
    @Mapping(target = "lastName", expression = "java(MappingSupport.trimToNull(entity.getLastName()))")
    @Mapping(target = "phone", expression = "java(MappingSupport.trimToNull(entity.getPhone()))")
    UserSummaryResponse toSummary(User entity);

    @Mapping(target = "createdAt", expression = "java(entity.getCreatedAt())")
    AuditLogResponse toAuditLogResponse(AuditLog entity);
}
