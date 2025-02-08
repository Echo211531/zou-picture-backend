package com.zr.yunbackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.zr.yunbackend.config.CosClientConfig;
import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.manager.CosManager;
import com.zr.yunbackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;


import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class PictureUploadTemplate {  
    @Resource
    protected CosManager cosManager;
    @Resource  
    protected CosClientConfig cosClientConfig;
  
    //模板方法，定义上传流程  
    public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片  
        validPicture(inputSource);  
  
        // 2. 图片上传地址  
        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginFilename(inputSource);
        // 获取原始文件名的后缀
        String originalSuffix = FileUtil.getSuffix(originFilename);
        // 如果原始文件名没有后缀，则默认设定为 "jpg"
        String suffix = (originalSuffix == null || originalSuffix.isEmpty()) ? "jpg" : originalSuffix;
        //上传到cos的文件名
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, suffix);
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFilename);

        File file = null;
        try {
            // 3. 创建临时文件
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源（本地或 URL）
            processFile(inputSource, file);

            // 4. 上传图片到对象存储
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            //5. 获取原图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            //获取处理过的图片信息
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {   //如果处理成功
                //获取压缩之后的结果
                CIObject compressedCiObject = objectList.get(0);  //因为当前只处理一张图片
                // 封装压缩图返回结果
                return buildResult(originFilename, compressedCiObject,imageInfo);
            }
            // 封装原图返回结果
            return buildResult(originFilename, file, uploadPath, imageInfo);
        } catch (Exception e) {  
            log.error("图片上传到对象存储失败", e);  
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {  
            // 6. 清理临时文件  
            deleteTempFile(file);  
        }  
    }  
  
    //校验输入源（本地文件或 URL）    
    protected abstract void validPicture(Object inputSource);


    //获取输入源的原始文件名    
    protected abstract String getOriginFilename(Object inputSource);  
  
    //处理输入源并生成本地临时文件  
    protected abstract void processFile(Object inputSource, File file) throws Exception;  
  
    //封装返回结果1
    private UploadPictureResult buildResult(String originFilename, File file, String uploadPath, ImageInfo imageInfo) {  
        UploadPictureResult uploadPictureResult = new UploadPictureResult();  
        int picWidth = imageInfo.getWidth();  
        int picHeight = imageInfo.getHeight();  
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));  
        uploadPictureResult.setPicWidth(picWidth);  
        uploadPictureResult.setPicHeight(picHeight);  
        uploadPictureResult.setPicScale(picScale);  
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());  //获取图片主色调
        uploadPictureResult.setPicSize(FileUtil.size(file));  
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);  
        return uploadPictureResult;  
    }
    //封装返回结果2
    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject,ImageInfo imageInfo) {
        // 封装结果到UploadPictureResult 中并返回
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();  //直接从图片处理成功后的结果中拿属性
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());  //获取图片主色调
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        // 设置图片为压缩后的地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        return uploadPictureResult;
    }
    //删除临时文件   
    public void deleteTempFile(File file) {  
        if (file == null) {  
            return;  
        }  
        boolean deleteResult = file.delete();  
        if (!deleteResult) {  
            log.error("file delete error, filepath = {}", file.getAbsolutePath());  
        }  
    }  
}