package com.zr.yunbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zr.yunbackend.model.dto.file.UploadUserPictureResult;
import com.zr.yunbackend.model.dto.picture.PictureQueryRequest;
import com.zr.yunbackend.model.dto.picture.PictureReviewRequest;
import com.zr.yunbackend.model.dto.picture.PictureUploadByBatchRequest;
import com.zr.yunbackend.model.dto.picture.PictureUploadRequest;
import com.zr.yunbackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.vo.PictureVO;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
*/
public interface PictureService extends IService<Picture> {
    //上传图片
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);
    void validPicture(Picture picture);
    List<Picture> selectByIsDelete(Integer isDelete);
    //图片审核
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);
    void fillReviewParams(Picture picture, User loginUser);

    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

}
