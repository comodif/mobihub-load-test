package org.example.mobihubloadtest.service;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mobihubloadtest.client.DeviceRestClient;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseCleanupService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CassandraOperations cassandraOperations;
    private final DeviceRestClient deviceRestClient;

    @Transactional
    public void cleanupAllTestData() {
        log.info("===== START: Test Data Cleanup Process =====");

        List<Long> clientIds = jdbcTemplate.getJdbcTemplate().queryForList(
                "SELECT id FROM platform.client WHERE name ~ '^TestUser[0-9]+ B2C tenant$'", Long.class);

        if (clientIds.isEmpty()) {
            log.info("No clients found matching 'TestUser%'. Cleanup finished.");
            return;
        }
        log.info("Found {} test clients to clean up. Client IDs: {}", clientIds.size(), clientIds);

        log.info("--> Step 1: Cleaning up Cassandra data...");
        deleteHarshAccelerationData(clientIds);
        deleteHarshDecelerationData(clientIds);
        deleteHarshTurnData(clientIds);
        deleteIgnitionData(clientIds);
        deleteObdData(clientIds);
        deleteTripSummaryData(clientIds);
        deleteGpsDataByClientId(clientIds);
        log.info("--> Step 1: Cassandra data cleanup complete.");
        log.info("--> Step 2: Invalidating asset caches...");
        invalidateAssetCaches(clientIds);
        log.info("--> Step 2: Asset cache invalidation complete.");
        log.info("--> Step 3: Cleaning up PostgreSQL database (respecting foreign keys)...");
        deleteUserRolesByFirstName(clientIds);
        deleteUsersByFirstName(clientIds);
        updateDevicesSubscriptionNull(clientIds);
        deleteSubscriptionsByDeviceImei(clientIds);
        deleteDevicesByImei(clientIds);
        deleteVehiclesByVin(clientIds);
        deleteInventoriesByImei();
        deleteAssetsByName(clientIds);
        deleteFileObjects(clientIds);
        deleteSimsByMsisdn(clientIds);
        deleteSimCardsByImsi(clientIds);
        deleteClientsByName();
        log.info("--> Step 3: PostgreSQL database cleanup complete.");
        log.info("===== FINISHED: Test Data Cleanup Process Completed Successfully =====");

    }

    private void deleteEventData(List<Long> clientIds, String tableName) {
        if (clientIds.isEmpty()) return;

        String sql = "SELECT id as asset_id, client_id FROM platform.asset WHERE client_id IN (:clientIds)";
        List<Map<String, Object>> assetData = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("clientIds", clientIds));
        log.info("{} assets found for {} cleanup.", assetData.size(), tableName);

        TimeRange timeRange = calculateTimeRange();
        log.info("Deleting {} between {} and {}", tableName, timeRange.start / 1000, timeRange.end / 1000);

        for (Map<String, Object> data : assetData) {
            try {
                Long assetId = ((Number) data.get("asset_id")).longValue();
                Integer clientId = ((Number) data.get("client_id")).intValue();

                String cql = String.format("DELETE FROM %s WHERE " +
                        "asset_id = ? AND " +
                        "client_id = ? AND " +
                        "year = ? AND " +
                        "month = ? AND " +
                        "time >= ? AND " +
                        "time <= ?", tableName);

                SimpleStatement statement = SimpleStatement.builder(cql)
                        .addPositionalValues(
                                assetId,
                                clientId,
                                2025,
                                6,
                                timeRange.start / 1000,
                                timeRange.end / 1000
                        )
                        .setConsistencyLevel(ConsistencyLevel.ONE)
                        .build();

                cassandraOperations.execute(statement);

                log.info("{} operation completed for assetId={}, clientId={}",
                        tableName, assetId, clientId);

            } catch (Exception e) {
                log.error("Error deleting {} for asset: {}. Error: {}",
                        tableName, data, e.getMessage(), e);
            }
        }
    }

    private void invalidateAssetCaches(List<Long> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return;
        }
        log.info("Starting asset cache invalidation process...");
        List<Long> assetIds = jdbcTemplate.queryForList(
                "SELECT id FROM platform.asset WHERE client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds),
                Long.class
        );

        if (assetIds.isEmpty()) {
            log.info("No assets found for the given clients. Skipping cache invalidation.");
            return;
        }

        log.info("Found {} assets to invalidate cache for. Processing...", assetIds.size());
        int successCount = 0;
        int errorCount = 0;

        for (Long assetId : assetIds) {
            try {
                deviceRestClient.invalidateAssetCache(assetId);
                log.debug("Successfully invalidated cache for assetId: {}", assetId);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to invalidate cache for assetId: {}. Error: {}", assetId, e.getMessage());
                errorCount++;
            }
        }
        log.info("Asset cache invalidation process completed. Success: {}, Failures: {}.", successCount, errorCount);
    }

    private void deleteUserRolesByFirstName(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'gateway.user_role'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM gateway.user_role WHERE user_id IN (SELECT id FROM gateway.user WHERE first_name = 'Test' AND client_id IN (:clientIds))",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} user_role records.", deleted);
    }

    private void deleteUsersByFirstName(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'gateway.user'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM gateway.user WHERE first_name = 'Test' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} user records.", deleted);
    }

    private void updateDevicesSubscriptionNull(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Setting subscription_id to NULL in 'platform.device'...");
        int updated = jdbcTemplate.update(
                "UPDATE platform.device SET subscription_id = NULL WHERE imei LIKE 'TEST_IMEI_%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Updated {} device records.", updated);
    }

    private void deleteSubscriptionsByDeviceImei(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'platform.subscription'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.subscription WHERE device_id IN (SELECT id FROM platform.device WHERE imei LIKE 'TEST_IMEI_%' AND client_id IN (:clientIds))",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} subscription records.", deleted);
    }

    private void deleteAssetsByName(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'platform.asset'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.asset WHERE name LIKE '34TEST%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} asset records.", deleted);
    }

    private void deleteFileObjects(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'platform.file_object'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.file_object WHERE file_name = 'default_asset.png' AND client_id IN (:clientIds) AND client_id != 1",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} file_object records.", deleted);
    }

    private void deleteDevicesByImei(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'platform.device'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.device WHERE imei LIKE 'TEST_IMEI_%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} device records.", deleted);
    }

    private void deleteVehiclesByVin(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'platform.vehicle'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.vehicle WHERE vin LIKE 'TEST_VIN_%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} vehicle records.", deleted);
    }

    private void deleteInventoriesByImei() {
        log.info("   -> PG: Deleting from 'platform.inventory'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.inventory WHERE imei LIKE 'TEST_IMEI_%'",
                new MapSqlParameterSource() // no parameters
        );
        log.info("   -> PG: Deleted {} inventory records.", deleted);
    }

    private void deleteSimsByMsisdn(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'platform.sim'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.sim WHERE msisdn LIKE '+9050000%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} sim records.", deleted);
    }

    private void deleteSimCardsByImsi(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        log.info("   -> PG: Deleting from 'platform.sim_card'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.sim_card WHERE imsi LIKE 'TEST_IMSI_%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("   -> PG: Deleted {} sim_card records.", deleted);
    }



    private void deleteHarshAccelerationData(List<Long> clientIds) {
        deleteEventData(clientIds, "harsh_acceleration_data");
    }

    private void deleteHarshDecelerationData(List<Long> clientIds) {
        deleteEventData(clientIds, "harsh_deceleration_data");
    }

    private void deleteHarshTurnData(List<Long> clientIds) {
        deleteEventData(clientIds, "harsh_turn_data");
    }

    private void deleteIgnitionData(List<Long> clientIds) {
        deleteEventData(clientIds, "ignition_data");
    }

    private void deleteObdData(List<Long> clientIds) {
        deleteEventData(clientIds, "obd_data");
    }

    private void deleteTripSummaryData(List<Long> clientIds) {
        deleteEventData(clientIds, "trip_summary_data");
    }

    private void deleteGpsDataByClientId(List<Long> clientIds) {
        deleteEventData(clientIds, "gps_data");
    }

    private void deleteClientsByName() {
        log.info("   -> PG: Deleting from 'platform.client'...");
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.client WHERE name LIKE 'TestUser%'",
                new MapSqlParameterSource()
        );
        log.info("   -> PG: Deleted {} client records.", deleted);
    }

    private record TimeRange(long start, long end) {}

    private TimeRange calculateTimeRange() {
        long currentTime = System.currentTimeMillis();
        long oneDayAgo = currentTime - Duration.ofDays(1).toMillis();
        return new TimeRange(oneDayAgo, currentTime);
    }


}
