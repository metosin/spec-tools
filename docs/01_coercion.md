# Spec Coercion

Like [Plumatic Schema](https://github.com/plumatic/schema), Spec-tools differentiates specs (what) and transformers (how). This enables spec values to be transformed between different formats like JSON and EDN. Transformers are implemented using the `spec-tools.core/Transformer` protocol.

```clj
(defprotocol Transformer
  (-name [this])
  (-options [this])
  (-encoder [this spec value])
  (-decoder [this spec value]))
```

Spec-tools ships with following transformers:

| Name                             | Description
|----------------------------------|------------
| `string-transformer`             | String-formats like properties files, query- & path-parameters.
| `json-transformer`               | [JSON](http://json.org/) format, like string, but numbers and booleans are supported
| `strip-extra-keys-transformer`   | Decoding strips out extra keys of `s/keys` specs.
| `strip-extra-values-transformer` | Decoding strips out extra values of `s/tuple` specs.
| `fail-on-extra-keys-transformer` | Decoding fails if `s/keys` specs have extra keys.
| `nil`                            | No transformations, e.g. [EDN](https://github.com/edn-format/edn) & [Transit](https://github.com/cognitect/transit-format).

## Coercion

```clj
(require '[spec-tools.core :as st])
```

For simple transformations, there is `st/coerce`. It takes a spec, a value and a transformer and uses [Spec Walker](./spec-walker.md) to walk over the specs and transform the value using the transformer. It transforms all values it can, leaving non-coercable parts untouched. Behind the scenes, specs are walked using their `s/form` & spec-tools form parser. Coercion is inspired by [spec-coerce](https://github.com/wilkerlucio/spec-coerce).

**NOTE**: with the current spec1 design, form-based coercion is just best effort. There is [CLJ-2251](https://dev.clojure.org/jira/browse/CLJ-2251) which would solve this. 

### Simple example

```clj
(st/coerce int? "1" st/string-transformer) 
; 1

(st/coerce int? "1" st/json-transformer) 
; "1"
```

### Real life example

We have the following specs defined:

```clj
(require '[clojure.spec.alpha :as s])

(s/def ::id int?)
(s/def ::description string?)
(s/def ::amount pos-int?)
(s/def ::delivery inst?)
(s/def ::tags (s/coll-of keyword? :into #{}))
(s/def ::item (s/keys :req-un [::description ::tags ::amount]))
(s/def ::items (s/map-of ::id ::item))
(s/def ::location (s/tuple double? double?))
(s/def ::order (s/keys :req-un [::id ::items ::delivery ::location]))
```

We have a JSON Web Service that reads and writes orders. A valid `::order` value:

```clj
(def order
  {:id 123,
   :items {1 {:description "vadelmalimsa"
              :tags #{:good :red}
              :amount 10},
           2 {:description "korvapuusti"
              :tags #{:raisin :sugar}
              :amount 20}},
   :delivery #inst"2007-11-20T20:19:17.000-00:00",
   :location [61.499374 23.7408149]})

(s/valid? ::order order)
; true
```

If we can use EDN or Transit, we can encode and decode the values automatically. Formats like JSON can't represent all required data types. We can simulate a JSON round-tripping using the [Muuntaja](https://github.com/metosin/muuntaja) library:

```clj
(require '[muuntaja.core :as m])

(def json-order
  (->> order
       (m/encode "application/json")
       (m/decode "application/json")))

json-order
;{:id 123,
; :delivery "2007-11-20T20:19:17Z",
; :items {:1 {:description "vadelmalimsa"
;             :tags ["good" "red"]
;             :amount 10},
;         :2 {:description "korvapuusti"
;             :tags ["raisin" "sugar"]
;             :amount 20}}
; :location [61.499374 23.7408149]}

(s/valid? ::order json-order)
; false
```

The roundtripped data is not correct anymore: the `::delivery` is now just a [ISO 8601](https://fi.wikipedia.org/wiki/ISO_8601) Date string, `::tags` are reduced to strings and the item `::id`s are keywords. Bummer.

We could transform all the invalid values manually, but it would add a lot of boilerplate and would be fragile as the transformations need to be in sync with the potentially evolving data models.

As the specs already contain all the required type information, we can derive the transformations for free. Coercion using the `st/json-transformer`:

```clj
(st/coerce ::order json-order st/json-transformer)
;{:id 123,
; :items {1 {:description "vadelmalimsa"
;            :tags #{:good :red}
;            :amount 10},
;         2 {:description "korvapuusti"
;            :tags #{:raisin :sugar}
;            :amount 20}},
; :delivery #inst"2007-11-20T20:19:17.000-00:00",
; :location [61.499374 23.7408149]}
```

Data is now valid:

```clj
(s/valid? ::order (st/coerce ::order json-order st/json-transformer))
; true
```

### Strict coercion

At system boundaries, like in web apis with external clients and in front of data stores, it is important to check that no extra data is carried in. In `clojure.spec`, maps are open by design. We could add extra constrains to `s/keys` with `s/and` not to contain extra keys, but there are no utilities for it in Spec1 and it would be a static constraint on spec, effecting all call sites. Spec should be open within the boundaries.

We can use coercion for this.

Client sends `::order` with some extra data:

```clj
(def evil-json-order
  (->> (-> order
           (assoc :owner "ikitommi")
           (assoc :LONGSTRING ".................................")
           (assoc-in [:items 1 :discount] 80)
           (assoc-in [:items 2 ::discount] 80))
       (m/encode "application/json")
       (m/decode "application/json")))

evil-json-order
;{:id 123
; :owner "ikitommi"
; :LONGSTRING "................................."
; :items {:1 {:description "vadelmalimsa"
;             :tags ["good" "red"]
;             :discount 80
;             :amount 10},
;         :2 {:description "korvapuusti"
;             :tags ["raisin" "sugar"]
;             ::discount 80
;             :amount 20}}
; :delivery "2007-11-20T22:19:17+02:00"
; :location [61.499374 23.7408149]}
```
Let's coerce the data in:

```clj
(st/coerce
  ::order
  evil-json-order
  st/json-transformer)
;{:id 123,
; :owner "ikitommi"
; :LONGSTRING ".................................",
; :delivery #inst"2007-11-20T20:19:17.000-00:00",
; :items {1 {:description "vadelmalimsa"
;            :tags #{:red :good}
;            :discount 80
;            :amount 10}
;         2 {:description "korvapuusti"
;            :tags #{:raisin :sugar}
;            ::discount 80
;            :amount 20}},
; :location [61.499374 23.7408149]}
```

Also, the invalid data is still there. And according to `clojure.spec`, it's still valid:

```clj
(s/valid?
  ::order
  (st/coerce
    ::order
    evil-json-order
    st/json-transformer))
; true
```

To strip out values not defined in the specs, we can compose a custom transformer that does both JSON->EDN and strips out all extra keys from `s/keys` and `s/tuple` values:

```clj
(def strict-json-transformer
  (st/type-transformer
    st/json-transformer
    st/strip-extra-keys-transformer
    st/strip-extra-values-transformer))
```

Let's coerce again:

```clj
(st/coerce
  ::order
  evil-json-order
  strict-json-transformer)
;{:id 123,
; :items {1 {:description "vadelmalimsa"
;            :tags #{:good :red}
;            :amount 10},
;         2 {:description "korvapuusti"
;            :tags #{:raisin :sugar}
;            :amount 20}},
; :delivery #inst"2007-11-20T20:19:17.000-00:00",
; :location [61.499374 23.7408149]}
```

Extra data is gone. And the value is still valid:

```clj
(s/valid?
  ::order
  (st/coerce
    ::order
    evil-json-order
    strict-json-transformer))
; true
```

## Custom coercion

By default, coercion uses the spec parser `:type` information to apply the coercion. Type-information is extracted automatically from all/most `clojure.core` predicates. If you want to support custom predicates, there are multiple options

### Composite specs

Simplest way is to create composite spec with `s/and`. Coercion reads the types from left to right and applies all found coercions.

```clj
(def adult? (s/and int? #(>= % 18)))

(st/coerce adult? "20" st/string-transformer)
; 20
```

### Manually typed

We can use `st/spec` to annotate specs and add a `:type` hint manually:

```clj
(def adult? 
  (st/spec 
    {:spec #(>= % 18)
     :type :long}))

(st/coerce adult? "20" st/string-transformer)
; 20
```

### Spec-based transformations

Transformations can be included in the spec annotations. Below is a example of an simple keyword, with custom encode and decode transformer attached, together with other documentation:

```clj
(require '[clojure.string :as str])

(s/def ::my-keyword
  (st/spec
    {:spec #(and (simple-keyword? %) (-> % name str/lower-case keyword (= %)))
     :description "a lowercase keyword, uppercase in string-mode"
     :json-schema/type {:type "string", :format "keyword"}
     :json-schema/example "kikka"
     :decode/string #(-> %2 name str/lower-case keyword)
     :encode/string #(-> %2 name str/upper-case)}))

(st/coerce
  ::my-keyword
  "OLIPA.KERRAN/AVARUUS"
  st/string-transformer)
; :olipa.kerran/avaruus
```

By adding keys with namespace of `:decode` or `:encode` and the name of the transformer (e.g. `json`, `string`) you can override the default type-based transformation. The values should be functions of `spec value -> value`.

## Roadmap

* Support for [spec-alpha2](https://github.com/metosin/spec-tools/issues/169)
* [Compile coercers](https://github.com/metosin/spec-tools/issues/167) for better perf

## Web-libs using spec-tools

You can plug-in spec-based coercion easily into any Clojure web app. To get more batteries, you can pick any of the pre-integrated solution from the list below. They provide tools for transparent parameter & response validation based on content-negotiation, extract JSON Schema / Swagger documentation out of specs and more:

* [Reitit](https://github.com/metosin/reitit), fast data driven routing for Clojure/Script. Supports single-page-apps, Ring and Pedestal/Interceptors. The reference implementation for spec-tools based coercion.
* [compojure-api](https://github.com/metosin/compojure-api) sweet routing macros for the JVM.
