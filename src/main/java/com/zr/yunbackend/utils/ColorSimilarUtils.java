package com.zr.yunbackend.utils;
import java.awt.*;

/**
 * 工具类：计算颜色相似度
 */
public class ColorSimilarUtils {
    private ColorSimilarUtils() {
        // 工具类不需要实例化
    }
	// 计算两个RGB颜色的相似度       相似度（0到1之间，1为完全相同）
    public static double calculateSimilarity(Color color1, Color color2) {
        int r1 = color1.getRed();
        int g1 = color1.getGreen();
        int b1 = color1.getBlue();

        int r2 = color2.getRed();
        int g2 = color2.getGreen();
        int b2 = color2.getBlue();

        // 计算RGB色彩空间 两个颜色的欧氏距离
        double distance = Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));

        // 计算相似度
        //分母：RGB色彩空间中可能的最大距离 0-255   255的平方*3开根
        return 1 - distance / Math.sqrt(3 * Math.pow(255, 2));
    }

    //根据十六进制颜色代码计算相似度
    public static double calculateSimilarity(String hexColor1, String hexColor2) {
        Color color1 = Color.decode(hexColor1);  //根据十六进制转为color对象
        Color color2 = Color.decode(hexColor2);
        return calculateSimilarity(color1, color2);
    }
}