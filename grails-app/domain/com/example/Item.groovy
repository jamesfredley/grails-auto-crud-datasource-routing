package com.example

import grails.gorm.MultiTenant

/**
 * A simple domain class on the default datasource.
 * Used as a control to verify default datasource routing works correctly.
 */
class Item implements MultiTenant<Item> {

    String tenantId
    String name

    static mapping = {
        tenantId name: 'tenantId'
    }

    static constraints = {
        name blank: false
        tenantId nullable: true
    }
}
