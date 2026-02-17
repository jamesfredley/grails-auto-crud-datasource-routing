package com.example

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

/**
 * Unit test for MetricService — verifies the Data Service is wired correctly.
 * The actual routing bug may only manifest in integration or bootRun contexts.
 */
class MetricServiceSpec extends Specification implements ServiceUnitTest<MetricService> {

    void 'MetricService should be instantiated'() {
        expect:
        service != null
    }
}
