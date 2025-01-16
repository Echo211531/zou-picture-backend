package com.zr.yunbackend.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zr.yunbackend.auth.model.SpaceUserAuthConfig;
import com.zr.yunbackend.auth.model.SpaceUserRole;
import com.zr.yunbackend.constant.SpaceUserPermissionConstant;
import com.zr.yunbackend.model.entity.Space;
import com.zr.yunbackend.model.entity.SpaceUser;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.enums.SpaceRoleEnum;
import com.zr.yunbackend.model.enums.SpaceTypeEnum;
import com.zr.yunbackend.service.SpaceUserService;
import com.zr.yunbackend.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SpaceUserAuthManager {

    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private UserService userService;

    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;  //引入 配置类
    //静态代码块，当类加载就读取json配置文件
    static {
        //读取配置文件，并将其转为 配置类，实现属性注入
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    //根据角色 获取权限列表
    public List<String> getPermissionsByRole(String spaceUserRole) {
        if (StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        // 找到匹配的角色
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles().stream()
                .filter(r -> spaceUserRole.equals(r.getKey())) //查当前要找的角色列表
                .findFirst()  //得到这条角色信息
                .orElse(null);
        if (role == null) { //角色不存在，一般不会出现
            return new ArrayList<>();
        }
        return role.getPermissions();  //返回该角色对应的权限列表
    }
    //获取当前用户的空间权限
    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 公共图库
        if (space == null) {
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
        }
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }


}