package com.zr.yunbackend.manager.sharding;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.zr.yunbackend.model.entity.Space;
import com.zr.yunbackend.model.enums.SpaceLevelEnum;
import com.zr.yunbackend.model.enums.SpaceTypeEnum;
import com.zr.yunbackend.service.SpaceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

//样板代码
//@Component
@Slf4j
public class DynamicShardingManager {

    @Resource
    private DataSource dataSource;
    @Resource
    private SpaceService spaceService;
    private static final String LOGIC_TABLE_NAME = "picture";
    private static final String DATABASE_NAME = "logic_db"; // 配置文件中的数据库名称

    @PostConstruct
    public void initialize() {
        log.info("初始化动态分表配置...");
        updateShardingTableNodes();   //初始化的时候获取分表的actual-data-nodes
    }

    //获取所有动态表名，包括初始表 picture 和分表 picture_{spaceId}
    private Set<String> fetchAllPictureTableNames() {
       // 获取所有团队空间中标识为旗舰版（spaceLevel = 2）的分表id
        Set<Long> spaceIds = spaceService.lambdaQuery() 
                .eq(Space::getSpaceType, SpaceTypeEnum.TEAM.getValue())  //获取所有团队
            	.eq(Space::getSpaceLevel, SpaceLevelEnum.FLAGSHIP.getValue())  // 过滤出旗舰版空间
                .list()
                .stream()
                .map(Space::getId)
                .collect(Collectors.toSet());
        //根据得到的spaceId拼接上picture_ 得到表名列表
        Set<String> tableNames = spaceIds.stream()  
                .map(spaceId -> LOGIC_TABLE_NAME + "_" + spaceId)
                .collect(Collectors.toSet());
        tableNames.add(LOGIC_TABLE_NAME);    // 最后不要忘了，添加初始逻辑表picture
        return tableNames;
    }

    /**
     * 更新 ShardingSphere 的 actual-data-nodes 动态表名配置
     */
    private void updateShardingTableNodes() {
        Set<String> tableNames = fetchAllPictureTableNames();  //获取所有可用的表名
        String newActualDataNodes = tableNames.stream()
                .map(tableName -> "zou_picture." + tableName) // 确保前缀合法
                .collect(Collectors.joining(","));
        //结果： zou_picture.picture_1,zou_picture.picture_2...
        log.info("动态分表 actual-data-nodes 配置: {}", newActualDataNodes);
	
        // ---样板代码-----
        ContextManager contextManager = getContextManager(); //获取上下文
        //接下来一堆代码就是 找配置文件中对应表的的 actual-data-nodes 
        ShardingSphereRuleMetaData ruleMetaData = contextManager.getMetaDataContexts()
                .getMetaData()
                .getDatabases()
                .get(DATABASE_NAME)
                .getRuleMetaData();

        Optional<ShardingRule> shardingRule = ruleMetaData.findSingleRule(ShardingRule.class);
        if (shardingRule.isPresent()) {
            ShardingRuleConfiguration ruleConfig = (ShardingRuleConfiguration) shardingRule.get().getConfiguration();
            List<ShardingTableRuleConfiguration> updatedRules = ruleConfig.getTables()
                    .stream()
                    .map(oldTableRule -> {
                        if (LOGIC_TABLE_NAME.equals(oldTableRule.getLogicTable())) {
                            //就这关键代码，把前面得到的表名列表拼上去
                            ShardingTableRuleConfiguration newTableRuleConfig = new ShardingTableRuleConfiguration(LOGIC_TABLE_NAME, newActualDataNodes);
                            newTableRuleConfig.setDatabaseShardingStrategy(oldTableRule.getDatabaseShardingStrategy());
                            newTableRuleConfig.setTableShardingStrategy(oldTableRule.getTableShardingStrategy());
                            newTableRuleConfig.setKeyGenerateStrategy(oldTableRule.getKeyGenerateStrategy());
                            newTableRuleConfig.setAuditStrategy(oldTableRule.getAuditStrategy());
                            return newTableRuleConfig;
                        }
                        return oldTableRule;
                    })
                    .collect(Collectors.toList());
            ruleConfig.setTables(updatedRules);
            contextManager.alterRuleConfiguration(DATABASE_NAME, Collections.singleton(ruleConfig));
            contextManager.reloadDatabase(DATABASE_NAME);
            log.info("动态分表规则更新成功！");
        } else {
            log.error("未找到 ShardingSphere 的分片规则配置，动态分表更新失败。");
        }
    }

    /**
     * 获取 ShardingSphere ContextManager
     */
    private ContextManager getContextManager() {
        try (ShardingSphereConnection connection = dataSource.getConnection().unwrap(ShardingSphereConnection.class)) {
            return connection.getContextManager();
        } catch (SQLException e) {
            throw new RuntimeException("获取 ShardingSphere ContextManager 失败", e);
        }
    }

    //动态创建分表
    public void createSpacePictureTable(Space space) {
        // 仅为旗舰版团队空间创建分表
        if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue() && space.getSpaceLevel() == SpaceLevelEnum.FLAGSHIP.getValue()) {
            Long spaceId = space.getId();
            String tableName = "picture_" + spaceId;
            // 创建新表
            String createTableSql = "CREATE TABLE " + tableName + " LIKE picture";
            try {
                SqlRunner.db().update(createTableSql);
                // 更新分表，因为又多了团队空间
                updateShardingTableNodes();
            } catch (Exception e) {
                log.error("创建图片空间分表失败，空间 id = {}", space.getId());
            }
        }
    }


}