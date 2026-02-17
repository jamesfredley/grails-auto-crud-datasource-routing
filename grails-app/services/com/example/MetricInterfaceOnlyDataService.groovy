package com.example

import grails.gorm.services.Service
import grails.gorm.transactions.Transactional

/**
 * Interface-only GORM Data Service for Metric — no abstract class.
 *
 * This tests a different pattern from MetricService: here @Service and
 * @Transactional(connection) are placed directly on the interface itself.
 * GORM generates the implementation class ($MetricInterfaceOnlyDataServiceImplementation)
 * at compile time, copying these annotations to the generated class.
 *
 * BUG UNDER TEST: Auto-implemented save(), get(), delete(), and count() should
 * route to the secondary datasource because of @Transactional(connection = 'secondary').
 * If the bug is present, operations route to the primary (default) datasource instead.
 */
@Service(Metric)
@Transactional(connection = 'secondary')
interface MetricInterfaceOnlyDataService {

    Metric get(Serializable id)

    Metric save(Metric metric)

    void delete(Serializable id)

    Long count()

    Metric findByName(String name)
}
