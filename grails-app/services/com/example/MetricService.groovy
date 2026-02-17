package com.example

import grails.gorm.services.Service
import grails.gorm.transactions.Transactional

/**
 * Abstract Data Service for Metric with @Transactional(connection = 'secondary').
 *
 * BUG UNDER TEST: The @Transactional(connection = 'secondary') annotation on this
 * class SHOULD cause all auto-implemented CRUD methods (save, get, delete, count)
 * to route to the secondary datasource. The GORM ServiceTransformation AST correctly
 * copies this annotation to the generated $MetricServiceImplementation class at compile
 * time (verified in source). However, at runtime, auto-implemented methods may not
 * reliably honor the connection qualifier.
 *
 * All methods in MetricDataService are auto-implemented by GORM — no method bodies
 * needed here. This class exists solely to carry the @Transactional(connection)
 * annotation.
 */
@Service(Metric)
@Transactional(connection = 'secondary')
abstract class MetricService implements MetricDataService {

    // All CRUD methods auto-implemented by GORM.
    // The @Transactional(connection = 'secondary') on this class should ensure
    // all generated methods route to the secondary datasource.
}
