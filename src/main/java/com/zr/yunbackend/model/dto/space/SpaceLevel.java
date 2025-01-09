package com.zr.yunbackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

//空间级别
@Data
@AllArgsConstructor //生成一个包含所有类字段的构造函数
public class SpaceLevel {

    private int value; //值
    private String text;  //中文
    private long maxCount;  //最大数量
    private long maxSize;  //最大容量
}