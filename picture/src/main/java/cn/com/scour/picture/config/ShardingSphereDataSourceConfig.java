package cn.com.scour.picture.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.single.api.config.SingleRuleConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring Boot 3 does not load older ShardingSphere spring.factories auto-configuration.
 * Build the ShardingSphere datasource explicitly so MyBatis uses it as the primary datasource.
 */
@Configuration
@Slf4j
public class ShardingSphereDataSourceConfig {

    private static final String DATABASE_NAME = "logic_db";

    private static final String DATA_SOURCE_NAME = "yu_picture";

    private static final String LOGIC_TABLE_NAME = "picture";

    private static final String SHARDING_ALGORITHM_NAME = "picture_sharding_algorithm";

    @Value("${spring.shardingsphere.datasource.yu_picture.driver-class-name}")
    private String driverClassName;

    @Value("${spring.shardingsphere.datasource.yu_picture.url}")
    private String url;

    @Value("${spring.shardingsphere.datasource.yu_picture.username}")
    private String username;

    @Value("${spring.shardingsphere.datasource.yu_picture.password}")
    private String password;

    @Value("${spring.shardingsphere.props.sql-show:false}")
    private String sqlShow;

    @Bean
    @Primary
    public DataSource dataSource() throws SQLException {
        return new RefreshableDataSource(this::createShardingSphereDataSource);
    }

    private DataSource createShardingSphereDataSource() throws SQLException {
        Map<String, DataSource> dataSourceMap = new LinkedHashMap<>();
        DataSource actualDataSource = createHikariDataSource("sharding-actual");
        dataSourceMap.put(DATA_SOURCE_NAME, actualDataSource);

        List<RuleConfiguration> ruleConfigurations = new ArrayList<>();
        ruleConfigurations.add(createShardingRuleConfiguration(actualDataSource));
        ruleConfigurations.add(createSingleRuleConfiguration());

        Properties props = new Properties();
        props.setProperty("sql-show", sqlShow);

        return ShardingSphereDataSourceFactory.createDataSource(
                DATABASE_NAME,
                dataSourceMap,
                ruleConfigurations,
                props
        );
    }

    @Bean
    public DataSource actualDataSource() {
        return createHikariDataSource("ddl-actual");
    }

    private DataSource createHikariDataSource(String poolName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName(poolName);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private ShardingRuleConfiguration createShardingRuleConfiguration(DataSource actualDataSource) {
        ShardingRuleConfiguration ruleConfiguration = new ShardingRuleConfiguration();

        ShardingTableRuleConfiguration pictureTableRule = new ShardingTableRuleConfiguration(
                LOGIC_TABLE_NAME,
                buildPictureActualDataNodes(actualDataSource)
        );
        pictureTableRule.setTableShardingStrategy(
                new StandardShardingStrategyConfiguration("spaceId", SHARDING_ALGORITHM_NAME)
        );
        ruleConfiguration.setTables(Collections.singleton(pictureTableRule));

        Properties algorithmProps = new Properties();
        algorithmProps.setProperty("strategy", "standard");
        algorithmProps.setProperty(
                "algorithmClassName",
                "cn.com.scour.picture.manager.sharding.PictureShardingAlgorithm"
        );
        ruleConfiguration.setShardingAlgorithms(
                Collections.singletonMap(
                        SHARDING_ALGORITHM_NAME,
                        new AlgorithmConfiguration("CLASS_BASED", algorithmProps)
                )
        );
        return ruleConfiguration;
    }

    private String buildPictureActualDataNodes(DataSource actualDataSource) {
        Set<String> tableNames = fetchExistingPictureTableNames(actualDataSource);
        String actualDataNodes = tableNames.stream()
                .map(tableName -> DATA_SOURCE_NAME + "." + tableName)
                .collect(Collectors.joining(","));
        log.info("Initial picture actual-data-nodes: {}", actualDataNodes);
        return actualDataNodes;
    }

    private Set<String> fetchExistingPictureTableNames(DataSource actualDataSource) {
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
            tableNames.add(LOGIC_TABLE_NAME);
            return tableNames;
        }
        if (tableNames.isEmpty()) {
            tableNames.add(LOGIC_TABLE_NAME);
        }
        return tableNames;
    }

    private SingleRuleConfiguration createSingleRuleConfiguration() {
        return new SingleRuleConfiguration(
                Arrays.asList(
                        DATA_SOURCE_NAME + ".user",
                        DATA_SOURCE_NAME + ".space",
                        DATA_SOURCE_NAME + ".space_user"
                ),
                DATA_SOURCE_NAME
        );
    }
}
