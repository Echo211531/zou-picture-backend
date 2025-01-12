package com.zr.yunbackend.service;

import com.zr.yunbackend.model.entity.Message;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author www21
* @description 针对表【message(AI扩图)】的数据库操作Service
*/
public interface MessageService extends IService<Message> {
    void updateMessageTable(String taskId, String status, String outputImageUrl);
    void updateMessageTable(String taskId, String status);
}
