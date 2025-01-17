package com.zr.yunbackend.manager.websocket.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
public class PictureEditEventDisruptorConfig {

    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;  //消费者
	
    //定义环形队列的基本参数
    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> messageModelRingBuffer() {
        // 设置ringBuffer 的大小
        int bufferSize = 1024 * 256;  //越小，数据容易发生覆盖，越大内存大了，但性能更高
        //创建disruptor
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                PictureEditEvent::new,  //定义放到缓冲区上的类型
                bufferSize,   //缓冲区大小
            	//创建一个线程工厂并指定前缀
                ThreadFactoryBuilder.create().setNamePrefix("pictureEditEventDisruptor").build()
        );
        // 给无锁队列 设置消费者
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        // 开启 disruptor
        disruptor.start();
        return disruptor;
    }
}