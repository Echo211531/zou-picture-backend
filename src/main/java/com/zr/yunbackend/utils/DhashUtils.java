package com.zr.yunbackend.utils;

import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class DhashUtils {

    //计算输入流中图片的 DHash 值
    public static long getImageDhashFrom(@NotNull InputStream inputStream) throws IOException {
        int width = 9;
        int height = 8;
        List<InputStream> inputStreams = Collections.singletonList(inputStream);
        //使用 Thumbnails 库将图片调整为 9x8 的大小
        Thumbnails.Builder<InputStream> inputStreamBuilder = Thumbnails.fromInputStreams(inputStreams).forceSize(width, height);
        BufferedImage bufferedImage = inputStreamBuilder.asBufferedImage();

        //构建最终的 64 位二进制字符串
        StringBuilder str64 = new StringBuilder((width - 1) * height);
        //遍历调整后的图片的每个像素点，计算其灰度值并存储在二维数组 grays 中
        int[][] grays = new int[width][height];
        for (int hi = 0; hi < height; hi++) {
            for (int wi = 0; wi < width; wi++) {
                // 返回当前像素点的 ARGB 值 转成color对象
                Color color = new Color(bufferedImage.getRGB(wi, hi));
                //将 RGB 值转换为灰度值
                grays[wi][hi] = ImageUtils.rgb2Gray(color.getRed(), color.getGreen(), color.getBlue());
                if (wi > 0) {
                    int hashBit = 0;
                    //如果当前像素点的灰度值大于左侧像素点的灰度值，则 hashBit 设置为 1
                    //否则，hashBit 设置为 0
                    if (grays[wi][hi] > grays[wi - 1][hi]) {
                        hashBit = 1;
                    }
                    str64.append(hashBit);  //追加结果到StringBuilder
                }
            }
        }
        //将构建好的 64 位二进制字符串转换为 long 类型的 DHash 值
        return BitUtils.str64ToLong(str64.toString());
    }

    public static int distanceBetween(@NotNull InputStream inputStream1, @NotNull InputStream inputStream2) throws IOException {
        String str1 = BitUtils.longToStr64(getImageDhashFrom(inputStream1));
        String str2 = BitUtils.longToStr64(getImageDhashFrom(inputStream2));
        return hammingDistanceBetween(str1,str2);
    }

    public static int distanceBetween(long hashValue1, long hashValue2) {
        String str1 = BitUtils.longToStr64(hashValue1);
        String str2 = BitUtils.longToStr64(hashValue2);
        return hammingDistanceBetween(str1,str2);
    }

    private static int hammingDistanceBetween(@NotNull String str1, @NotNull String str2) {
        int distance = 0;
        if (str1.length() != str2.length()) {
            throw new IllegalArgumentException("参数长度不一致");
        }
        for (int i = 0; i < str1.length(); i++) {
            if (str1.charAt(i) != str2.charAt(i)) {
                distance++;
            }
        }
        return distance;
    }
}
