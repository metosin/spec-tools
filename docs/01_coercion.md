## Spec Coercion

Like [Plumatic Schema](https://github.com/plumatic/schema), Spec-tools differentiates specs (what) and transformers (how). This enables spec values to be transformed between different formats like JSON and EDN. Transformations implement the `Transformer` protocol in `spec-tools.core`:

```clj
(defprotocol Transformer
  (-name [this])
  (-options [this])
  (-encoder [this spec value])
  (-decoder [this spec value]))
```

Spec-tools ships with following transformer implementations:

| Name                             | Description
|----------------------------------|------------
| `string-transformer`             | String-formats like properties files, query- & path-parameters.
| `json-transformer`               | [JSON](http://json.org/) format, like string, but numbers and booleans are supported
| `strip-extra-keys-transformer`   | Decoding strips out extra keys of `s/keys` specs.
| `strip-extra-values-transformer` | Decoding strips out extra values of `s/tuple` specs.
| `fail-on-extra-keys-transformer` | Decoding fails if `s/keys` specs have extra keys.
| `nil`                            | No transformations, e.g. [EDN](https://github.com/edn-format/edn) & [Transit](https://github.com/cognitect/transit-format).

### Coercion

```clj
(require '[spec-tools.core :as st])
```

For simple transformations, there is `st/coerce`. It takes a spec, value and a transformer and uses a [Spec Walker](./spec-walker.md) to walk over the specs. It transforms all values it can, leaving non-coercable parts untouched. Behind the scenes, specs are walked using their `s/form` & spec-tools form parser. Coercion is inspired by [spec-coerce](https://github.com/wilkerlucio/spec-coerce).

**NOTE**: with the current spec1 design, form-based coercion is just best effort. There is [CLJ-2251](https://dev.clojure.org/jira/browse/CLJ-2251) which would solve this. 

#### Simple example

```clj
(st/coerce int? "1" st/string-transformer) 
; 1

(st/coerce int? "1" st/json-transformer) 
; "1"
```

#### Real life example

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

We also have a JSON Web Service that would like to read and write the `::order` data:

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

The problem is, that JSON can't represent all the required data types. We can simulate a JSON round-trip using the [Muuntaja](https://github.com/metosin/muuntaja) library:

```clj
(require '[muuntaja.core :as m])

(def order'
  (->> order
       (m/encode "application/json")
       (m/decode "application/json")))

order'
;{:id 123,
; :delivery "2007-11-20T20:19:17Z",
; :items {:1 {:description "vadelmalimsa"
;             :tags ["good" "red"]
;             :amount 10},
;         :2 {:description "korvapuusti"
;             :tags ["raisin" "sugar"]
;             :amount 20}}
; :location [61.499374 23.7408149]}

(s/valid? ::order order')
; false
```

It's not correct anymore: the `::delivery` is now just a [ISO 8601](https://fi.wikipedia.org/wiki/ISO_8601) Date string, the `::tags` are reduced to strings and the item `::id`s are keywords. Bummer.

We could add a custom transformation and manually coerce all the fields but it would add a lot of boilerplate and would be fragile and hard to maintain as the number of specs goes up. We have all the required type/spec information already present, we can use them to get transformation for free.

Let's coerce the data using the `st/json-transformer`:

```clj
(st/coerce ::order order' st/json-transformer)
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
(s/valid? ::order (st/coerce ::order order' st/json-transformer))
; true
```

That wasn't too hard.

#### Strict coercion

At system boundaries (web apis with external clients, before saving data to the database), it important to check data no extra data is carried in. In `clojure.spec`, maps are open by design. We could add extra constrains to maps with `s/and` not to contain extra keys, but it would be a static constraint on spec, effecting all use cases. Let's use coercion instead.

Let's define an internal spec, to be used inside our business domain:

```clj
(s/def ::discount (s/int-in 0 100))
```

Client send `::order` with some extra data:

```clj
(def order2'
  (->> (-> order
           (assoc :owner "ikitommi")
           (assoc :LONGSTRING ".................................")
           (assoc-in [:items 1 :discount] 80)
           (assoc-in [:items 2 ::discount] 80))
       (m/encode "application/json")
       (m/decode "application/json")))

order2'
;{:id 123
; :owner "ikitommi"
; :LONGSTRING "................................."
; :items {1 {:description "vadelmalimsa"
;            :tags #{:red :good}
;            :discount 80
;            :amount 10}
;         2 {:description "korvapuusti"
;            :tags #{:raisin :sugar}
;            ::discount 80
;            :amount 20}}
; :delivery "2007-11-20T22:19:17+02:00"
; :location [61.499374 23.7408149]}
```

Let's coerce the data in:

```clj
(st/coerce
  ::order
  order2'
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
; :location [61.499374 23.7408149 "..."]}
```

That's not good. And it's valid:

```clj
(s/valid?
  ::order
  (st/coerce
    ::order
    order2'
    st/json-transformer))
; true
```

To strip out values not defined in the specs, we can create a custom transformer that does JSON->EDN and strips out all extra keys from `s/keys` and `s/tuple` values:

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
  order2'
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

Much better. And it's still valid:

```clj
(s/valid?
  ::order
  (st/coerce
    ::order
    order2'
    strict-json-transformer))
; true
```
