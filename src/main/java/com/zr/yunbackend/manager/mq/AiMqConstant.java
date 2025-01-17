package com.zr.yunbackend.manager.mq;
public interface AiMqConstant {
    //AI扩图队列
    String AI_EXCHANGE_NAME = "outPaintingExchange";
    String AI_QUEUE_NAME = "outPaintingQueue";;
    String AI_ROUTING_KEY = "outPaintingRoutingKey";
    //死信队列
    String AI_QUEUE_DEAD_NAME="ai_queue_dead";
    String AI_EXCHANGE_DEAD = "ai_deadExchange";
    String AI_ROUTING_DEAD_KEY = "";
}