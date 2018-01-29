# spec-tools [![Build Status](https://travis-ci.org/metosin/spec-tools.svg?branch=master)](https://travis-ci.org/metosin/spec-tools)

Clojure(Script) tools for [clojure.spec](http://clojure.org/about/spec). Like [Schema-tools](https://github.com/metosin/schema-tools) but for spec.

* [Spec Records](#spec-records)
* [Dynamic Conforming](#dynamic-conforming)
* [Data Specs](#data-specs)
* [Spec Visitors](#spec-visitors)
* [Generating JSON Schemas](#generating-json-schemas)
* [Swagger2 Integration](#swagger2-integration)

Status: **Alpha** (as spec is still alpha too).

Blogs:
* [Clojure.spec with Ring (& Swagger)](http://www.metosin.fi/blog/clojure-spec-with-ring-and-swagger/)
* [Clojure.spec as a Runtime Transformation Engine](http://www.metosin.fi/blog/clojure-spec-as-a-runtime-transformation-engine/)
* [Schema & Clojure Spec for the Web Developer](http://www.metosin.fi/blog/schema-spec-web-devs/)

## Latest version

[![Clojars Project](http://clojars.org/metosin/spec-tools/latest-version.svg)](http://clojars.org/metosin/spec-tools)

Requires Java 1.8 & Clojure `1.9.0` and/or ClojureScript `1.9.908`+.

## Spec Records

To support spec metadata and extensions like [dynamic conforming](#dynamic-conforming), Spec-tools introduces extendable Spec Records, `Spec`s. They wrap specs and act like specs or 1-arity functions. Specs are created with `spec-tools.core/spec` macro of with the underlying `spec-tools.core/create-spec` function.

The following Spec keys having a special meaning:

| Key                | Description                                                                 |
| -------------------|-----------------------------------------------------------------------------|
| `:spec`            | The wrapped spec (predicate).                                               |
| `:form`            | The wrapped spec form.                                                      |
| `:type`            | Type hint of the Spec, mostly auto-resolved. Used in runtime conformation.  |
| `:name`            | Name of the spec. Maps to `title` in JSON Schema.                           |
| `:description`     | Description of the spec. Maps to `description` in JSON Schema.              |
| `:gen`             | Generator function for the Spec (set via `s/with-gen`)                      |
| `:keys`            | Set of all map keys that the spec defines. Extracted from `s/keys` Specs.   |
| `:keys/req`        | Set of required map keys that the spec defines. Extracted from `s/keys` Specs.|
| `:keys/opt`        | Set of optional map keys that the spec defines. Extracted from `s/keys` Specs.|
| `:reason`          | Value is added to `s/explain-data` problems under key `:reason`             |
| `:json-schema/...` | Extra data that is merged with unqualifed keys into json-schema             |

### Creating Specs

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

#### Example usage

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

### Predefined Spec Records

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

### Custom errors

Can be added to a Spec via the key `:reason`

```clj
(s/explain (st/spec pos-int? {:reason "positive"}) -1)
; val: -1 fails predicate: pos-int?,  positive

(s/explain-data (st/spec pos-int? {:reason "positive"}) -1)
; #:clojure.spec.alpha{:problems [{:path [], :pred pos-int?, :val -1, :via [], :in [], :reason "positive"}]}
```

## Dynamic conforming

Spec-tools loans from the awesome [Schema](https://github.com/plumatic/schema) by separating specs (what) from conformers (how). Spec Record has a dynamical conformer, which can be instructed at runtime to use a suitable conforming function for that spec. This enables same Specs to conform differently in different runtime conditions, e.g. when reading data from JSON vs Transit.

Spec Record conform is by default a no-op. Binding a dynamic var `spec-tools.core/*conforming*` with a function of `spec => spec-conformer` will cause the Spec to be conformed with the selected spec-conformer. `spec-tools.core` has helper functions for setting the binding: `explain`, `explain-data`, `conform` and `conform!`.

Spec-conformers are arity2 functions taking the Spec Records and the value and should return either conformed value or `:clojure.spec.alpha/invalid`.

There is a [CLJ-2116](https://dev.clojure.org/jira/browse/CLJ-2116) to provide better support with `clojure.spec`.

### Type based conforming

A common way to do dynamic conforming is to select conformer based on the spec's `:type`. By default, the following types are supported (and mostly, auto-resolved): `:long`, `:double`, `:boolean`, `:string`, `:keyword`, `:symbol`, `:uuid`, `:uri`, `:bigdec`, `:date`, `:ratio`, `:map`, `:set` and `:vector`.

The following type-based conforming are found in `spec-tools.core`:

| Name                            | Description                                                                                                              |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `string-conforming`             | Conforms all specs from strings (things like `:query`, `:header` & `:path` -parameters).                                 |
| `json-conforming`               | [JSON](http://json.org/) Conforming (numbers and booleans not conformed).                                                |
| `strip-extra-keys-conforming`   | Strips out extra keys of `s/keys` Specs.                                                                                 |
| `fail-on-extra-keys-conforming` | Fails if `s/keys` Specs have extra keys.                                                                                 |
| `nil`                           | No extra conforming ([EDN](https://github.com/edn-format/edn) & [Transit](https://github.com/cognitect/transit-format)). |

### Conforming examples

```clj
(s/def ::age (s/and spec/integer? #(> % 18)))

;; no conforming
(s/conform ::age "20")
(st/conform ::age "20")
(st/conform ::age "20" nil)
; ::s/invalid

;; json-conforming
(st/conform ::age "20" st/json-conforming)
; ::s/invalid

;; string-conforming
(st/conform ::age "20" st/string-conforming)
; 20
```

### More complex example

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

;; no conforming
(st/conform ::user data)
; ::s/invalid

;; json-conforming doesn't conform numbers
(st/conform ::user data st/json-conforming)
; ::s/invalid

;; string-conforming for the rescue
(st/conform ::user data st/string-conforming)
; {:name "Ilona"
;  :age 48
;  :languages #{:clj :cljs}
;  :birthdate #inst"1968-01-02T15:04:05.000-00:00"}
```

### Map Conforming

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

(st/conform
  ::user
  inkeri
  st/strip-extra-keys-conforming)
; {:name "Inkeri"
;  :address {:street "Satamakatu"}}
```

Inspired by the [Schema-tools](https://github.com/metosin/schema-tools), there are also a `select-spec` to achieve the same:

```clj
(st/select-spec ::user inkeri)
; {:name "Inkeri"
;  :address {:street "Satamakatu"}}
```

### Custom conforming

```clj
(require '[clojure.string :as str])

(def my-string-conforming
  (st/type-conforming
    (assoc
      conform/string-type-conforming
      :keyword
      (fn [_ value]
        (-> value
            str/upper-case
            str/reverse
            keyword))))

(st/conform spec/keyword? "kikka")
; ::s/invalid

(st/conform spec/keyword? "kikka" st/string-conforming)
; :kikka

(st/conform spec/keyword? "kikka" my-string-conforming)
; :AKKIK
```

### Data Macros

* see http://www.metosin.fi/blog/clojure-spec-as-a-runtime-transformation-engine/#data-macros

### Composing conforming

Type-based conforming mappings are defined as data, so they are easy to combine and extend:

```clj
(require '[spec-tools.conform :as conform])

(def strict-json-conforming
  (st/type-conforming
    (merge
      conform/json-type-conforming
      conform/strip-extra-keys-type-conforming)))
```

## Data Specs

```clj
(require '[spec-tools.data-spec :as ds])
```

Data Specs offers an alternative, Schema-like data-driven syntax to define simple nested collection specs. Rules:

* Just data, no macros
* Can be transformed into vanilla specs with valid forms (via form inference)
* Supports nested Maps `{}`, Vectors `[]` and Sets `#{}`
  * Vectors and Sets are homogeneous, and must contains exactly one spec
* Maps have either a single spec key (homogeneous keys) or any number keyword keys.
  * With homogeneous keys, keys are also conformed
  * Map (keyword) keys
    * can be qualified or non-qualified (a qualified name will be generated for it)
    * are required by default
    * can be wrapped into `ds/opt` or `ds/req` for making them optional or required.
  * Map values
    * can be functions, specs, qualified spec names or nested collections.
    * wrapping value into `ds/maybe` makes it `s/nillable`

**NOTE**: to avoid macros, current implementation uses the undocumented functional core of `clojure.spec.alpha`: `every-impl`, `tuple-impl`, `map-spec-impl` & `nilable-impl`.

**NOTE**: To use enums with data-specs, you need to wrap them: `(s/spec #{:S :M :L})`

```clj
(s/def ::age spec/pos-int?)

;; a data-spec
(def person
  {::id integer?
   ::age ::age
   :boss boolean?
   :aliases [(ds/or {::maps {:alias string?}
                     ::strings string?})]
   (ds/req :name) string?
   (ds/opt :description) string?
   :languages #{keyword?}
   :orders [{:id int?
             :description string?}]
   :address (ds/maybe
              {:street string?
               :zip string?})})

;; it's just data.
(def new-person
  (dissoc person ::id))
```

* to turn a data-spec into a Spec, call `ds/spec` on it, providing a qualified keyword describing the root spec name - used to generate unique names for sub-specs that will be registered.

```clj
;; transform into specs
(def person-spec
  (ds/spec ::person person))

(def new-person-spec
  (ds/spec ::person new-person))
```

* the following specs are now registered:

```clj
(keys (st/registry #"user.*"))
; (:user/id
;  :user/age
;  :user$person/boss
;  :user$person/name
;  :user$person/description
;  :user$person/languages
;  :user$person/orders
;  :user$person$orders/description
;  :user$person$orders/id
;  :user$person/address
;  :user$person$address/street
;  :user$person$address/zip)
```

* and now we have specs:

```clj
(s/valid?
  new-person-spec
  {::age 63
   :boss true
   :name "Liisa"
   :languages #{:clj :cljs}
   :orders [{:id 1, :description "cola"}
            {:id 2, :description "kebab"}]
   :description "Liisa is a valid boss"
   :address {:street "Amurinkatu 2"
             :zip "33210"}})
; true
```

* all generated specs are wrapped into Specs Records so dynamic conforming works out of the box:

```clj
(st/conform!
  new-person-spec
  {::age "63"
   :boss "true"
   :name "Liisa"
   :languages ["clj" "cljs"]
   :orders [{:id "1", :description "cola"}
            {:id "2", :description "kebab"}]
   :description "Liisa is a valid boss"
   :address nil}
  st/string-conforming)
; {::age 63
;  :boss true
;  :name "Liisa"
;  :languages #{:clj :cljs}
;  :orders [{:id 1, :description "cola"}
;           {:id 2, :description "kebab"}]
;  :description "Liisa is a valid boss"
;  :address nil}
```

## Spec Visitors

A tool to walk over and transform specs using the [Visitor-pattern](https://en.wikipedia.org/wiki/Visitor_pattern). Main entry point is the `spec-tools.visitor/visit` function, extendable via `spec-tools.visitor/visit-spec` multimethod. There is an example implementation for recursively collecting nested specs. Also, the [Spec to JSON Schema -converter](#generating-json-schemas) is implemented using the visitor.

```clj
(require '[spec-tools.visitor :as visitor])

;; visitor to recursively collect all registered spec forms
(let [specs (atom {})]
  (visitor/visit
    person-spec
    (fn [_ spec _ _]
      (if-let [s (s/get-spec spec)]
        (swap! specs assoc spec (s/form s))
        @specs))))

; {:user/id (spec-tools.core/spec
;             {:spec clojure.core/integer?
;              :type :long})
;  :user/age (spec-tools.core/spec
;              {:spec clojure.core/pos-int?
;               :type :long})
;  :user$person/boss (spec-tools.core/spec
;                      {:spec clojure.core/boolean?
;                       :type :boolean})
;  :user$person/name (spec-tools.core/spec
;                      {:spec clojure.core/string?
;                       :type :string})
;  :user$person/languages (spec-tools.core/spec
;                           {:spec (clojure.spec.alpha/coll-of
;                                    (spec-tools.core/spec
;                                      {:spec clojure.core/keyword?
;                                       :type :keyword})
;                                    :into #{})
;                            :type :set})
;  :user$person$orders/id (spec-tools.core/spec
;                           {:spec clojure.core/int?
;                            :type :long})
;  :user$person$orders/description (spec-tools.core/spec
;                                    {:spec clojure.core/string?
;                                     :type :string})
;  :user$person/orders (spec-tools.core/spec
;                        {:spec (clojure.spec.alpha/coll-of
;                                 (spec-tools.core/spec
;                                   {:spec (clojure.spec.alpha/keys
;                                            :req-un [:user$person$orders/id :user$person$orders/description])
;                                    :type :map
;                                    :keys #{:id :description}
;                                    :keys/req #{:id :description}})
;                                 :into [])
;                         :type :vector})
;  :user$person$address/street (spec-tools.core/spec
;                                {:spec clojure.core/string?
;                                 :type :string})
;  :user$person$address/zip (spec-tools.core/spec
;                             {:spec clojure.core/string?
;                              :type :string})
;  :user$person/address (spec-tools.core/spec
;                         {:spec (clojure.spec.alpha/nilable
;                                  (spec-tools.core/spec
;                                    {:spec (clojure.spec.alpha/keys
;                                             :req-un [:user$person$address/street :user$person$address/zip])
;                                     :type :map
;                                     :keys #{:street :zip}
;                                     :keys/req #{:street :zip}}))
;                          :type nil})
;  :user$person/description (spec-tools.core/spec
;                             {:spec clojure.core/string?
;                              :type :string})}
```

**NOTE**: due to [CLJ-2152](http://dev.clojure.org/jira/browse/CLJ-2152), `s/&` & `s/keys*` can't be visited.

## Generating JSON Schemas

Generating JSON Schemas from arbitrary specs (and Spec Records).

```clj
(require '[spec-tools.json-schema :as jsc])

(jsc/transform person-spec)
; {:type "object"
;  :properties {"user/id" {:type "integer"}
;               "user/age" {:type "integer", :format "int64", :minimum 1}
;               "boss" {:type "boolean"}
;               "name" {:type "string"}
;               "languages" {:type "array", :items {:type "string"}, :uniqueItems true}
;               "orders" {:type "array"
;                         :items {:type "object"
;                                 :properties {"id" {:type "integer", :format "int64"}
;                                              "description" {:type "string"}}
;                                 :required ["id" "description"]}}
;               "address" {:oneOf [{:type "object"
;                                   :properties {"street" {:type "string"}
;                                                "zip" {:type "string"}}
;                                   :required ["street" "zip"]}
;                                  {:type "null"}]}
;               "description" {:type "string"}}
;  :required ["user/id" "user/age" "boss" "name" "languages" "orders" "address"]}
```

Extra data from Spec records is used to populate the data:

```clj
(jsc/transform
  (st/spec
    {:spec integer?
     :name "integer"
     :description "it's an int"
     :json-schema/default 42}))
; {:type "integer"
;  :title "integer"
;  :description "it's an int"
;  :default 42}
```

## Swagger2 Integration

A converter from Specs to Swagger2 (JSON) Schemas. Can be used as standalone but will be later available as [ring-swagger](https://clojars.org/metosin/ring-swagger) module. See https://github.com/metosin/ring-swagger/issues/95.

```clj
(require '[spec-tools.swagger.core :as swagger])
```

## Spec transformations

`swagger/transform` converts specs into Swagger2 JSON Schema. Transformation can be customized with the following optional options:

  * `:type` - a target type, either `:parameter` ([Parameter Object](http://swagger.io/specification/#parameterObject)) or `:schema` ([Schema Object](http://swagger.io/specification/#schemaObject)). If value is not defined, `:schema` is assumed.
  * `:in` - a parameter subtype, which is one of: `:query`, `:header`, `:path`, `:body` or `:formData`. See [Parameter Object](http://swagger.io/specification/#parameterObject) for details.

**NOTE**: As `clojure.spec` is more powerful than the Swagger2 JSON Schema, we are losing some data in the transformation. We try to retain all the informatin, via vendor extensions.

```clj
(swagger/transform float?)
; {:type "number" :format "float"}

;; no "null" in swagger2
(swagger/transform (s/nilable string?))
; {:type "string", :x-nullable true}

;; swagger2 parameter syntax
(swagger/transform (s/nilable string?) {:type :parameter})
; {:type "string", :allowEmptyValue true}

;; no "anyOf" in swagger2
(swagger/transform (s/cat :int integer? :string string?))
; {:type "array"
;  :items {:type "integer"
;          :x-anyOf [{:type "integer"}
;                    {:type "string"}]}}
```

### Swagger Spec generation

`swagger/swagger-spec` function takes an extended swagger2 spec as map and transforms it into a valid [Swagger Object](http://swagger.io/specification/#swaggerObject). Rules:

* by default, data is passed through, allowing any valid swagger data to be used
* for qualified map keys, `swagger/expand` multimethod is invoked with the key, value and the map as arguments
  * dispatches on the key, defaulting to `::swagger/extension`
  * returns a map that get's merged in to original map, without the dispatched key

Predifined dispatch keys below.

#### `::swagger/extension`

Transforms the the key into valid [swagger vendor extension](http://swagger.io/specification/#vendorExtensions) by prepending a `x-` to it's namespace. Value is not touched.

```clj
(swagger/swagger-spec
  {:my/thing 42})
; {:x-my/thing 42}
```

#### `::swagger/parameters`

Value should be a map with optional keys `:body`, `:query`, `:path`, `:header` and `:formData`. For all but `:body`, the value should be a `s/keys` spec (describing the ring parameters). With `:body`, the value can be any `clojure.spec.alpha/Spec`.

Returns a map with key `:parameters` with value of vector of swagger [Parameter Objects](http://swagger.io/specification/#parameterObject), merged over the existing `:parameters`. Duplicate parameters (with identical `:in` and `:name` are overridden)

```clj
(swagger/swagger-spec
  {:paths
   {"echo"
    {:post
     {:parameters
      [;; existing parameter, will be overriddden
       {:in "query"
        :name "name"
        :required false}
       ;; unique parameter, will remain
       {:in "query"
        :name "name2"
        :type "string"
        :required true}]
      ;; the spec-parameters
      ::swagger/parameters
      {:query (s/keys :opt-un [::name])
       :body ::user}}}}})
; {:paths
;  {"echo"
;   {:post
;    {:parameters
;     [{:in "query"
;       :name "name2"
;       :description "merged"
;       :type "string"
;       :required true}
;      {:in "query"
;       :name ""
;       :description ""
;       :type "string"
;       :required false}
;      {:in "body"
;       :name ""
;       :description ""
;       :required true
;       :schema {:type "object"
;                :title "user/user"
;                :properties {"name" {:type "string"}}
;                :required ["name"]}}]}}}}
```

#### `::swagger/responses`

Value should a [Swagger2 Responses Definition Object](https://swagger.io/specification/#responsesDefinitionsObject) with Spec or Spec as the `:schema`. Returns a map with key `:responses` with `:schemas` transformed into [Swagger2 Schema Objects](https://swagger.io/specification/#schemaObject), merged over existing `:responses`.


```clj
(s/def ::name string?)
(s/def ::user (s/keys :req-un [::name]))

(swagger/swagger-spec
  {:responses {404 {:description "fail"}
               500 {:description "fail"}}
   ::swagger/responses
   {200 {:schema ::user
         :description "Found it!"}
    404 {:description "Ohnoes."}}})
; {:responses
;  {200 {:schema
;        {:type "object",
;         :properties {"name" {:type "string"}},
;         :required ["name"],
;         :title "user/user"},
;        :description "Found it!"}
;   404 {:description "Ohnoes."
;        :schema {}},
;   500 {:description "fail"}}}
```

### Full example

```clj
(require '[spec-tools.swagger.core :as swagger])
(require '[clojure.spec.alpha :as s])

(s/def ::id string?)
(s/def ::name string?)
(s/def ::street string?)
(s/def ::city #{:tre :hki})
(s/def ::address (s/keys :req-un [::street ::city]))
(s/def ::user (s/keys :req-un [::id ::name ::address]))

(swagger/swagger-spec
  {:swagger "2.0"
   :info {:version "1.0.0"
          :title "Sausages"
          :description "Sausage description"
          :termsOfService "http://helloreverb.com/terms/"
          :contact {:name "My API Team"
                    :email "foo@example.com"
                    :url "http://www.metosin.fi"}
          :license {:name "Eclipse Public License"
                    :url "http://www.eclipse.org/legal/epl-v10.html"}}
   :tags [{:name "user"
           :description "User stuff"}]
   :paths {"/api/ping" {:get {:responses {:default {:description ""}}}}
           "/user/:id" {:post {:summary "User Api"
                               :description "User Api description"
                               :tags ["user"]
                               ::kikka 42
                               ::swagger/parameters {:path (s/keys :req [::id])
                                                     :body ::user}
                               :responses {200 {::swagger/spec ::user
                                                :description "Found it!"}
                                           404 {:description "Ohnoes."}}}}}})
; {:swagger "2.0",
;  :info {:version "1.0.0",
;         :title "Sausages",
;         :description "Sausage description",
;         :termsOfService "http://helloreverb.com/terms/",
;         :contact {:name "My API Team", :email "foo@example.com", :url "http://www.metosin.fi"},
;         :license {:name "Eclipse Public License", :url "http://www.eclipse.org/legal/epl-v10.html"}},
;  :tags [{:name "user", :description "User stuff"}],
;  :paths {"/api/ping" {:get {:responses {:default {:description ""}}}},
;          "/user/:id" {:post {:summary "User Api",
;                              :description "User Api description",
;                              :tags ["user"],
;                              :responses {200 {:description "Found it!",
;                                               :schema {:type "object",
;                                                        :properties {"id" {:type "string"},
;                                                                     "name" {:type "string"},
;                                                                     "address" {:type "object",
;                                                                                :properties {"street" {:type "string"},
;                                                                                             "city" {:enum [:tre :hki]}},
;                                                                                :required ["street" "city"]}},
;                                                        :required ["id" "name" "address"]}},
;                                          404 {:description "Ohnoes."}},
;                              :x-spec-tools.swagger.core-test/kikka 42,
;                              :parameters [{:in "path", :name "", :description "", :type "string", :required true}
;                                           {:in "body",
;                                            :name "",
;                                            :description "",
;                                            :required true,
;                                            :schema {:type "object",
;                                                     :properties {"id" {:type "string"},
;                                                                  "name" {:type "string"},
;                                                                  "address" {:type "object",
;                                                                             :properties {"street" {:type "string"},
;                                                                                          "city" {:enum [:tre :hki]}},
;                                                                             :required ["street" "city"]}},
;                                                     :required ["id" "name" "address"]}}]}}}}
```

## OpenAPI3 Integration

**TODO**

## License

Copyright © 2016-2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
