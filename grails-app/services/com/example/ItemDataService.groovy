package com.example

import grails.gorm.services.Service

/**
 * GORM Data Service for Item (default datasource).
 * Used as a control — default datasource routing should always work.
 */
@Service(Item)
interface ItemDataService {

    Item get(Serializable id)

    Item save(Item item)

    void delete(Serializable id)

    Long count()

    List<Item> list(Map args)
}
