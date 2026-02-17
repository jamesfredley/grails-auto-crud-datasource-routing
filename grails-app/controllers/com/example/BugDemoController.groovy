package com.example

import grails.converters.JSON
import groovy.sql.Sql

import javax.sql.DataSource

/**
 * Demonstrates the auto-implemented Data Service CRUD routing bug.
 *
 * Visit http://localhost:8080/bugDemo/index to see the issue.
 *
 * This controller tests TWO patterns:
 * 1. MetricService — abstract class with @Transactional(connection = 'secondary')
 *    implementing a separate MetricDataService interface
 * 2. MetricInterfaceOnlyDataService — interface-only with @Service + @Transactional(connection)
 *    directly on the interface, no abstract class
 *
 * Both patterns should route auto-implemented save/get/delete/count to the secondary datasource.
 */
class BugDemoController {

    MetricService metricService
    MetricInterfaceOnlyDataService metricInterfaceOnlyDataService
    ItemDataService itemDataService
    DataSource dataSource            // Primary datasource
    DataSource dataSource_secondary  // Secondary datasource

    def index() {
        // Set tenant for DISCRIMINATOR multi-tenancy
        System.setProperty('gorm.tenantId', 'tenant1')

        def results = [:]

        // --- Step 1: Save via auto-implemented Data Service methods ---

        // Save an Item on the default datasource (control)
        def item = new Item(name: 'test-item')
        def savedItem = itemDataService.save(item)
        results.item_saved = savedItem?.id != null

        // Save a Metric via MetricService (auto-implemented save with @Transactional(connection = 'secondary'))
        def metric = new Metric(name: 'test-metric', value: 42.0)
        def savedMetric = null
        try {
            savedMetric = metricService.save(metric)
            results.metric_saved = savedMetric?.id != null
            results.metric_save_error = null
        } catch (Exception e) {
            results.metric_saved = false
            results.metric_save_error = e.message
        }

        // --- Step 1b: Save via interface-only Data Service (no abstract class) ---
        def metric2 = new Metric(name: 'test-metric-interface-only', value: 99.0)
        def savedMetric2 = null
        try {
            savedMetric2 = metricInterfaceOnlyDataService.save(metric2)
            results.metric_interface_only_saved = savedMetric2?.id != null
            results.metric_interface_only_save_error = null
        } catch (Exception e) {
            results.metric_interface_only_saved = false
            results.metric_interface_only_save_error = e.message
        }

        // --- Step 2: Use auto-implemented count() ---
        try {
            results.metric_count_via_data_service = metricService.count()
        } catch (Exception e) {
            results.metric_count_via_data_service = "ERROR: ${e.message}"
        }

        try {
            results.metric_count_via_interface_only = metricInterfaceOnlyDataService.count()
        } catch (Exception e) {
            results.metric_count_via_interface_only = "ERROR: ${e.message}"
        }

        results.item_count_via_data_service = itemDataService.count()

        // --- Step 3: Diagnostic — list tables in each database ---
        results.diagnostic = [:]

        def diagPrimarySql = new Sql(dataSource)
        results.diagnostic.primary_tables = diagPrimarySql.rows(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'"
        ).collect { it.TABLE_NAME }
        diagPrimarySql.close()

        def diagSecondarySql = new Sql(dataSource_secondary)
        results.diagnostic.secondary_tables = diagSecondarySql.rows(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'"
        ).collect { it.TABLE_NAME }
        diagSecondarySql.close()

        // --- Step 4: Query both databases via raw JDBC to prove routing ---

        // Query PRIMARY database directly
        def primarySql = new Sql(dataSource)
        try {
            def primaryMetricCount = primarySql.firstRow(
                    "SELECT COUNT(*) AS cnt FROM METRIC"
            )?.cnt ?: 0
            results.metric_count_in_primary_db = primaryMetricCount
        } catch (Exception e) {
            results.metric_count_in_primary_db = "TABLE NOT FOUND"
        }

        def primaryItemCount = primarySql.firstRow(
                "SELECT COUNT(*) AS cnt FROM ITEM"
        )?.cnt ?: 0
        results.item_count_in_primary_db = primaryItemCount
        primarySql.close()

        // Query SECONDARY database directly
        def secondarySql = new Sql(dataSource_secondary)
        try {
            def secondaryMetricCount = secondarySql.firstRow(
                    "SELECT COUNT(*) AS cnt FROM METRIC"
            )?.cnt ?: 0
            results.metric_count_in_secondary_db = secondaryMetricCount
        } catch (Exception e) {
            results.metric_count_in_secondary_db = "TABLE NOT FOUND"
        }

        try {
            def secondaryItemCount = secondarySql.firstRow(
                    "SELECT COUNT(*) AS cnt FROM ITEM"
            )?.cnt ?: 0
            results.item_count_in_secondary_db = secondaryItemCount
        } catch (Exception e) {
            results.item_count_in_secondary_db = "TABLE NOT FOUND"
        }
        secondarySql.close()

        // --- Step 5: Verdict ---
        def metricInPrimary = results.metric_count_in_primary_db instanceof Number && results.metric_count_in_primary_db > 0
        def metricInSecondary = results.metric_count_in_secondary_db instanceof Number && results.metric_count_in_secondary_db > 0
        def saveFailedBadSql = results.metric_save_error?.toString()?.contains('bad SQL grammar')

        if (saveFailedBadSql && results.diagnostic?.primary_tables instanceof List && !((List) results.diagnostic.primary_tables).contains('METRIC')) {
            results.verdict = 'BUG CONFIRMED: Auto-implemented save() tried to write to PRIMARY database (which has no METRIC table), proving it ignored @Transactional(connection = "secondary"). The METRIC table exists ONLY on secondary — if routing worked, the save would have succeeded.'
            results.bug_present = true
        } else if (metricInPrimary && !metricInSecondary) {
            results.verdict = 'BUG CONFIRMED: Metric was saved to PRIMARY database instead of SECONDARY. Auto-implemented save() ignored @Transactional(connection = "secondary").'
            results.bug_present = true
        } else if (metricInSecondary && !metricInPrimary) {
            results.verdict = 'NO BUG: Metric was correctly saved to SECONDARY database. Auto-implemented save() honored @Transactional(connection = "secondary").'
            results.bug_present = false
        } else if (metricInPrimary && metricInSecondary) {
            results.verdict = 'UNEXPECTED: Metric found in BOTH databases.'
            results.bug_present = true
        } else {
            results.verdict = 'UNEXPECTED: Metric not found in either database. Check save errors above.'
            results.bug_present = null
        }

        results.explanation = [
                setup: 'METRIC table exists ONLY on the secondary database (created manually in BootStrap). If @Transactional(connection = "secondary") is honored, save() routes to secondary and succeeds. If the bug is present, save() routes to primary (no METRIC table) and fails with a SQL error.',
                bug: 'Auto-implemented CRUD methods (save, get, delete, count) on GORM Data Services do not propagate the connection qualifier from @Transactional(connection = "secondary")',
                expected: 'Both MetricService (abstract class pattern) and MetricInterfaceOnlyDataService (interface-only pattern) have @Transactional(connection = "secondary"), so save() should route to the secondary H2 database where the METRIC table exists',
                patterns_tested: [
                        'Pattern 1 (MetricService)': 'Abstract class with @Service(Metric) @Transactional(connection = "secondary") implementing MetricDataService interface',
                        'Pattern 2 (MetricInterfaceOnlyDataService)': 'Interface with @Service(Metric) @Transactional(connection = "secondary") directly — no abstract class'
                ],
                datasources: [
                        primary: 'jdbc:h2:mem:primarydb (ITEM table only)',
                        secondary: 'jdbc:h2:mem:secondarydb (ITEM + METRIC tables)'
                ]
        ]

        render results as JSON
    }
}
