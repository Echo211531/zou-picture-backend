package com.zr.yunbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zr.yunbackend.model.entity.Message;
import com.zr.yunbackend.service.MessageService;
import com.zr.yunbackend.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
* @author www21
* @description 针对表【message(AI扩图)】的数据库操作Service实现
* @createDate 2025-01-12 14:32:29
*/
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
    implements MessageService{
    //更新任务状态和输出图像URL
    @Override
    public void updateMessageTable(String taskId, String status, String outputImageUrl) {
        LambdaUpdateWrapper<Message> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Message::getTaskId, taskId)
                .set(Message::getTaskStatus, status);

        if (outputImageUrl != null && !outputImageUrl.isEmpty()) {
            updateWrapper.set(Message::getOutputImageUrl, outputImageUrl);
        }
        // 设置结束时间
        updateWrapper.set(Message::getEndTime, new Date());
        // 执行更新操作
        this.update(updateWrapper);
    }
    @Override
    //更新任务状态（不带输出图像URL）
    public void updateMessageTable(String taskId, String status) {
        updateMessageTable(taskId, status, null);
    }
}




