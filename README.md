# spec-tools [![Build Status](https://travis-ci.org/metosin/spec-tools.svg?branch=master)](https://travis-ci.org/metosin/spec-tools) [![Dependencies Status](https://jarkeeper.com/metosin/spec-tools/status.svg)](https://jarkeeper.com/metosin/spec-tools)

Clojure(Script) tools for [clojure.spec](http://clojure.org/about/spec). Bit like [Schema-tools](https://github.com/metosin/schema-tools).

Status: **Experimental**.

## Latest version

[![Clojars Project](http://clojars.org/metosin/spec-tools/latest-version.svg)](http://clojars.org/metosin/spec-tools)

## Features

### Dynamic conforming

#### Problem 

In Schema, there are matchers which know how to coerce a value from one schema to another. One can choose
at runtime which matchers will be used with the coercion. This is awesome for ring/http, where you need
different coercion rules for different parameter sets:

* with `:query`, `:header` & `:path` -parameters, there are only strings -> all non-strings need to be coerced
* with [JSON](http://json.org/), numbers and booleans should not be coerced, everything else should
* with [EDN](https://github.com/edn-format/edn) & [Transit](https://github.com/cognitect/transit-format), nothing should be coerced as all types are presentable

With Spec, the conformers are attached to spec instances instead of types. To support multiple
conformation modes, one needs to create separate specs for different modes.

#### Solution

Spec-tools solves this by defining a set of dynamic type predicates. They can conform values based on a 
dynamic `*conformations*`-parameter, bound in the `spec-tools/conform`. Conformations is a map of
`predicate => conformer`. By default, the following conformations and type predicates are supported:

* conformations: `string-conformations`, `json-conformations` and `nil` (no conforming).
* type predicates: `integer?`, `int?`, `double?`, `keyword?`, `boolean?`, `uuid?` and `inst?`.

Both new conformations and type predicates can be easily added in the client side.

**TODO**: all core predicates should be supported out-of-the-box.

#### Examples

```clj
(require '[clojure.spec :as s])
(require '[spec-tools.core :as st])

(s/def ::age (s/and st/integer? #(> % 18)))

;; default conform with 2-arity
(st/conform ::age "20")
; => ::s/invalid

;; setting the conformations with 3-arity
(st/conform ::age "20" st/json-conformations)
; => ::s/invalid

(st/conform ::age "20" st/string-conformations)
; => 20
```

#### More complex example

```clj
(s/def ::name string?)
(s/def ::birthday st/inst?)

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

(st/conform ::user data st/json-conformations)
; ::s/invalid (doesn't coerce numbers)

(st/conform ::user data st/string-conformations)
; {:name "Ilona"
;  :age 48
;  :languages #{:clj :cljs}
;  :birthdate #inst"1968-01-02T22:04:05.999-00:00"}
```

#### Extending

```clj
(def my-conformations
  (-> st/string-conformations
      (assoc
        keyword?
        (comp
          keyword
          str/reverse
          str/upper-case))))


(st/conform st/keyword? "kikka" st/string-conformations)
; :kikka

(st/conform st/keyword? "kikka" my-conformations)
; :AKKIK
```

### External docs

**TODO**: Like with dynamic conforming, generating different representations (e.g. JSON Schema) of
the spec needs type-based rules. Would be easy if spec was built on Records/Types, not on `reify`.
Reified specs need to be manually parsed. More info here:

* https://github.com/metosin/ring-swagger/issues/95

## License

Copyright © 2016 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
