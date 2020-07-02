# Swagger2

```clj
(require '[spec-tools.swagger.core :as swagger])
```

An utility to transform Specs to Swagger2 Schemas.

## Spec transformations

`swagger/transform` converts specs into Swagger2 Schema. Transformation can be customized with the following optional options:

* `:type` - a target type, either `:parameter` ([Parameter Object](https://swagger.io/specification/v2/#parameterObject)) or `:schema` ([Schema Object](https://swagger.io/specification/v2/#schemaObject)). If value is not defined, `:schema` is used.
* `:in` - a parameter subtype, which is one of: `:query`, `:header`, `:path`, `:body` or `:formData`. See [Parameter Object](https://swagger.io/specification/v2/#parameterObject) for details.

**NOTE**: As `clojure.spec` is more powerful than the Swagger2 Schema, we are losing some data in the transformation. We try to retain all the information using vendor extensions.

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

## Annotated Specs

Specs can be annotated to populate the JSON Schemas.

* `:name` is copied into `:title`
* `:description` is used as-is
* all keys with namespace `json-schema` and `swagger` are used without the namespace

```clj
(require '[spec-tools.core :as st])

(swagger/transform
  (st/spec
    {:spec integer?
     :name "integer"
     :description "it's an int"
     :swagger/example 42
     :json-schema/default 42}))
;{:type "integer"
; :title "integer"
; :description "it's an int"
; :example 42
; :default 42}
```

## Swagger Spec generation

`swagger/swagger-spec` function takes an extended swagger2 spec as map and transforms it into a valid [Swagger Object](https://swagger.io/specification/v2/#swaggerObject). Rules:

* by default, data is passed through, allowing any valid swagger data to be used
* for qualified map keys, `swagger/expand` multimethod is invoked with the key, value and the map as arguments
  * dispatches on the key, defaulting to `::swagger/extension`
  * returns a map that get's merged in to original map, without the dispatched key

Predefined dispatch keys below.

### `::swagger/parameters`

Value should be a map with optional keys `:body`, `:query`, `:path`, `:header` and `:formData`. For all but `:body`, the value should be a `s/keys` spec (describing the ring parameters). With `:body`, the value can be any `clojure.spec.alpha/Spec`.

Returns a map with key `:parameters` with value of vector of swagger [Parameter Objects](https://swagger.io/specification/v2/#parameterObject), merged over the existing `:parameters`. Duplicate parameters (with identical `:in` and `:name` are overridden)

```clj
(require '[clojure.spec.alpha :as s])

(s/def ::id string?)
(s/def ::name string?)
(s/def ::street string?)
(s/def ::city #{:tre :hki})
(s/def ::address (s/keys :req-un [::street ::city]))
(s/def ::user (s/keys :req-un [::id ::name ::address]))

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
;{:paths
; {"echo"
;  {:post
;   {:parameters [{:in "query"
;                  :name "name2"
;                  :type "string"
;                  :required true}
;                 {:in "query"
;                  :name "name"
;                  :description ""
;                  :type "string"
;                  :required false}
;                 {:in "body",
;                  :name "user/user",
;                  :description "",
;                  :required true,
;                  :schema {:type "object",
;                           :properties {"id" {:type "string"},
;                                        "name" {:type "string"},
;                                        "address" {:type "object",
;                                                   :properties {"street" {:type "string"},
;                                                                "city" {:enum [:tre :hki],
;                                                                        :type "string"}},
;                                                   :required ["street" "city"],
;                                                   :title "user/address"}},
;                           :required ["id" "name" "address"],
;                           :title "user/user"}}]}}}}
```

### `::swagger/responses`

Value should a [Swagger2 Responses Definition Object](https://swagger.io/specification/v2/#responsesDefinitionsObject) with Spec or Spec as the `:schema`. Returns a map with key `:responses` with `:schemas` transformed into [Swagger2 Schema Objects](https://swagger.io/specification/#schemaObject), merged over existing `:responses`.

```clj
(swagger/swagger-spec
  {:responses {404 {:description "fail"}
               500 {:description "fail"}}
   ::swagger/responses {200 {:schema ::user
                             :description "Found it!"}
                        404 {:description "Ohnoes."}}})
;{:responses {404 {:description "Ohnoes."}
;             500 {:description "fail"}
;             200 {:schema {:type "object"
;                           :properties {"id" {:type "string"}
;                                        "name" {:type "string"}
;                                        "address" {:type "object"
;                                                   :properties {"street" {:type "string"}
;                                                                "city" {:type "string"
;                                                                        :enum [:tre :hki]}}
;                                                   :required ["street" "city"]
;                                                   :title "user/address"}}
;                           :required ["id" "name" "address"]
;                           :title "user/user"}
;                  :description "Found it!"}}}
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
                               ::swagger/parameters {:path (s/keys :req [::id])
                                                     :body ::user}
                               ::swagger/responses {200 {:schema ::user
                                                         :description "Found it!"}
                                                    404 {:description "Ohnoes."}}}}}})
;{:swagger "2.0"
; :info {:version "1.0.0"
;        :title "Sausages"
;        :description "Sausage description"
;        :termsOfService "http://helloreverb.com/terms/"
;        :contact {:name "My API Team" :email "foo@example.com" :url "http://www.metosin.fi"}
;        :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}}
; :tags [{:name "user" :description "User stuff"}]
; :paths {"/api/ping" {:get {:responses {:default {:description ""}}}}
;         "/user/:id" {:post
;                      {:summary "User Api"
;                       :description "User Api description"
;                       :tags ["user"]
;                       :parameters [{:in "path"
;                                     :name "user/id"
;                                     :description ""
;                                     :type "string"
;                                     :required true}
;                                    {:in "body"
;                                     :name "user/user"
;                                     :description ""
;                                     :required true
;                                     :schema {:type "object"
;                                              :properties {"id" {:type "string"}
;                                                           "name" {:type "string"}
;                                                           "address" {:type "object"
;                                                                      :properties {"street" {:type "string"}
;                                                                                   "city" {:type "string"
;                                                                                           :enum [:tre :hki]}}
;                                                                      :required ["street" "city"]
;                                                                      :title "user/address"}}
;                                              :required ["id" "name" "address"]
;                                              :title "user/user"}}]
;                       :responses {200 {:schema {:type "object"
;                                                 :properties {"id" {:type "string"}
;                                                              "name" {:type "string"}
;                                                              "address" {:type "object"
;                                                                         :properties {"street" {:type "string"}
;                                                                                      "city" {:enum [:tre :hki]
;                                                                                              :type "string"}}
;                                                                         :required ["street" "city"]
;                                                                         :title "user/address"}}
;                                                 :required ["id" "name" "address"]
;                                                 :title "user/user"}
;                                        :description "Found it!"}
;                                   404 {:description "Ohnoes."}}}}}}
```
