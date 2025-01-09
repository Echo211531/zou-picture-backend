package com.zr.yunbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zr.yunbackend.model.dto.space.SpaceAddRequest;
import com.zr.yunbackend.model.dto.space.SpaceQueryRequest;
import com.zr.yunbackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
*/
public interface SpaceService extends IService<Space> {
    void validSpace(Space space, boolean add);
    void fillSpaceBySpaceLevel(Space space);
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);
}
