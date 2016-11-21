# spec-tools [![Build Status](https://travis-ci.org/metosin/spec-tools.svg?branch=master)](https://travis-ci.org/metosin/spec-tools) [![Dependencies Status](https://jarkeeper.com/metosin/spec-tools/status.svg)](https://jarkeeper.com/metosin/spec-tools)

Clojure(Script) tools for [clojure.spec](http://clojure.org/about/spec). Like [Schema-tools](https://github.com/metosin/schema-tools) but for spec.

Status: **Alpha** (as spec is still alpha too).

## Latest version

[![Clojars Project](http://clojars.org/metosin/spec-tools/latest-version.svg)](http://clojars.org/metosin/spec-tools)

## Features

* [Type records](#type-records)
* [Dynamic conforming](#dynamic-conforming)
* [Concise Map Specs](#concise-map-specs)
* [Generating JSON Schemas](#generating-json-schemas)

### Type Records

Spec is implemented using reified protocols. This makes extending current specs non-trivial. Spec-tools introduces Type Records that satisfy the Spec protocols (`clojure.spec.Spec` & `clojure.spec.Specize`) and implement the `clojure.lang.IFn`, so they can act as normal 1-arity functions. Type Records contain a type-hint, a spec predicate and optionally an extra info map for documentation purposes.

```clj
(require '[clojure.spec :as s])
(require '[spec-tools.core :as st])

(def my-integer? (st/type ::st/long integer?))

my-integer?
; #Type{:hint :spec-tools.core/long
;       :pred clojure.core/integer?}

(my-integer? 1)
; true

(s/valid? my-integer? 1)
; true

(assoc my-integer? :info {:description "It's a int"})
; #Type{:hint :spec-tools.core/long
;       :pred clojure.core/integer?
;       :info {:description "It's a int"}}

(eval (s/form (st/type ::st/long integer? {:description "It's a int"})))
; #Type{:hint :spec-tools.core/long
;       :pred clojure.core/integer?
;       :info {:description "It's a int"}}
```

Type records also support [dynamic conforming](#dynamic-conforming), making them great for runtime system border validation.

## Out-of-the-box Types

| type             | type hint        | predicate       |
| -----------------|------------------|-----------------|
| `st/string?`     | `::st/string`    | `string?`       |
| `st/integer?`    | `::st/long`      | `integer?`      |
| `st/int?`        | `::st/long`      | `int?`          |
| `st/double?`     | `::st/double`    | `double?`       |
| `st/keyword?`    | `::st/keyword`   | `keyword?`      |
| `st/boolean?`    | `::st/boolean`   | `boolean?`      |
| `st/uuid?`       | `::st/uuid`      | `uuid?`         |
| `st/inst?`       | `::st/date-time` | `inst?`         |

**TODO**: support all common common types & `clojure.core` predicates.

### Dynamic conforming

[Schema](https://github.com/plumatic/schema) supports runtime-defined schema coercions. Spec does not. Runtime conforming of the specs is needed to use specs effectively with less capable wire formats like JSON.

Type Records are attached with dynamic conformers. By default, no conforming is done. Binding a dynamic var `spec-tools.core/*conformers*` with a function of `type-hint => conformer` will cause the Type to be conformed with the matching conformer.

#### Out-of-the-box conformers

| Name                                | Description                                                                             |
| ------------------------------------|-----------------------------------------------------------------------------------------|
| `spec-tools.core/string-conformers` | Conforms all types from strings (`:query`, `:header` & `:path` -parameters).            |
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
   :birthdate "1968-01-02T15:04:05.999999-07:00"})

;; no type conforming
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
;  :birthdate #inst"1968-01-02T22:04:05.999-00:00"}
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

### Concise Map Specs

Creating `s/keys` specs with non-qualified keyword keys is quite verbose because all keys have to be defined forehand. Spec-tools adds Schema-like concise map-syntax separating keys and values. `spec-tools.core/map` expects a qualified map name (for qualified key generation) and a map with separate keys & values. Keys can be wrapped into `st/opt` or `st/req` to denote whether they are required or not. By default, keys are required. Qualified keywords are supported too, just use the key as a value.

```clj`
(s/def ::age st/integer?)

(def my-map
  (st/map
    ::my-map
    {::id integer?
     ::age ::age
     :boss st/boolean?
     (st/req :name) string?
     (st/opt :description) string?}))

(s/form my-map)
;(clojure.spec/keys
;  :req [:user/id :user/age]
;  :req-un [:user$$my-map/boss :user$$my-map/name]
;  :opt-un [:user$$my-map/description])

(st/conform  my-map {::id 1, ::age 18, :boss false, :name "Terttu"})
; {:user/id 1, :user/age 18, :boss false, :name "Terttu"}
```

**TODO**: create via `st/type` to get map-conformations like `disallow-extra-keys` etc.

### Generating JSON Schemas

Targetting to generate JSON Schemas from arbitrary specs (not just Type Records).

Related: https://github.com/metosin/ring-swagger/issues/95

WIP: https://github.com/metosin/spec-tools/blob/master/test/spec_tools/json_schema_test.clj

## License

Copyright © 2016 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
