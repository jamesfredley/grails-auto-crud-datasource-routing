package com.example

/**
 * A domain class on the default datasource.
 *
 * dbCreate: create-drop creates the METRIC table on the primary database.
 * BootStrap manually creates the same table on the secondary database
 * so we can test Data Service routing.
 *
 * BUG UNDER TEST: MetricService has @Transactional(connection = 'secondary'),
 * so auto-implemented save()/get()/count() should route to secondary.
 * If the bug is present, data ends up in the primary database instead.
 */
class Metric {

    String name
    Double value
    Date dateCreated

    static constraints = {
        name blank: false
        value nullable: false
    }
}
