package com.zr.yunbackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.manager.websocket.model.PictureEditRequestMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    Disruptor<PictureEditEvent> pictureEditEventDisruptor;  //环形队列
	//发送事件               （接收消息，当前session，User，pictureId）
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session,
                             User user, Long pictureId) {
        //拿到缓冲区
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 获取可以生成的位置
        long next = ringBuffer.next();
        //获取到这个位置的事件对象，将数据填入
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 最后发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void close() {
        pictureEditEventDisruptor.shutdown();
    }
}