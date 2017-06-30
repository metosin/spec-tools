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
