package org.example.mobihubloadtest.service;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // repo oluştur dockerize et ,invalidate cache yap docker compose oluştur
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CassandraOperations cassandraOperations;

    @Transactional
    public void cleanupAllTestData() {
        log.info("Starting BULK cleanup for all clients starting with 'TestUser'...");

        List<Long> clientIds = jdbcTemplate.getJdbcTemplate().queryForList(
                "SELECT id FROM platform.client WHERE name ILIKE 'TestUser%'", Long.class);

        if (clientIds.isEmpty()) {
            log.info("No clients found matching 'TestUser%'. Cleanup finished.");
            return;
        }
        log.info("Found {} clients to clean up in a single batch.", clientIds.size());
        deleteHarshAccelerationData(clientIds);
        deleteHarshDecelerationData(clientIds);
        deleteHarshTurnData(clientIds);
        deleteIgnitionData(clientIds);
        deleteObdData(clientIds);
        deleteTripSummaryData(clientIds);
        deleteGpsDataByClientId(clientIds);
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

        log.info("Cleanup completed for all clients starting with 'TestUser'.");

    }

    private void deleteUserRolesByFirstName(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM gateway.user_role WHERE user_id IN (" +
                        "SELECT id FROM gateway.user WHERE first_name = 'Test' AND client_id IN (:clientIds))",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} user_role records.", deleted);
    }

    private void deleteUsersByFirstName(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM gateway.user WHERE first_name = 'Test' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} user records.", deleted);
    }

    private void updateDevicesSubscriptionNull(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int updated = jdbcTemplate.update(
                "UPDATE platform.device SET subscription_id = NULL WHERE imei LIKE 'TEST_IMEI_%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Updated subscription_id to NULL for {} device records.", updated);
    }

    private void deleteSubscriptionsByDeviceImei(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.subscription WHERE device_id IN (" +
                        "SELECT id FROM platform.device WHERE imei LIKE 'TEST_IMEI_%' AND client_id IN (:clientIds))",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} subscription records.", deleted);
    }
    private void deleteDevicesByImei(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.device WHERE imei LIKE 'TEST_IMEI_%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} device records.", deleted);
    }

    private void deleteVehiclesByVin(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.vehicle WHERE vin LIKE 'TEST_VIN_%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} vehicle records.", deleted);
    }

    private void deleteInventoriesByImei() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.inventory WHERE imei LIKE 'TEST_IMEI_%'",
                new MapSqlParameterSource() // no parameters
        );
        log.info("Deleted {} inventory records.", deleted);
    }

    private void deleteAssetsByName(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.asset WHERE name LIKE '34TEST%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} asset records.", deleted);
    }

    private void deleteFileObjects(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.file_object WHERE file_name = 'default_asset.png' AND client_id IN (:clientIds) AND client_id != 1",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} file_object records.", deleted);
    }

    private void deleteSimsByMsisdn(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.sim WHERE msisdn LIKE '+9050000%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} sim records.", deleted);
    }

    private void deleteSimCardsByImsi(List<Long> clientIds) {
        if (clientIds.isEmpty()) return;
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.sim_card WHERE imsi LIKE 'TEST_IMSI_%' AND client_id IN (:clientIds)",
                new MapSqlParameterSource("clientIds", clientIds)
        );
        log.info("Deleted {} sim_card records.", deleted);
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

    // Methods for each table
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

    private record TimeRange(long start, long end) {}

    private TimeRange calculateTimeRange() {
        long currentTime = System.currentTimeMillis();
        long oneDayAgo = currentTime - Duration.ofDays(1).toMillis();
        return new TimeRange(oneDayAgo, currentTime);
    }

    private void deleteClientsByName() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM platform.client WHERE name LIKE 'TestUser%'",
                new MapSqlParameterSource()
        );
        log.info("Deleted {} client records.", deleted);
    }
}
