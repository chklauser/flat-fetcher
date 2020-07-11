# Flat Fetcher - Teach Hibernate to fetch graphs without joins
![CI matser](https://github.com/chklauser/flat-fetcher/workflows/CI/badge.svg?branch=master)

A library that takes a list of "root" entities and a named entity graph and bulk-fetches the graph  without joins. An example
trace would look like this:
```sql
-- Test data set up; Fetching roots...
select car0_.id as id1_0_, car0_.engine_id as engine_i2_0_, car0_.name as name3_0_ from car car0_
-- Roots fetched; fetching graph...
select door0_.id as id1_1_, door0_.car_id as car_id2_1_, door0_.open as open3_1_ from door door0_ where door0_.car_id in (? , ? , ? , ?)
select engine0_.id as id1_2_, engine0_.power as power2_2_ from engine engine0_ where engine0_.id in (? , ? , ? , ?)
select wheel0_.id as id1_3_, wheel0_.car_id as car_id2_3_, wheel0_.size as size3_3_ from wheel wheel0_ where wheel0_.car_id in (? , ? , ? , ?)
-- Done fetching graph.
```

This trace would be generated by this application code:
```java
log.info("Test data set up; Fetching roots...");
var rootCars = rwTx.execute(status ->
        em.createQuery("select c from Car c", Car.class).getResultList()
);

///// WHEN /////
log.info("Roots fetched; fetching graph...");
rwTx.executeWithoutResult(status -> {
    flatFetcher.fetch(Car.class, rootCars, "Car.full");
});
log.info("Done fetching graph.");
```

If you let Hibernate do this on its own, it will generate a monstrous join that generate exponentially more rows than actually exist:
```sql
select * 
from car c 
  left outer join engine e on e.id = c.engine_id
  left outer join door d on d.car_id = c.id
  left outer join wheels w on w.car_id = c.id 
```
This will generate (number of wheels) ⨉ (number of doors) rows for each car. For cars this is fine, but imagine doing the same 
for a national fleet of train compositions. 

The model, for completeness: (getter/setter omitted):
```java
@NamedEntityGraph(name = "Car.full", attributeNodes = {
        @NamedAttributeNode("wheels"),
        @NamedAttributeNode("doors"),
        @NamedAttributeNode("engine")
})
@Entity class Car {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    @Column
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "car")
    @Access(AccessType.PROPERTY)
    private Set<Wheel> wheels;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "car")
    @Access(AccessType.PROPERTY)
    private Set<Door> doors;

    @JoinColumn(name = "engineId")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @LazyToOne(LazyToOneOption.NO_PROXY)
    @Access(AccessType.PROPERTY)
    private Engine engine;

    @Column(name = "engineId", updatable = false, insertable = false)
    private UUID engineId;
}

@Entity class Door {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    @Column(nullable = false)
    boolean open;

    @JoinColumn(name = "carId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @LazyToOne(LazyToOneOption.NO_PROXY)
    @Access(AccessType.PROPERTY)
    Car car;

    @Column(name = "carId", insertable = false, updatable = false)
    UUID carId;
}

@Entity class Wheel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    @Column
    int size;

    @JoinColumn(name = "carId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @LazyToOne(LazyToOneOption.NO_PROXY)
    @Access(AccessType.PROPERTY)
    Car car;

    @Column(insertable = false, updatable = false)
    UUID carId;
}

@Entity class Engine {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    @Column
    int power;

    @OneToOne(fetch = FetchType.LAZY, optional = false, mappedBy = "engine")
    @LazyToOne(LazyToOneOption.NO_PROXY)
    @Access(AccessType.PROPERTY)
    Car car;
}
```

