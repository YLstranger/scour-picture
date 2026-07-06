package cn.com.scour.picture.manager.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PictureShardingAlgorithmTest {

    @Test
    void doShardingShouldRouteTeamSpaceToAvailablePhysicalTable() {
        PictureShardingAlgorithm algorithm = new PictureShardingAlgorithm();
        Long spaceId = 2072991911041961986L;
        PreciseShardingValue<Long> shardingValue =
                new PreciseShardingValue<>("picture", "spaceId", null, spaceId);

        String targetTable = algorithm.doSharding(Collections.singletonList("picture_" + spaceId), shardingValue);

        assertEquals("picture_" + spaceId, targetTable);
    }

    @Test
    void doShardingShouldFallbackToLogicTableWhenPhysicalTableIsNotInActualDataNodes() {
        PictureShardingAlgorithm algorithm = new PictureShardingAlgorithm();
        Long spaceId = 2072991911041961986L;
        PreciseShardingValue<Long> shardingValue =
                new PreciseShardingValue<>("picture", "spaceId", null, spaceId);

        String targetTable = algorithm.doSharding(Collections.singletonList("picture"), shardingValue);

        assertEquals("picture", targetTable);
    }
}
