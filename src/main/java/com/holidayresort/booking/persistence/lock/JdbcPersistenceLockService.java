package com.holidayresort.booking.persistence.lock;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Acquires the portable named-row locks used by all cross-module transactions.
 *
 * <p>The caller must already be inside a database transaction. Locks are always acquired in enum
 * order even if a caller supplies them in another order.
 */
@Service
public class JdbcPersistenceLockService {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPersistenceLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lock(PersistenceLock... requested) {
        if (requested == null || requested.length == 0) {
            throw new IllegalArgumentException("At least one persistence lock is required");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Persistence locks require an active transaction");
        }
        List<String> ids = Arrays.stream(requested)
                .distinct()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(PersistenceLock::databaseId)
                .toList();
        for (String id : ids) {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT id FROM persistence_lock WHERE id = ? FOR UPDATE", String.class, id);
            if (rows.isEmpty()) {
                throw new IllegalStateException("Unknown persistence lock: " + id);
            }
        }
    }

    public void lockRoomInventory() {
        lock(PersistenceLock.ROOM_INVENTORY);
    }

    public void lockBooking() {
        lock(PersistenceLock.ROOM_INVENTORY, PersistenceLock.BOOKING);
    }

    public void lockPayment() {
        lock(PersistenceLock.ROOM_INVENTORY, PersistenceLock.BOOKING, PersistenceLock.PAYMENT);
    }
}