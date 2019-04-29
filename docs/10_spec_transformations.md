# Spec Driven Transformations

**NOTE**: this document is partially rewritten as [spec coercion](01_coercion.md).

Like [Plumatic Schema](https://github.com/plumatic/schema), Spec-tools differentiates specs (what) and transformers (how). This enables spec values to be transformed between different formats like JSON and EDN. Core concept is the `Transformer` protocol:

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

### Conforming

Functions `explain`, `explain-data`, `conform` and `conform!` take the transformer an optional third argument and pass it into Specs via dynamic binding. Before [CLJ-2116](https://dev.clojure.org/jira/browse/CLJ-2116) or [CLJ-2251](https://dev.clojure.org/jira/browse/CLJ-2251) are fixed, specs need to be wrapped into Spec Records to make this work.

### Encoding and Decoding

There are also `encode` & `decode` functions that combine the two approaches and considered the best way to transform the values. `decode` first tries to use `coerce` and if that doesn't make the value valid against the given spec, falls back to `conform` & `unform` which can be used for all specs.

### Spec-driven transformations

* `:encode/*` and `:decode/*` keys from Specs to declare how the values should be transformed in & out from different formats
* both take a 2-arity function of `spec value => value` to do the actual transformation

```clj
(require '[clojure.string :as str])

(s/def ::spec
  (st/spec
    {:spec #(and (simple-keyword? %) (-> % name str/lower-case keyword (= %)))
     :description "a lowercase keyword, encoded in uppercase in string-mode"
     :decode/string #(-> %2 name str/lower-case keyword)
     :encode/string #(-> %2 name str/upper-case)}))

(st/decode ::spec :kikka)
; :kikka

(as-> "KiKka" $
      (st/decode ::spec $))
; :clojure.spec.alpha/invalid

(as-> "KiKka" $
      (st/decode ::spec $ st/string-transformer))
; :kikka

(as-> "KiKka" $
      (st/decode ::spec $ st/string-transformer)
      (st/encode ::spec $ st/string-transformer))
; "KIKKA"
```

### Spec [Bijections](https://en.wikipedia.org/wiki/Bijection)?

no, as there can be multiple valid representations for a encoded value. But it can be guaranteed that a decoded values X is always encoded into Y, which can be decoded back into X: `y -> X -> Y -> X`

```clj
(as-> "KikKa" $
      (doto $ prn)
      (st/encode ::spec $ st/string-transformer)
      (doto $ prn)
      (st/decode ::spec $ st/string-transformer)
      (doto $ prn)
      (st/encode ::spec $ st/string-transformer)
      (prn $))
; "KikKa"
; "KIKKA"
; :kikka
; "KIKKA"
```

### Type-driven transformations

* Uses `:type` information from Specs
  * resolved automatically for most core predicates.
  * top-level spec arguments in `encode` & `decode` etc are transformed into Spec Records automatically using `IntoSpec` protocol.
  * standard types are: `:long`, `:double`, `:boolean`, `:string`, `:keyword`, `:symbol`, `:uuid`, `:uri`, `:bigdec`, `:date`, `:ratio`, `:map`, `:set` and `:vector`.

```clj
(as-> "2014-02-18T18:25:37Z" $
      (st/decode inst? $))
; :clojure.spec.alpha/invalid

;; decode using string-transformer
(as-> "2014-02-18T18:25:37Z" $
      (st/decode inst? $ st/string-transformer))
; #inst"2014-02-18T18:25:37.000-00:00"

;; encode using string-transformer
(as-> "2014-02-18T18:25:37Z" $
      (st/decode inst? $ st/string-transformer)
      (st/encode inst? $ st/string-transformer))
; "2014-02-18T18:25:37.000+0000"
```

When creating custom specs, `:type` gives you encoders & decoders (and docs!) for free, like with [Data.Unjson](https://hackage.haskell.org/package/unjson-0.15.2.0/docs/Data-Unjson.html).

```clj
(s/def ::kw
  (st/spec
    {:spec #(keyword %) ;; anonymous function
     :type :keyword}))  ;; encode & decode like a keyword

(st/decode ::kw "kikka" st/string-transformer)
;; :kikka

(st/decode ::kw "kikka" st/json-transformer)
;; :kikka
```

### Transforming nested specs

Because of current design of clojure.spec, we need to wrap all non top-level specs into Spec Records manually to enable transformations.

```clj
(s/def ::name string?)
(s/def ::birthdate spec/inst?)

(s/def ::languages
  (s/coll-of
    (s/and spec/keyword? #{:clj :cljs})
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

;; no transformer
(st/decode ::user data)
; ::s/invalid

;; json-transformer doesn't transform numbers
(st/decode ::user data st/json-transformer)
; ::s/invalid

;; string-transformer for the rescue
(st/decode ::user data st/string-transformer)
; {:name "Ilona"
;  :age 48
;  :languages #{:clj :cljs}
;  :birthdate #inst"1968-01-02T15:04:05.000-00:00"}
```

#### Transforming Map Specs

To strip out extra keys from a keyset:

```clj
(s/def ::name string?)
(s/def ::street string?)
(s/def ::address (st/spec (s/keys :req-un [::street])))
(s/def ::user (st/spec (s/keys :req-un [::name ::address])))

(def inkeri
  {:name "Inkeri"
   :age 102
   :address {:street "Satamakatu"
             :city "Tampere"}})

(st/decode ::user inkeri st/strip-extra-keys-transformer)
; {:name "Inkeri"
;  :address {:street "Satamakatu"}}
```

There are also a shortcut for this, `select-spec`:

```clj
(st/select-spec ::user inkeri)
; {:name "Inkeri"
;  :address {:street "Satamakatu"}}
```

### Custom Transformers

Transformers should have a simple keyword name and optionally type-based decoders, encoders, default decoder and -encoder set. Currently there is no utility to verify that `y -> X -> Y -> X` holds for custom transformers.

```clj
(require '[clojure.string :as str])
(require '[spec-tools.transform :as stt])

(defn transform [_ value]
  (-> value
      str/upper-case
      str/reverse
      keyword))

;; string-decoding + special keywords
;; encoding writes strings by default
(def my-string-transformer
  (type-transformer
    {:name :custom
     :decoders (merge
                 stt/string-type-decoders
                 {:keyword transform})
     :default-encoder stt/any->string}))

(decode keyword? "kikka")
; :clojure.spec.alpha/invalid

(decode keyword? "kikka" my-string-transformer)
; :AKKIK

; spec-driven transforming
(decode
  (spec
    {:spec #(keyword? %)
     :decode/custom transform})
  "kikka"
  my-string-transformer)
; :AKKIK

;; defaut encoding to strings
(encode int? 1 my-string-transformer)
; "1"
```

Type-based transformer encoding & decoding mappings are defined as data, so they are easy to compose:

```clj
(def strict-json-transformer
  (st/type-transformer
    {:name :custom
     :decoders (merge
                 stt/json-type-decoders
                 stt/strip-extra-keys-type-decoders)
     :encoders stt/json-type-encoders}))
```

Or using `type-transformer` directly:

```clj
(def strict-json-transformer
  (st/type-transformer
    st/json-transformer
    st/strip-extra-keys-transformer
    st/strip-extra-values-transformer))
```
