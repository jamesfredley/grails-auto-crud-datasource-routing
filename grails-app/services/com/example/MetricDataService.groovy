package com.example

/**
 * GORM Data Service interface for Metric.
 *
 * All methods are auto-implemented by the GORM AST transform at compile time
 * via the abstract class MetricService, which provides
 * @Transactional(connection = 'secondary') to route all operations to the
 * secondary datasource.
 *
 * NOTE: @Service(Metric) is on the abstract class only — not this interface.
 * Having it on both causes compilation failures.
 */
interface MetricDataService {

    Metric get(Serializable id)

    Metric save(Metric metric)

    void delete(Serializable id)

    Long count()

    List<Metric> list(Map args)

    Metric findByName(String name)

    List<Metric> findAllByName(String name)
}
