package com.zr.yunbackend.manager.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

//图片分表算法实现
public class PictureShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    @Override								//支持分表的表名
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> preciseShardingValue) {
        Long spaceId = preciseShardingValue.getValue();  //获取分表值，即spaceId
        String logicTableName = preciseShardingValue.getLogicTableName();   //拿到逻辑表，即没有分别前的一直查的那张表
        // spaceId 为 null 表示查询所有图片
        if (spaceId == null) {
            return logicTableName;
        }
        // 有spaceId，则根据 spaceId 动态生成分表名
        String realTableName = "picture_" + spaceId;
        if (availableTargetNames.contains(realTableName)) {   //如果表在支持的范围内，比如团队空间，则查分表
            return realTableName;
        } else {
            return logicTableName;   //否则返回逻辑表
        }
    }

    @Override
    public Collection<String> doSharding(Collection<String> collection, RangeShardingValue<Long> rangeShardingValue) {
        return new ArrayList<>();
    }
    @Override
    public Properties getProps() {
        return null;
    }
    @Override
    public void init(Properties properties) {

    }
}