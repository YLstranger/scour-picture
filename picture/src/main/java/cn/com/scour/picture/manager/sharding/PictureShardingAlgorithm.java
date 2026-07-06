package cn.com.scour.picture.manager.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Properties;

/**
 * 图片分表算法
 */
@Slf4j
public class PictureShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> preciseShardingValue) {
        Long spaceId = preciseShardingValue.getValue();
        String logicTableName = preciseShardingValue.getLogicTableName();
        // spaceId 为 null 表示查询所有图片
        if (spaceId == null) {
            return logicTableName;
        }
        // 根据 spaceId 动态生成分表名
        String realTableName = "picture_" + spaceId;
        if (availableTargetNames.contains(realTableName)) {
            log.info("Picture sharding route matched, spaceId={}, targetTable={}, availableTargetNames={}",
                    spaceId, realTableName, availableTargetNames);
            return realTableName;
        }
        log.info("Picture sharding route fallback to logic table, spaceId={}, targetTable={}, availableTargetNames={}",
                spaceId, logicTableName, availableTargetNames);
        return logicTableName;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> rangeShardingValue) {
        return availableTargetNames;
    }

    @Override
    public void init(Properties properties) {

    }
}
