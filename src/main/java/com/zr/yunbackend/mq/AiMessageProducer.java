package com.zr.yunbackend.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
//发消息
@Component
public class AiMessageProducer {
    @Resource
    private RabbitTemplate rabbitMqTemplate;

    //发消息
    public void sendMessage(String message) {
        rabbitMqTemplate.convertAndSend(AiMqConstant.AI_EXCHANGE_NAME, AiMqConstant.AI_ROUTING_KEY, message);
    }
}