package com.zr.yunbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zr.yunbackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.zr.yunbackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.zr.yunbackend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zr.yunbackend.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author www21
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-01-14 11:17:16
*/
public interface SpaceUserService extends IService<SpaceUser> {
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);
    void validSpaceUser(SpaceUser spaceUser, boolean add);
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
