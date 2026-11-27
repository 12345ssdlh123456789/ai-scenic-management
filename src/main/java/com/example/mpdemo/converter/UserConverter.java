package com.example.mpdemo.converter;

import com.example.mpdemo.dto.CreateUserDTO;
import com.example.mpdemo.dto.UpdateUserDTO;
import com.example.mpdemo.entity.User;
import com.example.mpdemo.util.RoleLevelUtil;
import com.example.mpdemo.vo.UserVO;
import com.example.mpdemo.vo.UserListVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.MappingTarget;

/**
 * User 与 DTO/VO 互转 Mapper（MapStruct）。
 * <p>
 * <b>注意包位置：</b>本接口必须放在 {@code com.example.mpdemo.converter}，
 * 不能放在 {@code com.example.mpdemo.mapper}。因为 {@code DazuoyeApplication}
 * 上的 {@code @MapperScan("com.example.mpdemo.mapper")} 会把该包下所有接口
 * 当作 MyBatis mapper 注册成 MyBatis 代理，导致 MapStruct 的实现类被覆盖，
 * 调用时抛出：
 * <pre>
 * BindingException: Invalid bound statement (not found):
 *   com.example.mpdemo.mapper.UserMapperStruct.toEntity
 * </pre>
 * 迁移到独立包后，MyBatis 只扫描真正的 mapper，MapStruct 的 @Component
 * 实现类才能被正常注入。
 */
@Mapper(componentModel = "spring", uses = {})
public interface UserConverter {

    @Mappings({
        @Mapping(target = "password", ignore = true), // 密码需单独加密
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "createTime", ignore = true),
        @Mapping(target = "updateTime", ignore = true),
        @Mapping(target = "version", ignore = true),
        @Mapping(target = "balance", ignore = true),
        @Mapping(target = "vipExpireTime", ignore = true),
        @Mapping(target = "orders", ignore = true),
        @Mapping(target = "status", constant = "1"),
        @Mapping(target = "role", expression = "java(dto.getRole() != null ? dto.getRole() : \"USER\")")
    })
    User toEntity(CreateUserDTO dto);

    @Mappings({
        @Mapping(target = "password", ignore = true),
        @Mapping(target = "createTime", ignore = true),
        @Mapping(target = "updateTime", ignore = true),
        @Mapping(target = "version", ignore = true),
        @Mapping(target = "balance", ignore = true),
        @Mapping(target = "vipExpireTime", ignore = true),
        @Mapping(target = "orders", ignore = true)
    })
    User updateEntity(UpdateUserDTO dto, @MappingTarget User entity);

    @Mappings({
        @Mapping(source = "createTime", target = "createTime"),
        @Mapping(source = "updateTime", target = "updateTime"),
        @Mapping(target = "roleLevel", expression = "java(com.example.mpdemo.util.RoleLevelUtil.getRoleLevelStatic(entity.getRole()))")
    })
    UserVO toVO(User entity);

    @Mappings({
        @Mapping(source = "createTime", target = "createTime"),
        @Mapping(target = "roleLevel", expression = "java(com.example.mpdemo.util.RoleLevelUtil.getRoleLevelStatic(entity.getRole()))")
    })
    UserListVO toListVO(User entity);
}
