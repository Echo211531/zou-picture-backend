package com.zr.yunbackend.service;
import com.baomidou.mybatisplus.extension.service.IService;

import com.zr.yunbackend.model.dto.space.analyze.SpaceCategoryAnalyzeRequest;
import com.zr.yunbackend.model.dto.space.analyze.SpaceRankAnalyzeRequest;
import com.zr.yunbackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.zr.yunbackend.model.entity.Space;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.vo.analyze.SpaceCategoryAnalyzeResponse;
import com.zr.yunbackend.model.vo.analyze.SpaceUsageAnalyzeResponse;

import java.util.List;


/**
*/
public interface SpaceAnalyzeService extends IService<Space> {
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) ;
}
