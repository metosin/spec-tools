# spec-tools [![Build Status](https://travis-ci.org/metosin/spec-tools.svg?branch=master)](https://travis-ci.org/metosin/spec-tools) [![Dependencies Status](https://jarkeeper.com/metosin/spec-tools/status.svg)](https://jarkeeper.com/metosin/spec-tools)

Clojure(Script) tools for [clojure.spec](http://clojure.org/about/spec). Like [Schema-tools](https://github.com/metosin/schema-tools) but for spec.

* [Spec Records](#spec-records)
* [Dynamic Conforming](#dynamic-conforming)
* [Simple Collection Specs](#simple-collection-specs)
* [Generating JSON Schemas](#generating-json-schemas)

Status: **Alpha** (as spec is still alpha too).

## Latest version

[![Clojars Project](http://clojars.org/metosin/spec-tools/latest-version.svg)](http://clojars.org/metosin/spec-tools)

No dependencies, but requires Java 1.8, Clojure `1.9.0-alpha15` and ClojureScript `1.9.456`.

### Spec Records

Clojure Spec is implemented using reified protocols. This makes extending current specs non-trivial. Spec-tools introduces Spec Records that wrap the spec predicates and are easy to modify and extend. They satisfy the Spec protocols (`clojure.spec.Spec` & `clojure.spec.Specize`) and implement the `clojure.lang.IFn` so they can be used a normal function predicates. Specs are created with `spec-tools.core/spec`. The following keys having a special meaning:

| Key                | Description                                                                         |
| -------------------|-------------------------------------------------------------------------------------|
| `:pred`            | The wrapped spec predicate.                                                         |
| `:form`            | The wrapped spec form.                                                              |
| `:type`            | Type hint of the Spec, mostly auto-resolved. Used in runtime conformation           |
| `:name`            | Name of the spec. Contributes to (openapi-)docs                                     |
| `:gen`             | Generator function for the Spec (set via `s/with-gen`)                              |
| `:keys`            | Set of map keys that the spec defines. Extracted from `s/keys` Specs.               |
| `:reason`          | Value is added to `s/explain-data` problems under key `:reason`                     |
| `:description`     | Description of the spec. Contributes to (openapi-)docs                              |
| `:json-schema/...` | Extra data that is merged with unqualifed keys into json-schema (TODO)              |

#### Creating Specs

The following are all equivalent:

```clj
(require '[clojure.spec :as s])
(require '[spec-tools.core :as st])

;; using type inference
(st/spec integer?)

;; with explicit type
(st/spec integer? {:type :long})

;; map form
(st/spec {:pred integer?, :type :long})

; #Spec{:type :long,
;       :pred clojure.core/integer?}
```

#### Example usage

```clj

(def my-integer? (st/spec integer?))

my-integer?
; #Spec{:type :long
;       :pred clojure.core/integer?}

(my-integer? 1)
; true

(s/valid? my-integer? 1)
; true

(assoc my-integer? :info {:description "It's a int"})
; #Spec{:type :long
;       :pred clojure.core/integer?
;       :description "It's a int"}

(eval (s/form (st/spec ::st/long integer? {:description "It's a int"})))
; #Spec{:type :long
;       :pred clojure.core/integer?
;       :description "It's a int"}
```

For most clojure core predicates, the `:type` can be resolved automatically with a help of the `spec-tools.types/resolve-type` multimethod.

### Predefined Spec Records

The following `clojure.core` predicates have a Spec-wrapped version in `spec-tools.specs`:
* `any?`, `some?`, `number?`, `integer?`, `int?`, `pos-int?`, `neg-int?`, `nat-int?`,
`float?`, `double?`, `boolean?`, `string?`, `ident?`, `simple-ident?`, `qualified-ident?`,
`keyword?`, `simple-keyword?`, `qualified-keyword?`, `symbol?`, `simple-symbol?`,
`qualified-symbol?`, `uuid?`, `uri?`, `bigdec?`, `inst?`, `seqable?`, `indexed?`,
`map?`, `vector?`, `list?`, `seq?`, `char?`, `set?`, `nil?`, `false?`, `true?`, `zero?`
`rational?`, `coll?`, `empty?`, `associative?`, `sequential?`, `ratio?` and `bytes?`.

```clj
(require '[spec-tools.specs :as sts])

sts/boolean?
; #Spec{:type :boolean
;       :pred clojure.core/boolean?}

(st/boolean? true)
; true

(assoc sts/boolean? :description "it's an bool")
; #Spec{:type :boolean
;       :pred clojure.core/boolean?
;       :description "It's a bool"}
```

### Custom errors

Can be added to a Spec via the key `:reason`

```clj
(s/explain (st/spec pos-int? {:reason "positive"}) -1)
; val: -1 fails predicate: pos-int?,  positive

(s/explain-data (st/spec pos-int? {:reason "positive"}) -1)
; #:clojure.spec{:problems [{:path [], :pred pos-int?, :val -1, :via [], :in [], :reason "positive"}]}
```

## Dynamic conforming

Spec-tools loans from the awesome [Schema](https://github.com/plumatic/schema) by separating specs (what) from conformers (how). The Spec Records contains a dynamical conformer, which can be instructed at runtime to select a suitable conforming function for the given type. Same specs can conform differently, e.g. when sending data over JSON vs Transit.

By default, Specs conform is a no-op. Binding a dynamic var `spec-tools.core/*conformers*` with a function of `spec/type => spec-conformer` will cause the Spec to be conformed at runtime with the selected conformer.

Conformers are arity2 functions taking the Spec Records and the value and should return either conformed value of `:clojure.spec/invalid`.

The following conformers are found in `spec-tools.conform`:

| Name                | Description                                                                              |
| --------------------|------------------------------------------------------------------------------------------|
| `string-conformers` | Conforms all specs from strings (things like `:query`, `:header` & `:path` -parameters). |
| `json-conformers`   | [JSON](http://json.org/) Conforming (maps, arrays, numbers and booleans not conformed).  |
| `nil`               | No conforming (for [EDN](https://github.com/edn-format/edn) & [Transit](https://github.com/cognitect/transit-format)). |

For maps, there are also special conformers:
* `strip-extra-keys`: strip keys from `s/keys` specs that are not defined
* `fail-on-extra-keys`: **TODO**

#### Conforming examples

```clj
(require '[spec-tools.conform :as stc])

(s/def ::age (s/and sts/integer? #(> % 18)))

;; no conforming
(s/conform ::age "20")
(st/conform ::age "20")
(st/conform ::age "20" nil)
; ::s/invalid

;; json-conforming
(st/conform ::age "20" stc/json-conformers)
; ::s/invalid

;; string-conforming
(st/conform ::age "20" stc/string-conformers)
; 20
```

#### More complex example

```clj
(s/def ::name string?)
(s/def ::birthdate sts/inst?)

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

;; json-conformers doesn't conform numbers
(st/conform ::user data stc/json-conformers)
; ::s/invalid

;; string-conformers for the rescue
(st/conform ::user data stc/string-conformers)
; {:name "Ilona"
;  :age 48
;  :languages #{:clj :cljs}
;  :birthdate #inst"1968-01-02T15:04:05.000-00:00"}
```

#### Map Conforming

```clj
(s/def ::user (st/spec (s/keys :req-un [::name]))

(st/conform
  ::user
  {:name "Inkeri", :age 102}
  {:map stc/strip-extra-keys})
; {:name "Inkeri"}
```

#### Custom conformers

Default conformers are just data, so overriding or extending them is easy:

```clj
(def my-string-conformers
  (-> stc/string-conformers
      (assoc
        :keyword
        (fn [_ value]
          (-> value
              str/upper-case
              str/reverse
              keyword))))

(st/conform st/keyword? "kikka")
; ::s/invalid

(st/conform st/keyword? "kikka" stc/string-conformers)
; :kikka

(st/conform st/keyword? "kikka" my-string-conformers)
; :AKKIK
```

### Simple Collection Specs

Spec-tools enables simple, Schema-like nested collection syntax for specs. `spec-tools.core/coll-spec` takes a qualified spec name (for nested qualified key generation) and a Clojure `map`, `vector` or `set` form as a value. Collection specs are recursive and return `Spec` instances. The following rules apply:

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

### Generating JSON Schemas

**WIP**

Targeting to generate JSON Schemas from arbitrary specs (and Spec Records).

Simple cases work, feel free to contribute more coverage (both impls & tests).

Upcoming [Spec of Specs](http://dev.clojure.org/jira/browse/CLJ-2112) should help.

```clj
(require '[spec-tools.json-schema :as jsc])

(jsc/to-json person-spec)
; {:type "object",
;  :properties {"id" {:type "integer"},
;               "age" {:type "integer"},
;               "name" {:type "string"},
;               "likes" {:type "object"
;                        :additionalProperties {:type "boolean"}},
;               "languages" {:type "array", :items {:type "string"}, :uniqueItems true},
;               "address" {:type "object",
;                          :properties {"street" {:type "string"}
;                                       "zip" {:type "string"}},
;                          :required ("street" "zip")}},
;  :required ("id" "age" "name" "likes" "languages")}
```

Related: https://github.com/metosin/ring-swagger/issues/95

## License

Copyright © 2016-2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
