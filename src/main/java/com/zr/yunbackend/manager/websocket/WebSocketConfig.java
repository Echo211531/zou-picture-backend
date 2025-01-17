package com.zr.yunbackend.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
	//引入处理器
    @Resource
    private PictureEditHandler pictureEditHandler;  
    //引入拦截器
    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;
	
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册一个处理器，请求地址为/ws/picture/edit  
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(wsHandshakeInterceptor)  //配置拦截器
                .setAllowedOrigins("*");  //设置跨域范围
    }
}