package com.zr.yunbackend.manager;
import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.MultiObjectDeleteException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.zr.yunbackend.config.CosClientConfig;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.io.File;

import java.util.ArrayList;
import java.util.List;


@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;
    public PutObjectResult putPictureObject(String key, File file) {
        // 创建一个PutObjectRequest对象，指定要上传的目标bucket和文件的唯一键(key)，以及要上传的文件
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();  //创建规则
        // 设置是否返回原图信息，1 表示返回原图信息
        picOperations.setIsPicInfo(1);        //设置规则

        // 图片压缩（转成 webp 格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
//        Pic-Operations:
//        {
//            "is_pic_info": 1,
//                "rules": [{
//            "fileid": "exampleobject",
//                    "rule": "imageMogr2/format/<Format>"
//        }]
//        }
        List<PicOperations.Rule> rules = new ArrayList<>(); //定义一个规则列表
        //构造如上压缩规则
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webpKey); //设置id，就是当前文件名
        compressRule.setRule("imageMogr2/format/webp"); //规则如上
        compressRule.setBucket(cosClientConfig.getBucket());
        rules.add(compressRule);  //添加压缩规则

        // 构造处理参数
        picOperations.setRules(rules);
        // 规则作为构造参数发给PutObjectRequest中
        putObjectRequest.setPicOperations(picOperations);

        //  使用 TransferManager 进行分块上传并开启断点续传
        TransferManager transferManager = new TransferManager(cosClient);
        TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartUploadThreshold(5 * 1024 * 1024); // 分块上传阈值设为5MB
        transferManagerConfiguration.setMinimumUploadPartSize(1 * 1024 * 1024); // 分块大小设为1MB
        transferManager.setConfiguration(transferManagerConfiguration);

        //将文件上传至COS
        return cosClient.putObject(putObjectRequest);
    }



    /**
     * 删除COS中的对象
     *
     * @param keys          要删除的对象键列表
     */
    public void deleteObjects(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        String bucketName = cosClientConfig.getBucket();
        DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName);
        List<DeleteObjectsRequest.KeyVersion> keyVersions = new ArrayList<>();
        for (String key : keys) {
            keyVersions.add(new DeleteObjectsRequest.KeyVersion(key));
        }
        deleteObjectsRequest.setKeys(keyVersions);

        try {
            cosClient.deleteObjects(deleteObjectsRequest);
        } catch (MultiObjectDeleteException mde) {
            // 处理部分删除成功的情况
            for (MultiObjectDeleteException.DeleteError error : mde.getErrors()) {
                System.err.println("Error in deleting object " + error.getKey() + ": " + error.getMessage());
            }
        } catch ( CosClientException e) {
            e.printStackTrace();
        }
    }
    // ... 一些操作 COS 的方法

}