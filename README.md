# Auto-Implemented Data Service CRUD Methods Ignore `@Transactional(connection)`

**Grails Version**: 7.0.7  
**GORM Version**: 7.0.7  
**Severity**: Critical — data silently routes to the wrong datasource

## Bug Description

GORM Data Service auto-implemented CRUD methods (`save()`, `get()`, `delete()`, `count()`) **ignore** the `@Transactional(connection = 'secondary')` annotation on the abstract class and always route to the default datasource.

## Steps to Reproduce

1. Clone this repository
2. Run `./gradlew bootRun`
3. Visit `http://localhost:8080/bugDemo/index`
4. Observe the JSON response — `bug_present: true` confirms the issue

## Expected Behavior

`MetricService` has `@Transactional(connection = 'secondary')`. Auto-implemented `save()` should route the insert to the **secondary** H2 database, where the METRIC table exists.

## Actual Behavior

Auto-implemented `save()` routes to the **default** (primary) datasource instead. Since the METRIC table only exists on secondary, this produces:

```
bad SQL grammar [insert into metric (id, version, name, value, date_created) values (default, ?, ?, ?, ?)]
```

The `count()` method also fails with: `Either class [com.example.Metric] is not a domain class or GORM has not been initialized correctly`.

### Actual Output (verified)

```json
{
  "item_saved": true,
  "metric_saved": false,
  "metric_save_error": "Hibernate operation: could not prepare statement; bad SQL grammar [insert into metric ...]",
  "metric_count_via_data_service": "ERROR: Either class [com.example.Metric] is not a domain class or GORM has not been initialized correctly",
  "item_count_via_data_service": 1,
  "diagnostic": {
    "primary_tables": ["ITEM"],
    "secondary_tables": ["ITEM", "METRIC"]
  },
  "metric_count_in_primary_db": "TABLE NOT FOUND",
  "metric_count_in_secondary_db": 0,
  "verdict": "BUG CONFIRMED: Auto-implemented save() tried to write to PRIMARY database (which has no METRIC table), proving it ignored @Transactional(connection = \"secondary\").",
  "bug_present": true
}
```

**Key evidence**: `diagnostic.secondary_tables` includes `METRIC` — the table exists where routing *should* go. The SQL error proves routing went to primary instead.

## How This App Proves the Bug

The app uses **two separate H2 in-memory databases**:

| Database | URL | Tables |
|----------|-----|--------|
| Primary (default) | `jdbc:h2:mem:primarydb` | `ITEM` only |
| Secondary | `jdbc:h2:mem:secondarydb` | `ITEM` + `METRIC` |

The `METRIC` table is created **only on secondary** (via BootStrap DDL). If `@Transactional(connection = 'secondary')` were honored by auto-implemented methods, `save()` would route to secondary and succeed. Instead, it routes to primary (no METRIC table) and fails — proving the connection qualifier is ignored.

### Setup

```groovy
// MetricService.groovy
@Service(Metric)
@Transactional(connection = 'secondary')
abstract class MetricService implements MetricDataService {
    // All CRUD methods auto-implemented by GORM
    // save(), get(), count(), etc. SHOULD route to secondary
}
```

```groovy
// MetricDataService.groovy (interface)
interface MetricDataService {
    Metric get(Serializable id)
    Metric save(Metric metric)
    void delete(Serializable id)
    Long count()
    List<Metric> list(Map args)
    Metric findByName(String name)
    List<Metric> findAllByName(String name)
}
```

## Root Cause Analysis

In `ServiceTransformation.groovy`:

1. `copyAnnotations(classNode, impl)` (line 253) copies `@Transactional(connection = 'secondary')` to the generated `$MetricServiceImplementation` class at compile time
2. Generated methods have `methodImpl.setDeclaringClass(impl)` (line 303)
3. Implementers call `findConnectionId(newMethodNode)` which delegates to `TransactionalTransform.findTransactionalAnnotation()`
4. `findTransactionalAnnotation()` falls back to `methodNode.getDeclaringClass()` (lines 171-176), which IS the impl class that has the annotation
5. **Despite the AST chain being correct, at runtime the auto-implemented methods do not honor the connection qualifier**

The likely issue is in the implementer classes (e.g., `SaveImplementer`, `FindAllByImplementer`, `CountImplementer`) — they may resolve the connection ID at compile time but not propagate it correctly into the generated method body's `GormEnhancer.findStaticApi()` call.

## Workaround

Manually implement all CRUD methods using `GormEnhancer.findStaticApi()`:

```groovy
@Service(Metric)
@Transactional(connection = 'secondary')
abstract class MetricService implements MetricDataService {

    private GormStaticApi<Metric> getSecondaryApi() {
        GormEnhancer.findStaticApi(Metric, 'secondary')
    }

    Metric save(Metric metric) { secondaryApi.save(metric) }
    Metric get(Serializable id) { secondaryApi.get(id) }
    Long count() { secondaryApi.count() }
    void delete(Serializable id) {
        def entity = secondaryApi.get(id)
        if (entity) { entity.delete() }
    }
}
```

This defeats the purpose of auto-implementation but ensures correct routing.

## Environment

- **Grails**: 7.0.7
- **Spring Boot**: 3.5.10
- **Groovy**: 4.0.30
- **JDK**: 17+
- **H2**: In-memory (two named databases)

## Project Structure

| File | Purpose |
|------|---------|
| `grails-app/domain/com/example/Metric.groovy` | Domain on default datasource (table created on secondary by BootStrap) |
| `grails-app/domain/com/example/Item.groovy` | Control domain on default datasource (MultiTenant) |
| `grails-app/services/com/example/MetricDataService.groovy` | Data Service interface (auto-implemented by GORM) |
| `grails-app/services/com/example/MetricService.groovy` | Abstract class with `@Transactional(connection = 'secondary')` |
| `grails-app/services/com/example/ItemDataService.groovy` | Control Data Service on default datasource |
| `grails-app/controllers/com/example/BugDemoController.groovy` | Demonstrates the bug via JSON endpoint |
| `src/integration-test/groovy/com/example/DataServiceRoutingSpec.groovy` | Integration test verifying routing |
| `grails-app/conf/application.yml` | Two H2 in-memory datasources + DISCRIMINATOR multi-tenancy |
| `grails-app/init/com/example/BootStrap.groovy` | Creates METRIC table on secondary + sets tenant |
