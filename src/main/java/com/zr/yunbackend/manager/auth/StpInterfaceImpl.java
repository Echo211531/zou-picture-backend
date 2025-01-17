package com.zr.yunbackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.zr.yunbackend.constant.SpaceUserPermissionConstant;
import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.model.entity.Picture;
import com.zr.yunbackend.model.entity.Space;
import com.zr.yunbackend.model.entity.SpaceUser;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.enums.SpaceRoleEnum;
import com.zr.yunbackend.model.enums.SpaceTypeEnum;
import com.zr.yunbackend.service.PictureService;
import com.zr.yunbackend.service.SpaceService;
import com.zr.yunbackend.service.SpaceUserService;
import com.zr.yunbackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.zr.yunbackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    private PictureService pictureService;


    //返回一个账号不同请求下所拥有的权限码集合,这个后续使用注解校验时会 自动进行判断
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 判断 loginType，多套体系，但现在我们仅有空间体系
        // 如果不是space，则返回空权限
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        // 先获取管理员的所有权限列表
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 获取上下文对象
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 如果所有字段都为空，表示查询公共图库，可以通过
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }
        // 从sa-token 登录态中 获取 userId
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId();
        // 1. 首先尝试从上下文中获取 SpaceUser 对象
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {  //如果有，直接返回其权限
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        // 2.如果有spaceUserId，必然是团队空间，通过数据库查询 特定团队空间的 SpaceUser对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 校验当前登录用户是否是该空间的spaceUser，并且处理管理员的情况
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            // 这里会导致管理员在私有空间没有权限，可以再查一次库处理
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 3.如果没有 spaceUserId，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
        Long spaceId = authContext.getSpaceId();

        // 4.如果没有 spaceId
        if (spaceId == null) {
            Long pictureId = authContext.getPictureId();
            // 图片 id 也没有，则默认通过所有权限校验
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            //通过 pictureId 获取 Picture 对象和 Space 对象
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            // 公共图库，仅本人或管理员可操作
            if (spaceId == null) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，返回仅可查看权限
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        //5.如果有 spaceId
        // 获取 Space 对象
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                return new ArrayList<>();
            }
        } else {
            // 团队空间，直接根据spaceId和userId 查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }

    //判断所有字段都为空
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }

    @Value("${server.servlet.context-path}")
    private String contextPath;   //获取请求上下文
    //从请求中获取上下文对象
    private SpaceUserAuthContext getAuthContextByRequest() {
        //获取当前请求对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        //获取当前请求类型，如get或post
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        // 兼容 get 和 post 操作
        //如果请求参数是json，即post请求
        if (ContentType.JSON.getValue().equals(contentType)) {
            //拿到请求体
            String body = ServletUtil.getBody(request);
            //把body转成之前定义的请求上下文
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {  //如果是get请求
            //获取到请求参数的map集合
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            //把map转成之前定义的请求上下文
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            //获取到完整的请求路径 如/api/picture/aaa?a=1
            String requestUri = request.getRequestURI();
            //替换掉上下文，即api/
            String partUri = requestUri.replace(contextPath + "/", "");
            //获取第一个/前面的字符串
            String moduleName = StrUtil.subBefore(partUri, "/", false);
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验) 本项目不用
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return null;
    }

}