package com.holidayresort.booking.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** D-owned transaction boundary shared by JDBC adapters. */
@Configuration
public class PersistenceConfiguration {

    @Bean
    TransactionTemplate persistenceTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}