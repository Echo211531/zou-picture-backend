package com.zr.yunbackend.manager.pictureSearch;
import cn.hutool.json.JSONUtil;
import com.zr.yunbackend.manager.pictureSearch.impl.BaiduSearchPicture;
import com.zr.yunbackend.manager.pictureSearch.impl.SoSearchPicture;
import com.zr.yunbackend.manager.pictureSearch.model.SearchPictureResult;


import java.util.List;

/**
 * 以图搜图测试
 */
public class PictureSearchTest {
	public static void main(String[] args) {
		// 360以图搜图
		String imageUrl1 = "https://zr-1328625840.cos.ap-shanghai.myqcloud.com/public/1873375553307328514/2025-03-01_8498dsjtf6ollvq8.webp";
		AbstractSearchPicture soSearchPicture = new SoSearchPicture();
		List<SearchPictureResult> soResultList = soSearchPicture.execute("so", imageUrl1, 1, 21);
		System.out.println("结果列表: " + JSONUtil.parse(soResultList));

		// 百度以图搜图
		String imageUrl2 = "https://www.codefather.cn/logo.png";
		AbstractSearchPicture baiduSearchPicture = new BaiduSearchPicture();
		List<SearchPictureResult> baiduResultList = baiduSearchPicture.execute("baidu", imageUrl2, 1, 31);
		System.out.println("结果列表" + JSONUtil.parse(baiduResultList));
	}
}
