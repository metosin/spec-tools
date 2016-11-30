# spec-tools [![Build Status](https://travis-ci.org/metosin/spec-tools.svg?branch=master)](https://travis-ci.org/metosin/spec-tools) [![Dependencies Status](https://jarkeeper.com/metosin/spec-tools/status.svg)](https://jarkeeper.com/metosin/spec-tools)

Clojure(Script) tools for [clojure.spec](http://clojure.org/about/spec). Like [Schema-tools](https://github.com/metosin/schema-tools) but for spec.

Status: **Alpha** (as spec is still alpha too).

## Latest version

[![Clojars Project](http://clojars.org/metosin/spec-tools/latest-version.svg)](http://clojars.org/metosin/spec-tools)

No dependencies, but requires Java 1.8 & Clojure 1.9.0 (alpha14).

## Features

* [Spec Records](#spec-records)
* [Dynamic Conforming](#dynamic-conforming)
* [Simple Collection Specs](#simple-collection-specs)
* [Generating JSON Schemas](#generating-json-schemas)

### Spec Records

Clojure Spec is implemented using reified protocols. This makes extending current specs non-trivial. Spec-tools introduces Spec Records that both satisfy the Spec protocols (`clojure.spec.Spec` & `clojure.spec.Specize`) and implement the `clojure.lang.IFn`, so they can act as normal 1-arity functions. Spec Records contain a type-hint, a spec predicate and optionally an extra info map for documentation purposes.

```clj
(require '[clojure.spec :as s])
(require '[spec-tools.core :as st])

(def my-integer? (st/spec ::st/long integer?))

my-integer?
; #Spec{:hint :spec-tools.core/long
;       :pred clojure.core/integer?}

(my-integer? 1)
; true

(s/valid? my-integer? 1)
; true

(assoc my-integer? :info {:description "It's a int"})
; #Spec{:hint :spec-tools.core/long
;       :pred clojure.core/integer?
;       :info {:description "It's a int"}}

(eval (s/form (st/spec ::st/long integer? {:description "It's a int"})))
; #Spec{:hint :spec-tools.core/long
;       :pred clojure.core/integer?
;       :info {:description "It's a int"}}
```

Spec records also support [dynamic conforming](#dynamic-conforming), making them great for runtime system border validation.

## Out-of-the-box Spec Records

| spec             | type hint        | predicate       |
| -----------------|------------------|-----------------|
| `st/string?`     | `::st/string`    | `string?`       |
| `st/integer?`    | `::st/long`      | `integer?`      |
| `st/int?`        | `::st/long`      | `int?`          |
| `st/double?`     | `::st/double`    | `double?`       |
| `st/keyword?`    | `::st/keyword`   | `keyword?`      |
| `st/boolean?`    | `::st/boolean`   | `boolean?`      |
| `st/uuid?`       | `::st/uuid`      | `uuid?`         |
| `st/inst?`       | `::st/date`      | `inst?`         |

**TODO**: support all common common specs & `clojure.core` predicates.

### Dynamic Conforming

[Schema](https://github.com/plumatic/schema) supports runtime-defined schema coercions. Spec does not. Runtime conforming of the specs is needed to use specs effectively with less capable wire formats like JSON.

Spec Records are attached with dynamic conformers. By default, no conforming is done. Binding a dynamic var `spec-tools.core/*conformers*` with a function of `type-hint => conformer` will cause the Spec to be conformed with the matching conformer.

#### Out-of-the-box conformers

| Name                                | Description                                                                             |
| ------------------------------------|-----------------------------------------------------------------------------------------|
| `spec-tools.core/string-conformers` | Conforms all specs from strings (`:query`, `:header` & `:path` -parameters).            |
| `spec-tools.core/json-conformers`   | [JSON](http://json.org/) Conforming (maps, arrays, numbers and booleans not conformed). |
| `nil`                               | No conforming (for [EDN](https://github.com/edn-format/edn) & [Transit](https://github.com/cognitect/transit-format)). |

In `spec-tools.core` there is a modified `conform` supporting setting the conformers as optional third parameter.

#### Conforming examples

```clj
(s/def ::age (s/and st/integer? #(> % 18)))

;; no conforming
(s/conform ::age "20")
(st/conform ::age "20")
(st/conform ::age "20" nil)
; ::s/invalid

;; json-conforming
(st/conform ::age "20" st/json-conformers)
; ::s/invalid

;; string-conforming
(st/conform ::age "20" st/string-conformers)
; 20
```

#### More complex example

```clj
(s/def ::name string?)
(s/def ::birthdate st/inst?)

(s/def ::languages
  (s/coll-of
    (s/and st/keyword? #{:clj :cljs})
    :into #{}))

(s/def ::user
  (s/keys
    :req-un [::name ::languages ::age]
    :opt-un [::birthdate]))

(def data
  {:name "Ilona"
   :age "48"
   :languages ["clj" "cljs"]
   :birthdate "1968-01-02T15:04:05Z"})

;; no conforming
(st/conform ::user data)
; ::s/invalid

;; doesn't conform numbers
(st/conform ::user data st/json-conformers)
; ::s/invalid

;; all good
(st/conform ::user data st/string-conformers)
; {:name "Ilona"
;  :age 48
;  :languages #{:clj :cljs}
;  :birthdate #inst"1968-01-02T15:04:05.000-00:00"}
```

#### Extending

Default conformers are just data, so extending them is easy:

```clj
(def my-conformers
  (-> st/string-conformers
      (assoc
        ::st/keyword
        (comp
          keyword
          str/reverse
          str/upper-case))))

(st/conform st/keyword? "kikka")
; ::s/invalid

(st/conform st/keyword? "kikka" st/string-conformers)
; :kikka

(st/conform st/keyword? "kikka" my-conformers)
; :AKKIK
```

### Simple Collection Specs

Creating nested anonymous collection specs with Clojure Spec is quite verbose as all leaf specs have to be registered forehand. Spec-tools enables simple, Schema-like nested collection syntax for specs. `spec-tools.core/coll-spec` takes a qualified spec name (for nested qualified key generation) and a vanilla Clojure `map`, `vector` or `set` as a value. Collection specs are recursive.

* Vectors and Sets are homogeneous, and must contains exactly one spec
* Maps have either a single spec key (homogeneous keys) or any number keyword keys.
* Map keyword keys
   * can be qualified or non-qualified (a qualified name will be generated for it)
   * are required by default
   * can be wrapped into `st/opt` or `st/req` for marking them optional or required.
* Map values can be specs, qualified spec names or nested collections.

```clj
(s/def ::age (s/and integer? #(> % 18)))

(def person-spec
  (st/coll-spec
    ::person
    {::id integer?
     :age ::age
     :name string?
     :likes {string? boolean?}
     (st/req :languages) #{keyword?}
     (st/opt :address) {:street string?
                        :zip string?}}))

(s/valid?
  person-spec
  {::id 1
   :age 63
   :name "Liisa"
   :likes {"coffee" true
           "maksapihvi" false}
   :languages #{:clj :cljs}
   :address {:street "Amurinkatu 2"
             :zip "33210"}})
; true

; the following specs got registered:
(st/registry #"user.*")
; #{:user/id
;   :user$person/age
;   :user$person/name
;   :user$person/likes
;   :user$person/languages
;   :user$person/address
;   :user$person$address/zip
;   :user$person$address/street}
```

* **TODO**: Support optional values via `st/maybe`
* **TODO**: create via `st/spec` to allow type-hinted map-conformations like `disallow-extra-keys` etc.

### Generating JSON Schemas

Targeting to generate JSON Schemas from arbitrary specs (not just Spec Records).

```clj
(require '[spec-tools.json-schema :as jsc])

(jsc/to-json person-spec)
; {:type "object",
;  :properties {"id" {:type "integer"},
;               "age" {:type "integer"},
;               "name" {:type "string"},
;               "likes" {:type "object"
;                        :additionalProperties {:type "boolean"}},
;               "languages" {:type "array", :items {:type "string"}},
;               "address" {:type "object",
;                          :properties {"street" {:type "string"}
;                                       "zip" {:type "string"}},
;                          :required ("street" "zip")}},
;  :required ("id" "age" "name" "likes" "languages")}
```

Related: https://github.com/metosin/ring-swagger/issues/95

WIP: https://github.com/metosin/spec-tools/blob/master/test/spec_tools/json_schema_test.clj

## License

Copyright © 2016 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
