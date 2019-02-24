# Spec Records

Tools for enabling adding meta-data to Specs and 

To enable spec metadata and features like [Spec driven transformations](#spec-driven-transformations), Spec-tools introduces extendable Spec Records, `Spec`s. They wrap specs and act like specs or 1-arity functions. Specs are created with `spec-tools.core/spec` macro or with the underlying `spec-tools.core/create-spec` function.

The following Spec keys having a special meaning:

| Key                | Description                                                                 |
| -------------------|-----------------------------------------------------------------------------|
| `:spec`            | The wrapped spec (predicate).                                               |
| `:form`            | The wrapped spec form.                                                      |
| `:type`            | Type hint of the Spec, mostly auto-resolved. Used in runtime conformation.  |
| `:name`            | Name of the spec. Maps to `title` in JSON Schema.                           |
| `:description`     | Description of the spec. Maps to `description` in JSON Schema.              |
| `:gen`             | Generator function for the Spec (set via `s/with-gen`)                      |
| `::parse/keys`     | Set of all map keys that the spec defines. Extracted from `s/keys` Specs.   |
| `::parse/keys-req` | Set of required map keys that the spec defines. Extracted from `s/keys` Specs.|
| `::parse/keys-opt` | Set of optional map keys that the spec defines. Extracted from `s/keys` Specs.|
| `:reason`          | Value is added to `s/explain-data` problems under key `:reason`             |
| `:decode/...`      | 2-arity function to transform a value from an external format.              |
| `:encode/...`      | 2-arity function to transform a value into external format.                 |
| `:json-schema/...` | Extra data that is merged with unqualifed keys into json-schema             |
| `:swagger/...`     | Extra data that is merged with unqualifed keys into swagger-schema          |

There are also some extra read-only keys from spec parsing, these all are namespaced with `::parse` (`spec-tools.parse`).

## Creating Specs

The following are all equivalent:

```clj
(require '[spec-tools.core :as st])

;; using type inference
(st/spec integer?)

;; with explicit type
(st/spec integer? {:type :long})

;; map form
(st/spec {:spec integer?})
(st/spec {:spec integer?, :type :long})

;; function
(st/create-spec
  {:spec integer?
   :form `integer?
   :type :long})

;; function, with type and form inference
(st/create-spec
  {:spec integer?})

;; ... resulting in:
; #Spec{:type :long,
;       :form clojure.core/integer?}
```

### Example usage

```clj
(require '[clojure.spec.alpha :as s])

(def my-integer? (st/spec integer?))

my-integer?
; #Spec{:type :long
;       :form clojure.core/integer?}

(my-integer? 1)
; true

(s/valid? my-integer? 1)
; true

(assoc my-integer? :description "It's a int")
; #Spec{:type :long
;       :form clojure.core/integer?
;       :description "It's a int"}

(eval (s/form (st/spec integer? {:description "It's a int"})))
; #Spec{:type :long
;       :form clojure.core/integer?
;       :description "It's a int"}
```

For most core predicates, `:type` can be resolved automatically using the `spec-tools.parse/parse-form` multimethod.

For most core predicates, `:form` can be resolved automatically using the `spec-tools.form/resolve-form` multimethod.

## Predefined Spec Records

Most `clojure.core` predicates have a predefined Spec Record instance in `spec-tools.spec`.

```clj
(require '[spec-tools.spec :as spec])

spec/boolean?
; #Spec{:type :boolean
;       :form clojure.core/boolean?}

(spec/boolean? true)
; true

(s/valid? spec/boolean? false)
; true

(assoc spec/boolean? :description "it's an bool")
; #Spec{:type :boolean
;       :form clojure.core/boolean?
;       :description "It's a bool"}
```

## Custom errors

Can be added to a Spec via the key `:reason`

```clj
(s/explain (st/spec pos-int? {:reason "positive"}) -1)
; val: -1 fails predicate: pos-int?,  positive

(s/explain-data (st/spec pos-int? {:reason "positive"}) -1)
; #:clojure.spec.alpha{:problems [{:path [], :pred pos-int?, :val -1, :via [], :in [], :reason "positive"}]}
```
