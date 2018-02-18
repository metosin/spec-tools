## 0.6.1 (19.2.2018)

* 0.6.0 deployed correctly

## 0.6.0 (19.2.2018)

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

## 0.5.1 (31.10.2017)

* remove `bigdec?` in favor of `decimal?` (1.9.0-beta4 changes)
* updated deps:

```clj
[org.clojure/clojure "1.9.0-beta4"] is available but we use "1.9.0-beta4"
[org.clojure/spec.alpha "0.1.143"] is available but we use "0.1.134"
```

## 0.5.0 (19.10.2017)

* don't publish empty `:required` fields for JSON Schemas, by [acron0](https://github.com/acron0)
* added parsers for `s/merge` & `st/spec`.
* Don't fail on recursive spec visits, fixes [#75](https://github.com/metosin/spec-tools/issues/75)
* **BREAKING**: `spec-tools.visitor/visit-spec` should recurse with `spec-tools.visitor/visit` instead of `spec-tools.visitor/visit-spec`

* updated deps:

```clj
[org.clojure/clojure "1.9.0-beta2"] is available but we use "1.9.0-alpha19"
[org.clojure/clojurescript "1.9.946"] is available but we use "1.9.908"
```

## 0.4.0 (11.10.2017)

* `or` and `and` keys are parsed correctly for JSON Schema & Swagger, Fixes [#79](https://github.com/metosin/spec-tools/issues/79)
* **BREAKING**: `spec-tools.type` is now `spec-tools.parse` with public api of:
  * `parse-spec`: given a spec name, form or instance, maybe returns a spec info map with resolved `:type` and optionally other info, e.g. `:keys`, `:keys/req` and `:keys/opt` for `s/keys` specs.
  * `parse-form`: multimethod to parse info out of a form
* Spec Records of `s/and` are fully resolved now, fixes https://github.com/metosin/compojure-api/issues/336

* updated deps:

```clj
[org.clojure/spec.alpha "0.1.134"] is available but we use "0.1.123"
```

## 0.3.3 (1.9.2017)

* `spec-tools.core/create-spec` fails with qualified keyword if they don't link to a spec, thanks to [Camilo Roca](https://github.com/carocad)

* updated deps:

```clj
[org.clojure/clojure "1.9.0-alpha19"] is available but we use "1.9.0-alpha17"
[org.clojure/clojurescript "1.9.908"] is available but we use "1.9.660"
```

## 0.3.2 (29.7.2017)

* map `spec-tools.spec` predicate symbols into `clojure.core` counterparts for JSON Schema / Swagger mappings.

## 0.3.1 (27.7.2017)

* resolve `:type` from first predicate of `s/and`, thanks to [Andy Chambers](https://github.com/cddr)
* better error messages when trying to create non-homogeneous data-specs for Vectors & Sets

## 0.3.0 (30.6.2017)

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

## 0.2.2 (2017-06-12)

* Spec Record `describe*` uses the map syntax, e.g. `(st/spec clojure.core/string? {}` => `(st/spec {:spec clojure.core/string?})`

* Spec Records inherit `::s/name` from underlaying specs, fixes [#56](https://github.com/metosin/spec-tools/issues/56)

**[compare](https://github.com/metosin/spec-tools/compare/0.2.1...0.2.2)**

## 0.2.1 (2017-06-09)

* fixed `explain*` for Spec Records

* updated deps:

```clj
[org.clojure/clojure "0.1.123"] is available but we use "0.1.108"
[org.clojure/clojure "1.9.0-alpha17"] is available but we use "1.9.0-alpha16"
[org.clojure/clojurescript "1.9.562"] is available but we use "1.9.542"
```

**[compare](https://github.com/metosin/spec-tools/compare/0.2.0...0.2.1)**

## 0.2.0 (2017-05-16)

* **BREAKING**: update spec to `alpha16`:
  * `clojure.spec` => `clojure.spec.alpha`, `cljs.spec` => `cljs.spec.alpha` etc.

* updated deps:

```clj
[org.clojure/spec.alpha "0.1.108"]
[org.clojure/clojure "1.9.0-alpha16"] is available but we use "1.9.0-alpha15"
[org.clojure/clojurescript "1.9.542"] is available but we use "1.9.518"
```

**[compare](https://github.com/metosin/spec-tools/compare/0.1.1...0.2.0)**

## 0.1.1 (2017-05-10)

* Remove hard dependency on ClojureScript, thanks to [Kenny Williams](https://github.com/kennyjwilli). [#52](https://github.com/metosin/spec-tools/pull/52)

**[compare](https://github.com/metosin/spec-tools/compare/0.1.0...0.1.1)**

## 0.1.0 (2017-05-04)

* Initial release.
