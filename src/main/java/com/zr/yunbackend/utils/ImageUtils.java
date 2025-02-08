package com.zr.yunbackend.utils;

//将 RGB 空间值转换为灰度值
public class ImageUtils {
    public static int rgb2Gray(int r, int g, int b) {
        return (int) Math.round(r * 0.299 + g * 0.581 + b * 0.114);
    }
}