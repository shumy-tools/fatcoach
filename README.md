# FatCoach (experimental)
#### The lazy coach who helps you achieve your goal.
FatCoach is a Back-End as a Service (BaaS) framework (in Kotlin) which abstracts the underlying SQL database from Front-End developers.
Front-end developers use a domain-specific language (DSL) to input data and to query the database. 

FatCoach shares a similar purpose to [GraphQL](https://graphql.org); however, with a different philosophy and architecture. GraphQL was designed mainly for interoperability, and because of that it cannot expose a flexible query DSL to the Front-End. A powerful query language requires an assumption of the underlying database and its capabilities. GraphQL cannot expose SQL features because the data source could be a REST service instead.  

FatCoach is more opinionated about the model and assumes certain datasource capabilities. Because of that, FatCoach is able to expose a powerful query engine to the Front-End. Furthermore, every DSL instruction can be intercepted and checked with plugable security adaptors for access control.

### Example Schema
FatCoach doesn't have a DSL for the schema. The schema is directly defined via the Kotlin language. However, it doesn't have the same rigid structure like [Exposed](https://github.com/JetBrains/Exposed). The Schema can be defined at runtime with the possibility of being used as a framework for Headless CMS.

Lets assume the following data model in UML:

![An Example schema](./docs/Test.png)

Entities are categorized as Master or Detail. The underlying logic for the master/detail structure is that it works as a unit. A detail entity cannot exist without a master and only has one master. A master always owns a detail entity via an owned relation. When such a master/detail connection exists, this generally means that both entities are created in the same business process.

The schema is defined in Koltin via:
```kotlin
val schema = FcSchema {
  val Country = master("Country") {
    text("name")
    text("code")
  }

  val Role = master("Role") {
    text("name")
    datetime("createdAt") {
      deriveFrom { LocalDateTime.now() }
    }

    ownedCol("perms", detail("Permission"){
      text("name")
      text("script")
    })
  }

  master("User") {
    text("name")
    text("email") {
      checkIf { it.contains('@') }
    }

    linkedCol("roles", Role)

    ownedRef("address", detail("Address") {
      text("city")
      text("local")
      linkedRef("country", Country)
    })
  }
}
```

### Input DSL
* Creating a Country entry and returning @id = 1.
```
Country {
  name: "Spain",
  code: "ES"
}
```

* Creating two ```Role``` entries with owned ```Permission``` entries and returning @id = [1, 2].
```
Role {
  name: "admin",
  perms: [
    { name: "activate-some", script: "some-js-script-1" },
    { name: "activate-all", script: "some-js-script-2" }
  ]
}

Role {
  name: "operator",
  perms: [
    { name: "disable-some", script: "other-js-script-1" },
    { name: "disable-all", script: "other-js-script-2" }
  ]
}
```

* Creating a ```User``` entry with an owned ```address``` referencing the ```Country``` entry, and a list of ```roles``` references. Returning @id = 1.
```
User {
  name: "Alex",
  email: "alex@mail.com",
  roles: [1, 2]
  address: {
    city: "Barcelona",
    local: "A place for a Home",
    country: 1
  }
}
```

* Updating the ```User``` name and removing the ```Role``` where @id = 1.
```
User @id == 1 {
  name: "Alex Dupon",
  roles: @del 1
}
```

### Query SQL
A query returns a sub-tree snapshot of the database graph. 

* Queering ```User``` entries filtered by ```User::name``` and ```User::address.country.name```. Returns a JSON structure for the selected fields, including the inner fields for the Address reference. The ```*``` symbol is the selector for all fields. 
```
User | name == "Alex" and address.country.name == "Spain" | {
  name,
  address {
    city,
    country { * }
  }
}
```

* Page and limit can be applied.
```
User limit 2 page 1 { * }
```

* Order can be applied to each field.
```
User {
  (asc 1) name,
  (dsc 2) email
}
```

* Sub-filters can be applied to collections.
```
User {
  *,
  roles | name == "admin" | { * }
}
```