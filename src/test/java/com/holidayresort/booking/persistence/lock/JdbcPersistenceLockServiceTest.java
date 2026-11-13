package com.holidayresort.booking.persistence.lock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcPersistenceLockServiceTest {

    @Autowired
    JdbcPersistenceLockService locks;

    @Test
    void acquiresTheCanonicalLockOrderRegardlessOfArgumentOrder() {
        assertThatCode(() -> locks.lock(PersistenceLock.PAYMENT, PersistenceLock.ROOM_INVENTORY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnEmptyLockRequest() {
        assertThatThrownBy(() -> locks.lock())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refusesToAcquireLocksWithoutACallerTransaction() {
        assertThatThrownBy(() -> locks.lockRoomInventory())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }
}