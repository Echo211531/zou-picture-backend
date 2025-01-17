package com.zr.yunbackend.manager.mq;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.rabbitmq.client.Channel;
import com.zr.yunbackend.api.aliyunai.AliYunApi;
import com.zr.yunbackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.service.MessageService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;

import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.io.IOException;


@Component
@Slf4j
public class AiTaskConsumer {

    @Resource
    private AliYunApi aliYunApi;
    @Resource
    private MessageService messageService;

    private static final int MAX_RETRIES = 20; // 最大重试次数
    private static final long TIMEOUT_MILLIS = 60 * 60 * 1000L; // 1小时超时
    private static final long SLEEP_INTERVAL = 3000L; // 轮询间隔时间

    @SneakyThrows
    @RabbitListener(queues = {AiMqConstant.AI_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到消息 = {}", message);
        if (StringUtils.isBlank(message)) {
            // 消息为空，则拒绝掉消息
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接受到的消息为空");
        }

        String taskId = message;  //任务ID
        try {
            // 异步等待任务完成并更新状态
            checkTaskStatusAsync(taskId, channel, deliveryTag);
        } catch (Exception e) {
            log.error("AI扩图错误", e);
            channel.basicNack(deliveryTag, false, true); // 重新排队消息
        }
    }
    //异步轮询 检查AI扩图结果
    @Async
    public void checkTaskStatusAsync(String taskId, Channel channel, long deliveryTag) {
        int retryCount = 0;  //记录重试次数
        long startTime = System.currentTimeMillis();  //记录开始时间
        GetOutPaintingTaskResponse taskStatus = null;  //存api响应对象
        //循环检查（最大重试次数和超时期限内）
        while (retryCount < MAX_RETRIES && (System.currentTimeMillis() - startTime) < TIMEOUT_MILLIS) {
            try {
                Thread.sleep(SLEEP_INTERVAL);  // 非阻塞：主线程继续处理其他消息
                taskStatus = aliYunApi.getOutPaintingTask(taskId);
                //如果成功获取结果，就退出循环
                if ("SUCCEEDED".equals(taskStatus.getOutput().getTaskStatus())
                        || "FAILED".equals(taskStatus.getOutput().getTaskStatus())) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 设置中断标志
                return;
            } catch (Exception e) {
                log.error("获取AI扩图任务状态失败", e);
                // 可能需要根据具体情况决定是否继续重试
                if (++retryCount >= MAX_RETRIES) {
                    log.error("达到最大重试次数");
                    break;
                }
            }
            retryCount++;
        }

        // 更新消息表的状态
        updateMessageTable(taskId, taskStatus);
        // 确认消息已处理
        confirmMessage(channel, deliveryTag, taskStatus);
    }

    private void updateMessageTable(String taskId, GetOutPaintingTaskResponse taskStatus) {
        if (taskStatus != null) {
            String status = taskStatus.getOutput().getTaskStatus();
            String outputImageUrl = "SUCCEEDED".equals(status) ? taskStatus.getOutput().getOutputImageUrl() : null;
            messageService.updateMessageTable(taskId, status, outputImageUrl);
        } else {
            // 如果任务状态始终无法获取，可以考虑设置为失败或其他默认状态
            messageService.updateMessageTable(taskId, "UNKNOWN", null);
        }
    }
    //最终确认消息
    private void confirmMessage(Channel channel, long deliveryTag, GetOutPaintingTaskResponse taskStatus) {
        try {
            if (taskStatus == null || !"SUCCEEDED".equals(taskStatus.getOutput().getTaskStatus()) && !"FAILED".equals(taskStatus.getOutput().getTaskStatus())) {
                // 如果任务状态不确定或者未完成，则重新排队消息
                channel.basicNack(deliveryTag, false, true);
            } else {
                // 成功或失败都确认消息
                channel.basicAck(deliveryTag, false);
            }
        } catch (IOException e) {
            log.error("确认消息失败", e);
        }
    }
}