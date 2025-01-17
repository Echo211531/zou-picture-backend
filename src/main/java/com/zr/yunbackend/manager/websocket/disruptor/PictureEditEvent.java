package com.zr.yunbackend.manager.websocket.disruptor;

import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.manager.websocket.model.PictureEditRequestMessage;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

@Data
public class PictureEditEvent {
    //消息,之前定义的websocket处理消息载体
    private PictureEditRequestMessage pictureEditRequestMessage;
    //当前用户的 session
    private WebSocketSession session;
    //当前用户
    private User user;
    //图片 id
    private Long pictureId;

}