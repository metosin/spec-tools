# 0.10.4 (2020-07-24)

* Add OpenAPI 3 schema generation. PR [#236](https://github.com/metosin/spec-tools/pull/236) by [Roman Rudakov](https://github.com/rrudakov)
* Enforce collection type for collection data specs. [#237](https://github.com/metosin/spec-tools/issues/237). PR [#239](https://github.com/metosin/spec-tools/pull/239) by [Wanderson Ferreira]

# 0.10.3 (2020-05-16)

* Always generate non-empty `:body` parameter name for Swagger, fixes https://github.com/metosin/reitit/issues/399

# 0.10.2 (2020-05-05)

* You can use `:json-schema` and `:swagger` spec data to overwrite the generated
  JSON Schema and Swagger, respectively. PRs [#229](https://github.com/metosin/spec-tools/pull/229) by [Wanderson Ferreira]
  and [#231](https://github.com/metosin/spec-tools/pull/231) by [Tommi Reiman](https://github.com/ikitommi)
* Add support for coercing strings to ratios. [#209](https://github.com/metosin/spec-tools/issues/209). PR [#218](https://github.com/metosin/spec-tools/pull/218) by [Wanderson Ferreira]
* Allow disabling title inference. [#198](https://github.com/metosin/spec-tools/issues/198). PR [#221](https://github.com/metosin/spec-tools/pull/221) by [Wanderson Ferreira]
* Fix JSON Schema for `bytes?`. PR [#230](https://github.com/metosin/spec-tools/pull/230) by [Joe Lane](https://github.com/MageMasher)
* Fix Swagger for `sequential?`. [#193](https://github.com/metosin/spec-tools/issues/193). PR [#227](https://github.com/metosin/spec-tools/pull/227) by [Wanderson Ferreira]
* Fix `spec-tools.core/merge` with symbol specs. [#201](https://github.com/metosin/spec-tools/issues/201). PR [#220](https://github.com/metosin/spec-tools/pull/220) by [Wanderson Ferreira]
* Fix decimal coercion for numbers. PR [#217](https://github.com/metosin/spec-tools/pull/217) by [Wanderson Ferreira]
* Fix how `strip-extra-keys-transformer` works with `s/or`. [#178](https://github.com/metosin/spec-tools/issues/178). PR [#219](https://github.com/metosin/spec-tools/pull/219) by [Wanderson Ferreira]
* Fix multi-spec parsing with ClojureScript. PR [#225](https://github.com/metosin/spec-tools/pull/225) by [Toropenko Sergey](https://github.com/akond)

 [Wanderson Ferreira]: https://github.com/wandersoncferreira

# 0.10.1 (2020-01-15)

* Support for `decimal?` coercion by [Wanderson Ferreira](https://github.com/wandersoncferreira)
* Add ability to coerce multi-specs, fixes [#84](https://github.com/metosin/spec-tools/issues/84)
* Add URI transform support. [#194](https://github.com/metosin/spec-tools/pull/194) by [Teemu Heikkilä](https://github.com/theikkila)

# 0.10.0 (2019-06-26)

* Removed the jackson-databind dependency. [#158](https://github.com/metosin/spec-tools/pull/158)
  * **BREAKING** (minor): When encoding dates to strings, the timezone is now encoded as `Z` instead of `+0000`. This makes the output [RFC3339-compatible](https://www.ietf.org/rfc/rfc3339.txt) and keeps it ISO-8601-compatible.

```clojure
;; the new behavior - version 0.10.0 and later
user=> (st/encode inst? (java.util.Date.) st/json-transformer)
"2019-06-26T06:49:15.538Z"

;; the old behavior - version 0.9.3 and earlier
user=> (st/encode inst? (java.util.Date.) st/json-transformer)
"2019-06-26T06:50:02.233+0000"
```

# 0.9.3 (2019-06-07)

* Updated dependency on jackson-databind to fix a vulnerability. [#189](https://github.com/metosin/spec-tools/pull/189) 

# 0.9.2 (2019-05-10)

## 0.9.2-alpha2

* Fix dynamic conforming with composite specs, fixes [#184](https://github.com/metosin/spec-tools/issues/184)

## 0.9.2-alpha1

* Coercion doesn't reverse lazy sequences, fixes [#176](https://github.com/metosin/spec-tools/issues/176), by [salokristian](https://github.com/salokristian).
* `spec-tools.spell` namespace for closing map specs **functionally** using [spell-spec](https://github.com/bhauman/spell-spec).
   * requires explicit dependencies to `com.bhauman/spell-spec` & `expound`
   * `spec-tools.spell/closed` to close a spec (non recursive)
   * `spec-tools.spell/closed-key` to functionally create a closed `s/keys` spec

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.spell :as spell])

(s/def ::name string?)
(s/def ::use-history boolean?)
(s/def ::config (spell/closed (s/keys :opt-un [::name ::use-history])))
(s/def ::options (spell/closed (s/keys :opt-un [::config])))

(def invalid {:config {:name "John" :use-hisory false :countr 1}})

(s/explain-data ::options invalid)
;#:clojure.spec.alpha{:problems ({:path [:config 0],
;                                 :pred #{:use-history},
;                                 :val :use-hisory,
;                                 :via [:user/options :user/config],
;                                 :in [:config :use-hisory 0],
;                                 :expound.spec.problem/type :spell-spec.alpha/misspelled-key,
;                                 :spell-spec.alpha/misspelled-key :use-hisory,
;                                 :spell-spec.alpha/likely-misspelling-of (:use-history)}
;                                {:path [:config 0],
;                                 :pred #{:name :use-history},
;                                 :val :countr,
;                                 :via [:user/options :user/config],
;                                 :in [:config :countr 0],
;                                 :expound.spec.problem/type :spell-spec.alpha/unknown-key,
;                                 :spell-spec.alpha/unknown-key :countr}),
;                     :spec :user/options,
;                     :value {:config {:name "John", :use-hisory false, :countr 1}}}

(println (spell/explain-str ::options invalid))
; -- Misspelled map key -------------
; 
;     {:config {:name ..., :countr ..., :use-hisory ...}}
;                                       ^^^^^^^^^^^
; 
; should probably be: :use-history
; 
; -- Unknown map key ----------------
; 
;     {:config {:name ..., :use-hisory ..., :countr ...}}
;                                           ^^^^^^^
; 
; should be one of: :name, :use-history
; 
; -------------------------
; Detected 2 errors
```

# 0.9.1 (2019-03-21)

* `spec-tools.core/merge` is now visitable by [Erik Assum](https://github.com/slipset).

# 0.9.0 (2019-02-26)

* `st/coerce` doesn't reverse list order, fixes [compojure-api#406](https://github.com/metosin/compojure-api/issues/406)
* Less verbose `st/Spec` form, all `:spec-tools.parse` keys are stripped, fixes [#159](https://github.com/metosin/spec-tools/issues/159)
* **BREAKING**: `nil` specs are allowed, resolved as `any?`
* More robust walker, named specs can be used with `s/or`, `s/and`, `s/coll-of`, `s/map-of`, `s/tuple` and `s/nilable`, fixes [#165](https://github.com/metosin/spec-tools/issues/165).
  * Thanks to [Andrew Rudenko](https://github.com/prepor) and [Nicholas Hurden](https://github.com/nhurden) for contributing!
* Both `st/json-transformer` and `st/string-transformer` also transform values from keywords:

```clj
(require '[spec-tools.core :as st])

(st/coerce (s/map-of int? int?) {:1 1, :2 2} st/json-transformer)
; {1 1, 2 2}
```

* **BREAKING**: `st/select-spec` now uses `st/coerce` instead of `st/decode`. Stripping out extra keys from specs:

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.core :as st])

(s/def ::height integer?)
(s/def ::weight integer?)
(s/def ::person (s/keys :req-un [::height ::weight]))
(s/def ::persons (s/coll-of ::person :into []))
(s/def ::data (s/keys :req-un [::persons]))

(st/select-spec
  ::data
  {:TOO "MUCH"
   :persons [{:INFOR "MATION"
              :height 200
              :weight 80}]})
; => {:persons [{:weight 80, :height 200}]}
```

# 0.8.3 (2019-01-17)

* Identify Leaf Specs
  * Leaf Spec Records have `{:leaf? true}` data
  * Non-leaf Spec can encode to `::s/invalid`, fixing both [#146](https://github.com/metosin/spec-tools/issues/146) & [#147](https://github.com/metosin/spec-tools/issues/146)
  * thanks to [Alex Coyle](https://github.com/alzadude)!
* Remove an implicit dependency on test.check. [#150](https://github.com/metosin/spec-tools/pull/150)
* Make `fail-on-extra-keys-transformer` work again. [#151](https://github.com/metosin/spec-tools/issues/151)

# 0.8.2 (2018-11-10)

* fixed a [issue coercion issue](https://github.com/metosin/spec-tools/issues/145)

# 0.8.1 (2018-11-09)

* updated deps:

```clj
[org.clojure/spec.alpha "1.10.439"] is available but we use "1.10.339"
```

# 0.8.0 (2018-10-21)

* Fix [fishy gen* call in your Spec protocol](https://github.com/metosin/spec-tools/issues/136).
* Support Spec Records with Swagger on cljs by [Miloslav Nenadál](https://github.com/nenadalm)
* Swagger parameters read Spec `:description`, fixes [#135](https://github.com/metosin/spec-tools/issues/135)
* JSON Schema objects get `:title` property from qualified Spec registry name
* All top-level data-specs & nested map data-spec have name derived from `:name`, fixes [#124](https://github.com/metosin/spec-tools/issues/124)
* New `st/coerce` function to coerce a value using form parsing and spec transformers. Can only walk over simple specs, and doesn't require any wrapping of specs. Inspired by [spec-coerce](https://github.com/wilkerlucio/spec-coerce).

```clj
(deftest coercion-test
  (testing "predicates"
    (is (= 1 (st/coerce int? "1" st/string-transformer)))
    (is (= "1" (st/coerce int? "1" st/json-transformer)))
    (is (= :user/kikka (st/coerce keyword? "user/kikka" st/string-transformer))))
  (testing "s/and"
    (is (= 1 (st/coerce (s/and int? keyword?) "1" st/string-transformer)))
    (is (= :1 (st/coerce (s/and keyword? int?) "1" st/string-transformer))))
  (testing "s/or"
    (is (= 1 (st/coerce (s/or :int int? :keyword keyword?) "1" st/string-transformer)))
    (is (= :1 (st/coerce (s/or :keyword keyword? :int int?) "1" st/string-transformer))))
  (testing "s/coll-of"
    (is (= #{1 2 3} (st/coerce (s/coll-of int? :into #{}) ["1" 2 "3"] st/string-transformer)))
    (is (= #{"1" 2 "3"} (st/coerce (s/coll-of int? :into #{}) ["1" 2 "3"] st/json-transformer)))
    (is (= [:1 2 :3] (st/coerce (s/coll-of keyword?) ["1" 2 "3"] st/string-transformer)))
    (is (= ::invalid (st/coerce (s/coll-of keyword?) ::invalid st/string-transformer))))
  (testing "s/keys"
    (is (= {:c1 1, ::c2 :kikka} (st/coerce (s/keys :req-un [::c1]) {:c1 "1", ::c2 "kikka"} st/string-transformer)))
    (is (= {:c1 1, ::c2 :kikka} (st/coerce (s/keys :req-un [(and ::c1 ::c2)]) {:c1 "1", ::c2 "kikka"} st/string-transformer)))
    (is (= {:c1 "1", ::c2 :kikka} (st/coerce (s/keys :req-un [::c1]) {:c1 "1", ::c2 "kikka"} st/json-transformer)))
    (is (= ::invalid (st/coerce (s/keys :req-un [::c1]) ::invalid st/json-transformer))))
  (testing "s/map-of"
    (is (= {1 :abba, 2 :jabba} (st/coerce (s/map-of int? keyword?) {"1" "abba", "2" "jabba"} st/string-transformer)))
    (is (= {"1" :abba, "2" :jabba} (st/coerce (s/map-of int? keyword?) {"1" "abba", "2" "jabba"} st/json-transformer)))
    (is (= ::invalid (st/coerce (s/map-of int? keyword?) ::invalid st/json-transformer))))
  (testing "s/nillable"
    (is (= 1 (st/coerce (s/nilable int?) "1" st/string-transformer)))
    (is (= nil (st/coerce (s/nilable int?) nil st/string-transformer))))
  (testing "s/every"
    (is (= [1] (st/coerce (s/every int?) ["1"] st/string-transformer))))
  (testing "composed"
    (let [spec (s/nilable
                 (s/nilable
                   (s/map-of
                     keyword?
                     (s/or :keys (s/keys :req-un [::c1])
                           :ks (s/coll-of (s/and int?) :into #{})))))
          value {"keys" {:c1 "1" ::c2 "kikka"}
                 "keys2" {:c1 true}
                 "ints" [1 "1" "invalid" "3"]}]
      (is (= {:keys {:c1 1 ::c2 :kikka}
              :keys2 {:c1 true}
              :ints #{1 "invalid" 3}}
             (st/coerce spec value st/string-transformer)))
      (is (= {:keys {:c1 "1" ::c2 :kikka}
              :keys2 {:c1 true}
              :ints #{1 "1" "invalid" "3"}}
             (st/coerce spec value st/json-transformer))))))
```

* `st/decode` first tries to use `st/coerce`, falling back to conforming-based approach
* **BREAKING**: enhanced parsing results from `spec-tools.parse/parse-spec`:
  * all parse result keys have been qualified:
    * `:keys` => `::parse/keys`
    * `:keys/req` => `::parse/keys-req`
    * `:keys/opt` => `::parse/keys-opt`
  * new parser keys `::parse/items`, `::parse/item`, `::parse/key` and `::parse/value`
  * `s/and` and `s/or` are parsed into composite types:
  
```clj
(testing "s/or"
  (is (= {::parse/items [{:spec int?, :type :long} {:spec keyword?, :type :keyword}]
          :type [:or [:long :keyword]]}
         (parse/parse-spec (s/or :int int? :keyword keyword?)))))
(testing "s/and"
  (is (= {::parse/items [{:spec int?, :type :long} {:spec keyword?, :type :keyword}]
          :type [:and [:long :keyword]]}
         (parse/parse-spec (s/and int? keyword?)))))
```
 
# 0.7.2 (2018-09-26)

* Update deps:

```clj
[org.clojure/spec.alpha "0.2.176"] is available but we use "0.1.143"
[org.clojure/clojurescript "1.10.339"] is available but we use "1.10.329"
[com.fasterxml.jackson.core/jackson-databind "2.9.7"] is available but we use "2.9.6"
```

# 0.7.1 (2018-06-25)

* Not setting a Swagger response model doesn't emit empty schema `{}`.
* Spec keys with `swagger` namespace are merged into Swagger schemas, overriding values from `json-schema` namespaced keys:

```clj
(require '[spec-tools.core :as st])
(require '[spec-tools.swagger.core :as swagger])

(swagger/transform
  (st/spec
    {:spec string?
     :json-schema/default ""
     :json-schema/example "json-schema-example"
     :swagger/example "swagger-example"}))
; {:type "string"
;  :default ""
;  :example "swagger-example"}
```

* updated deps:

```clj
[com.fasterxml.jackson.core/jackson-databind "2.9.6"] is available but we use "2.9.5"
```

# 0.7.0 (2018-05-14)

* Fix `rational?` mapping for JSON Schema, fixes [#113](https://github.com/metosin/spec-tools/issues/113)
* Remove `::swagger/extension` expansion in Swagger2 generation.
* Date-conforming is now [ISO8601](https://en.wikipedia.org/wiki/ISO_8601)-compliant on Clojure too, thanks to [Fabrizio Ferrai](https://github.com/f-f).
* new `st/IntoSpec` protocol to convert non-recursively vanilla `clojure.spec` Specs into `st/Spec`:s. Used in `st/encode`, `st/decode`, `st/explain`, `st/explain-data`, `st/conform` and `st/conform!`.
* **BREAKING**: Bye bye conforming, welcome transformers!
  * Guide: https://github.com/metosin/spec-tools#spec-driven-transformations
  * removed: `st/type-conforming`, `st/json-conforming`, `st/string-conforming`
  * new `st/Transformer` protocol to drive spec-driven value transformations
  * spec values can be both encoded (`st/encode`) & decoded (`st/decode`) using a transformer, fixes [#96](https://github.com/metosin/spec-tools/issues/96).
  * renamed ns `spec-tools.conform` into `spec-tools.transform`, covering both encoding & decoding of values
  * `st/type-transformer`, supporting both `:type` and `Spec` level transformations
  * Spec-driven transformations via keys in `encode` and `decode` namespaces.
  * `st/encode`, `st/decode`, `st/explain`, `st/explain-data`, `st/conform` and `st/conform!` take the transformer instance an optional third argument
     * `st/json-transformer`, `st/string-transformer`, `strip-extra-keys-transformer` and `fail-on-extra-keys-transformer` are shipped out-of-the-box.

### Transformer

```clj
(defprotocol Transformer
  (-name [this])
  (-encoder [this spec value])
  (-decoder [this spec value]))
```

### Spec-driven transformations

* use `:encode/*` and `:decode/*` keys from Spec instances to declare how the values should be transformed

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.core :as st])
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

no, as there can be multiple valid representations for a encoded value. But it's quaranteed that a decoded values X is always encoded into Y, which can be decoded back into X, `y -> X -> Y -> X`

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

* use `:type` information from Specs (mostly resolved automatically)

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

`:type` gives you encoders & decoders (and docs) for free, like [Data.Unjson](https://hackage.haskell.org/package/unjson-0.15.2.0/docs/Data-Unjson.html):

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

# 0.6.1 (2018-02-19)

* 0.6.0 deployed correctly

# 0.6.0 (2018-02-19)

* **BREAKING**: the transforming functions in `spec-tools.conform` just transform, dont' validate. Fixes [#92](https://github.com/metosin/spec-tools/issues/92). Thanks to [Benjamin Albrecht](https://github.com/benalbrecht)
* Fixed `s/gen` triggers `IllegalArgumentException` for nested aliased specs [#94](https://github.com/metosin/spec-tools/issues/94) by [@johanwiren](https://github.com/johanwiren).
* New `spec-tools.data-spec/or`, thanks to [Dmitri Sotnikov](https://github.com/yogthos):

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.data-spec :as ds])

(s/conform
  (ds/spec
    ::user
    [(ds/or {:map {:alias string?}
             :string string?})])
  [{:alias "Rudi"}, "Rudolf"])
; [[:map {:alias "rudi"}] [:string "Rudolf"]]
```

* **BREAKING**: `map-of` data-spec keys are also data-specs. So this works now:

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.data-spec :as ds])

(s/valid?
  (ds/spec ::ints {[int?] [int?]})
  {[1 2 3] [4 5 6]})
; true
```

* `ds/spec` supports 1-arity version, allowing extra options `:keys-spec` & `:keys-default`.

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.data-spec :as ds])

(s/valid?
  (ds/spec
    {:name ::optiona-user
     :spec {(ds/req :id) int?
            :age pos-int?
            :name string?}
     :keys-default ds/opt})
  {:id 123})
; true
```

* `ds/spec` option `:name` is only required if non-qualified map keys are present.

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.data-spec :as ds])

(s/valid?
  (ds/spec
    {:spec [{::alias string?}]})
  [{::alias "kikka"}
   {::alias "kukka"}])
; true
```

* `spec-tools.core/merge` that selects only the specced keys from each conformed result, then merges those results onto the original input. This avoids overwriting conformed values with unconformed values while preserving all unspecced keys of the input. Fixes [#90](https://github.com/metosin/spec-tools/issues/90). By [Arttu Kaipiainen](https://github.com/arttuka).

* updated deps:

```clj
[org.clojure/clojure "1.9.0"] is available but we use "1.9.0-beta4"
```

# 0.5.1 (2017-10-31)

* remove `bigdec?` in favor of `decimal?` (1.9.0-beta4 changes)
* updated deps:

```clj
[org.clojure/clojure "1.9.0-beta4"] is available but we use "1.9.0-beta4"
[org.clojure/spec.alpha "0.1.143"] is available but we use "0.1.134"
```

# 0.5.0 (2017-10-19)

* don't publish empty `:required` fields for JSON Schemas, by [acron0](https://github.com/acron0)
* added parsers for `s/merge` & `st/spec`.
* Don't fail on recursive spec visits, fixes [#75](https://github.com/metosin/spec-tools/issues/75)
* **BREAKING**: `spec-tools.visitor/visit-spec` should recurse with `spec-tools.visitor/visit` instead of `spec-tools.visitor/visit-spec`

* updated deps:

```clj
[org.clojure/clojure "1.9.0-beta2"] is available but we use "1.9.0-alpha19"
[org.clojure/clojurescript "1.9.946"] is available but we use "1.9.908"
```

# 0.4.0 (2017-10-11)

* `or` and `and` keys are parsed correctly for JSON Schema & Swagger, Fixes [#79](https://github.com/metosin/spec-tools/issues/79)
* **BREAKING**: `spec-tools.type` is now `spec-tools.parse` with public api of:
  * `parse-spec`: given a spec name, form or instance, maybe returns a spec info map with resolved `:type` and optionally other info, e.g. `:keys`, `:keys/req` and `:keys/opt` for `s/keys` specs.
  * `parse-form`: multimethod to parse info out of a form
* Spec Records of `s/and` are fully resolved now, fixes https://github.com/metosin/compojure-api/issues/336

* updated deps:

```clj
[org.clojure/spec.alpha "0.1.134"] is available but we use "0.1.123"
```

# 0.3.3 (2017-09-01)

* `spec-tools.core/create-spec` fails with qualified keyword if they don't link to a spec, thanks to [Camilo Roca](https://github.com/carocad)

* updated deps:

```clj
[org.clojure/clojure "1.9.0-alpha19"] is available but we use "1.9.0-alpha17"
[org.clojure/clojurescript "1.9.908"] is available but we use "1.9.660"
```

# 0.3.2 (2017-07-29)

* map `spec-tools.spec` predicate symbols into `clojure.core` counterparts for JSON Schema / Swagger mappings.

# 0.3.1 (2017-07-27)

* resolve `:type` from first predicate of `s/and`, thanks to [Andy Chambers](https://github.com/cddr)
* better error messages when trying to create non-homogeneous data-specs for Vectors & Sets

# 0.3.0 (2017-06-30)

* Swagger2 integration (moved from [spec-swagger](https://github.com/metosin/spec-swagger))
  * `spec-tools.swagger.core/transform` to transform Specs into Swagger [Parameter Objects](http://swagger.io/specification/#parameterObject) and [Schema Objects](http://swagger.io/specification/#schemaObject)
  * `spec-tools.swagger.core/swagger-spec` to create valid [Swagger Object](http://swagger.io/specification/#swaggerObject).
  * see [the docs](https://github.com/metosin/spec-tools#swagger2-integration) for details.

* **BREAKING**: More configurable Spec Visitor
  * `spec-tools.visitor/visit takes optionally 4th argument, an options-map, passed into all sub-visits & accepts
  * changed the extension multimethod from `visit` to `visit-spec` (to better support static analysis for arity errors)
  * the `accept` function is now 4-arity (was 3-arity), taking the options-map as 4th argument
  * the `spec-tools.json-schema/transform` also has optional 4-arity with the options-map as 4th argument

* visitor (and by so, json-schema generation) supports also direct predicate specs, via form inference:

```clj
(require '[spec-tools.json-schema :as json-schema])

(json-schema/transform int?)
; {:type "integer", :format "int64"}
```

* added `spec-tools.core/spec-name`, to resolve spec name, like `clojure.spec.alpha/spec-name` but non-private & understands Spec Records.
* added `spec-tools.core/spec-description`, to resolve spec description, understands Spec Records.

* JSON Schema generation set `:title` for Object Schemas based on `st/spec-name`.

* `s/cat` & `s/alt` don't set `:minItems` and `:maxItems` as they are Regexs.

* moved many helper functions to `spec-tools.impl`

# 0.2.2 (2017-06-12)

* Spec Record `describe*` uses the map syntax, e.g. `(st/spec clojure.core/string? {}` => `(st/spec {:spec clojure.core/string?})`

* Spec Records inherit `::s/name` from underlaying specs, fixes [#56](https://github.com/metosin/spec-tools/issues/56)

**[compare](https://github.com/metosin/spec-tools/compare/0.2.1...0.2.2)**

# 0.2.1 (2017-06-09)

* fixed `explain*` for Spec Records

* updated deps:

```clj
[org.clojure/clojure "0.1.123"] is available but we use "0.1.108"
[org.clojure/clojure "1.9.0-alpha17"] is available but we use "1.9.0-alpha16"
[org.clojure/clojurescript "1.9.562"] is available but we use "1.9.542"
```

**[compare](https://github.com/metosin/spec-tools/compare/0.2.0...0.2.1)**

# 0.2.0 (2017-05-16)

* **BREAKING**: update spec to `alpha16`:
  * `clojure.spec` => `clojure.spec.alpha`, `cljs.spec` => `cljs.spec.alpha` etc.

* updated deps:

```clj
[org.clojure/spec.alpha "0.1.108"]
[org.clojure/clojure "1.9.0-alpha16"] is available but we use "1.9.0-alpha15"
[org.clojure/clojurescript "1.9.542"] is available but we use "1.9.518"
```

**[compare](https://github.com/metosin/spec-tools/compare/0.1.1...0.2.0)**

# 0.1.1 (2017-05-10)

* Remove hard dependency on ClojureScript, thanks to [Kenny Williams](https://github.com/kennyjwilli). [#52](https://github.com/metosin/spec-tools/pull/52)

**[compare](https://github.com/metosin/spec-tools/compare/0.1.0...0.1.1)**

# 0.1.0 (2017-05-04)

* Initial release.
