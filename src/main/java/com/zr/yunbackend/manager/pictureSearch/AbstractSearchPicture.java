package com.zr.yunbackend.manager.pictureSearch;

import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.manager.pictureSearch.enums.SearchSourceEnum;
import com.zr.yunbackend.manager.pictureSearch.model.SearchPictureResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 抽象 以图搜图 父类
 */
@Slf4j
public abstract class AbstractSearchPicture {

	/**
	 * 执行搜索
	 *
	 * @param searchSource  搜索源
	 * @param sourcePicture 源图片
	 * @param randomSeed    随机种子
	 * @param searchCount   搜索数量
	 * @return 搜索结果
	 */
	public final List<SearchPictureResult> execute(String searchSource, String sourcePicture, Integer randomSeed, Integer searchCount) {
		log.info("开始搜索图片，搜索源：{}，源图片：{}，随机种子：{}", searchSource, sourcePicture, randomSeed);
		// 校验
		//获取搜索源，如360还是baidu
		SearchSourceEnum searchSourceEnum = SearchSourceEnum.getEnumByKey(searchSource);
		if (searchSourceEnum == null) {
			throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的搜索源");
		}
		// 传入 搜索源+原图 执行搜索
		String requestUrl = this.executeSearch(searchSourceEnum, sourcePicture);
		// 发送请求 获取搜索结果
		List<SearchPictureResult> pictureResultList = this.sendRequestGetResponse(requestUrl, randomSeed, searchCount);
		// 如果当前结果大于 搜索数量 就截取
		if (pictureResultList.size() > searchCount) {
			pictureResultList = pictureResultList.subList(0, searchCount);
		}
		log.info("搜索图片结束，返回结果数量：{}", pictureResultList.size());
		return pictureResultList;
	}

//--------------------模板方法：具体实现在360或baidu搜索子类---------------------------
	/**
	 * 根据原图片获取搜索图片的列表地址
	 *
	 * @param searchSourceEnum 搜索源枚举
	 * @param sourcePicture    源图片
	 * @return 搜索图片的列表地址
	 */
	protected abstract String executeSearch(SearchSourceEnum searchSourceEnum, String sourcePicture);

	/**
	 * 发送请求获取响应
	 *
	 * @param requestUrl  请求地址
	 * @param randomSeed  随机种子
	 * @param searchCount 搜索数量
	 * @return 响应结果
	 */
	protected abstract List<SearchPictureResult> sendRequestGetResponse(String requestUrl, Integer randomSeed, Integer searchCount);
}
