package com.zr.yunbackend.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zr.yunbackend.model.dto.picture.*;
import com.zr.yunbackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.vo.PictureVO;
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
    void checkPictureAuth(User loginUser, Picture picture);
    List<Picture> getPicturesBySpaceId(Long spaceId);
    void deletePicture(long pictureId, User loginUser);
    void createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);
}
