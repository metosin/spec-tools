# spec-tools [![Build Status](https://travis-ci.org/metosin/spec-tools.svg?branch=master)](https://travis-ci.org/metosin/spec-tools) [![Dependencies Status](https://jarkeeper.com/metosin/spec-tools/status.svg)](https://jarkeeper.com/metosin/spec-tools)

Tools for working with the [clojure.spec](http://clojure.org/about/spec). Bit like [Schema-tools](https://github.com/metosin/schema-tools).

Status: **Experimental**.

## Latest version

[![Clojars Project](http://clojars.org/metosin/spec-tools/latest-version.svg)](http://clojars.org/metosin/spec-tools)

## Features

### Dynamic conforming

In Schema, coercions are built for different types: we can create matchers that know how to transform data 
of one type to another. We can choose at runtime which matchers we use with the coercion. This is needed
to handle different wire-formats with the ring/http:

* With `:query`/`:header`/`:path` -parameters, we can only have Strings -> we have to derive all types from those
* With `JSON`, Numbers and Booleans should not be coerced, everything else should
* With `EDN`/`Transit`, nothing should be coerced (all types are presentable)

With Spec, the conformers are attached to spec instances instead of types. To support multiple
conformation modes, one needs to create separate specs for different modes.

Spec-tools defines a set of "type" predicates which have a dynamic conformer attached. Dynamic conformers
can be used in multiple modes: `:string`, `:json` or default. `spec-tools-core` holds a set of predicates that have
the dynamic conformer attached: `integer?`, `int?`, `double?`, `keyword?`, `boolean?`, `uuid?` and `inst?`.

**TODO** all core-predicates should be supported and the whole thing should be more polymorphic.

#### Examples

```clj
(require '[clojure.spec :as s])
(require '[spec-tools.core :as st])

(s/def ::age (s/and st/integer? #(> % 18)))
(s/def ::birthday st/inst?)

(st/conform ::age "20")
; => ::s/invalid

(st/conform :json ::age "20")
; => ::s/invalid

(st/conform :string ::age "20")
; => 20

(st/conform :string ::birthdate "1912-01-02T15:04:05.999999-07:00")
; => #inst"1912-01-02T22:04:05.999-00:00"
```

#### Something more complex

```clj
(s/def ::name string?)
(s/def ::languages (s/coll-of (s/and st/keyword? #{:clj :cljs}) :into #{}))
(s/def ::user (s/keys :req-un [::name ::languages ::age]
                      :opt-un [::birthdate]))

(st/conform :json ::user {:name "Tiina"
                          :age 48
                          :languages ["clj" "cljs"]
                          :birthdate "1968-01-02T15:04:05.999999-07:00"})
; {:name "Ilona"
;  :age 48
;  :languages #{:clj :cljs}
;  :birthdate #inst"1968-01-02T22:04:05.999-00:00"}
```

### Api-docs

Like with dynamic conforming, generating different representations (e.g. JSON Schema) of the spec needs
type-based rules, not instance-based. More info here:

* https://github.com/metosin/ring-swagger/issues/95

## License

Copyright © 2016 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
