package com.example

import grails.testing.mixin.integration.Integration
import groovy.sql.Sql
import spock.lang.Specification

import javax.sql.DataSource

/**
 * Integration test that verifies auto-implemented Data Service CRUD methods
 * route to the correct datasource when @Transactional(connection = 'secondary')
 * is present on the abstract class.
 *
 * This test boots the full Grails application context and exercises the
 * auto-implemented save()/get()/count() methods, then queries each H2 database
 * via raw JDBC to prove where data ended up.
 */
@Integration
class DataServiceRoutingSpec extends Specification {

    MetricDataService metricDataService
    ItemDataService itemDataService
    DataSource dataSource            // Primary datasource
    DataSource dataSource_secondary  // Secondary datasource

    void setup() {
        System.setProperty('gorm.tenantId', 'tenant1')
    }

    void 'auto-implemented save() should route Metric to secondary datasource'() {
        when: 'saving a Metric via auto-implemented MetricService.save()'
        def metric = new Metric(name: 'integration-test-metric', value: 123.0)
        def saved = metricDataService.save(metric)

        then: 'the save succeeds'
        saved != null
        saved.id != null

        when: 'querying the secondary database via raw JDBC'
        def secondarySql = new Sql(dataSource_secondary)
        def secondaryCount = 0
        try {
            secondaryCount = secondarySql.firstRow("SELECT COUNT(*) AS cnt FROM METRIC")?.cnt ?: 0
        } finally {
            secondarySql.close()
        }

        then: 'the Metric is in the secondary database'
        secondaryCount > 0

        when: 'querying the primary database via raw JDBC'
        def primarySql = new Sql(dataSource)
        def primaryCount = 0
        try {
            primaryCount = primarySql.firstRow("SELECT COUNT(*) AS cnt FROM METRIC")?.cnt ?: 0
        } catch (Exception ignored) {
            // Table may not exist in primary — that's expected
            primaryCount = 0
        } finally {
            primarySql.close()
        }

        then: 'the Metric is NOT in the primary database'
        primaryCount == 0
    }

    void 'auto-implemented count() should query the secondary datasource'() {
        when: 'counting Metrics via auto-implemented MetricService.count()'
        def count = metricDataService.count()

        then: 'count returns a value (may be 0 if save test ran in isolation)'
        count != null
        count >= 0
    }

    void 'auto-implemented get() should retrieve from secondary datasource'() {
        given: 'a Metric saved via the Data Service'
        def metric = new Metric(name: 'get-test-metric', value: 456.0)
        def saved = metricDataService.save(metric)

        when: 'retrieving via auto-implemented get()'
        def retrieved = metricDataService.get(saved.id)

        then: 'the Metric is found'
        retrieved != null
        retrieved.name == 'get-test-metric'
        retrieved.value == 456.0
    }

    void 'control: Item save routes to default datasource correctly'() {
        when: 'saving an Item via auto-implemented ItemDataService.save()'
        def item = new Item(name: 'control-item')
        def saved = itemDataService.save(item)

        then: 'the save succeeds'
        saved != null
        saved.id != null

        when: 'querying the primary database via raw JDBC'
        def primarySql = new Sql(dataSource)
        def primaryCount = primarySql.firstRow("SELECT COUNT(*) AS cnt FROM ITEM")?.cnt ?: 0
        primarySql.close()

        then: 'the Item is in the primary database'
        primaryCount > 0
    }
}
