package cn.com.scour.picture.manager.sharding;

import cn.com.scour.picture.config.RefreshableDataSource;
import cn.com.scour.picture.model.entity.Space;
import cn.com.scour.picture.model.enums.SpaceLevelEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.apache.shardingsphere.sharding.rule.TableRule;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DynamicShardingManager {

    private static final String LOGIC_TABLE_NAME = "picture";

    private static final String DATA_SOURCE_NAME = "yu_picture";

    private static final String SHARDING_ALGORITHM_NAME = "picture_sharding_algorithm";

    @Resource
    private DataSource dataSource;

    @Resource(name = "actualDataSource")
    private DataSource actualDataSource;

    @PostConstruct
    public void initialize() {
        log.info("Initializing dynamic picture sharding rules...");
        try {
            refreshShardingRule();
        } catch (Exception e) {
            log.warn("Initialize dynamic picture sharding rules failed, application startup continues", e);
        }
    }

    public void createSpacePictureTable(Space space) {
        createSpacePictureTable(space, false);
    }

    public void ensureSpacePictureTableReady(Space space) {
        createSpacePictureTable(space, true);
    }

    private void createSpacePictureTable(Space space, boolean strict) {
        if (!needSharding(space)) {
            return;
        }
        Long spaceId = space.getId();
        String tableName = getSpacePictureTableName(spaceId);
        try {
            if (strict) {
                refreshShardingRuleIfTableExists(tableName);
                log.info("Picture sharding table is verified, spaceId={}, table={}", spaceId, tableName);
                return;
            }
            createPhysicalPictureTable(tableName);
            refreshShardingRule(tableName);
            log.info("Picture sharding table is ready, spaceId={}, table={}", spaceId, tableName);
        } catch (Exception e) {
            log.error("Prepare picture sharding table failed, spaceId={}, table={}", spaceId, tableName, e);
            if (strict) {
                throw new IllegalStateException("Prepare picture sharding table failed, spaceId=" + spaceId, e);
            }
        }
    }

    private boolean needSharding(Space space) {
        return space != null
                && space.getId() != null
                && space.getSpaceLevel() != null
                && SpaceLevelEnum.FLAGSHIP.getValue() == space.getSpaceLevel();
    }

    private String getSpacePictureTableName(Long spaceId) {
        return LOGIC_TABLE_NAME + "_" + spaceId;
    }

    private void createPhysicalPictureTable(String tableName) {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " LIKE " + LOGIC_TABLE_NAME;
        try (Connection connection = actualDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            log.info("Physical picture sharding table is ready, table={}", tableName);
        } catch (Exception e) {
            log.error("Create physical picture sharding table failed, table={}", tableName, e);
            throw new IllegalStateException("Create physical picture sharding table failed, table=" + tableName, e);
        }
    }

    private void refreshShardingRule() {
        refreshShardingRule(null);
    }

    private void refreshShardingRule(String requiredTableName) {
        Set<String> tableNames = fetchExistingPictureTableNames();
        if (requiredTableName != null) {
            tableNames.add(requiredTableName);
        }
        String actualDataNodes = tableNames.stream()
                .map(tableName -> DATA_SOURCE_NAME + "." + tableName)
                .collect(Collectors.joining(","));
        log.info("Dynamic picture actual-data-nodes: {}", actualDataNodes);

        ContextManager contextManager = getContextManager()
                .orElseThrow(() -> new IllegalStateException("ShardingSphere ContextManager is not available"));
        String databaseName = getDatabaseName(contextManager);
        log.info("Refreshing dynamic picture sharding rule, databaseName={}, requiredTableName={}, physicalTables={}",
                databaseName, requiredTableName, tableNames);
        contextManager.getConfigurationContextManager()
                .alterRuleConfiguration(databaseName, createShardingRuleConfiguration(actualDataNodes));
        contextManager.reloadDatabaseMetaData(databaseName);
        Set<String> runtimeActualTableNames = getRuntimeActualTableNames(contextManager, databaseName);
        if (!runtimeActualTableNames.containsAll(tableNames)) {
            log.warn("Dynamic rule alter did not update runtime route table, rebuilding datasource, expectedTables={}, runtimeActualTables={}",
                    tableNames, runtimeActualTableNames);
            refreshDataSource(tableNames);
        }
        log.info("Dynamic picture sharding rule refreshed, tables={}", tableNames);
    }

    private Set<String> fetchExistingPictureTableNames() {
        Set<String> tableNames = new LinkedHashSet<>();
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND (table_name = ? OR table_name REGEXP ?)
                ORDER BY table_name
                """;
        try (Connection connection = actualDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LOGIC_TABLE_NAME);
            statement.setString(2, "^" + LOGIC_TABLE_NAME + "_[0-9]+$");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tableNames.add(resultSet.getString("table_name"));
                }
            }
        } catch (Exception e) {
            log.warn("Fetch existing picture sharding tables failed, only default picture table will be used", e);
        }
        if (tableNames.isEmpty()) {
            tableNames.add(LOGIC_TABLE_NAME);
        }
        return tableNames;
    }

    private boolean physicalTableExists(String tableName) {
        String sql = """
                SELECT COUNT(1)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """;
        try (Connection connection = actualDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (Exception e) {
            log.warn("Check physical picture sharding table failed, table={}", tableName, e);
            return false;
        }
    }

    private void refreshShardingRuleIfTableExists(String tableName) {
        if (!physicalTableExists(tableName)) {
            throw new IllegalStateException("Physical picture sharding table does not exist, table=" + tableName);
        }
        refreshShardingRule(tableName);
    }

    private ShardingRuleConfiguration createShardingRuleConfiguration(String actualDataNodes) {
        ShardingRuleConfiguration ruleConfig = new ShardingRuleConfiguration();
        ShardingTableRuleConfiguration tableRuleConfig =
                new ShardingTableRuleConfiguration(LOGIC_TABLE_NAME, actualDataNodes);
        tableRuleConfig.setTableShardingStrategy(
                new StandardShardingStrategyConfiguration("spaceId", SHARDING_ALGORITHM_NAME)
        );
        ruleConfig.setTables(Collections.singleton(tableRuleConfig));

        Properties algorithmProps = new Properties();
        algorithmProps.setProperty("strategy", "standard");
        algorithmProps.setProperty(
                "algorithmClassName",
                "cn.com.scour.picture.manager.sharding.PictureShardingAlgorithm"
        );
        ruleConfig.setShardingAlgorithms(
                Collections.singletonMap(
                        SHARDING_ALGORITHM_NAME,
                        new AlgorithmConfiguration("CLASS_BASED", algorithmProps)
                )
        );
        return ruleConfig;
    }

    private Set<String> getRuntimeActualTableNames(ContextManager contextManager, String databaseName) {
        return contextManager.getMetaDataContexts()
                .getMetaData()
                .getDatabase(databaseName)
                .getRuleMetaData()
                .getRules()
                .stream()
                .filter(ShardingRule.class::isInstance)
                .map(ShardingRule.class::cast)
                .findFirst()
                .flatMap(rule -> rule.findTableRule(LOGIC_TABLE_NAME))
                .map(TableRule::getActualDataNodes)
                .orElse(Collections.emptyList())
                .stream()
                .map(dataNode -> dataNode.getTableName())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void refreshDataSource(Set<String> expectedTableNames) {
        if (!(dataSource instanceof RefreshableDataSource refreshableDataSource)) {
            throw new IllegalStateException("DataSource does not support refresh, dataSourceClass="
                    + dataSource.getClass().getName());
        }
        try {
            refreshableDataSource.refresh();
            ContextManager refreshedContextManager = getContextManager()
                    .orElseThrow(() -> new IllegalStateException("ShardingSphere ContextManager is not available after datasource refresh"));
            String refreshedDatabaseName = getDatabaseName(refreshedContextManager);
            Set<String> refreshedActualTableNames = getRuntimeActualTableNames(refreshedContextManager, refreshedDatabaseName);
            if (!refreshedActualTableNames.containsAll(expectedTableNames)) {
                throw new IllegalStateException(String.format(
                        "Refresh picture datasource failed, expectedTables=%s, runtimeActualTables=%s",
                        expectedTableNames, refreshedActualTableNames
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Refresh picture datasource failed", e);
        }
    }

    private Optional<ContextManager> getContextManager() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isWrapperFor(ShardingSphereConnection.class)) {
                log.warn("DataSource connection is not a ShardingSphereConnection, connectionClass={}",
                        connection.getClass().getName());
                return Optional.empty();
            }
            return Optional.of(connection.unwrap(ShardingSphereConnection.class).getContextManager());
        } catch (Exception e) {
            log.warn("Get ShardingSphere ContextManager failed", e);
            return Optional.empty();
        }
    }

    private String getDatabaseName(ContextManager contextManager) {
        Map<String, ShardingSphereDatabase> databaseMap = contextManager.getMetaDataContexts()
                .getMetaData()
                .getDatabases();
        if (databaseMap.containsKey(DATA_SOURCE_NAME) && databaseMap.get(DATA_SOURCE_NAME).containsDataSource()) {
            return DATA_SOURCE_NAME;
        }
        return databaseMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().containsDataSource())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ShardingSphere database metadata contains datasource"));
    }
}
