# OpenAPI3

```clojure
(require '[spec-tools.openapi.core :as openapi])
```

An utility to transform Specs to OpenApi3 Schemas.

## Spec transformations ##

`openapi/transform` converts specs into OpenAPI3 Schema. The most of the
features are similar to swagger2 spec transformation with some extra
functionality.

```clojure
;; OpenAPI3 support oneOf and null type
(openapi/transform (s/nilable string?))
;;=> {:oneOf [{:type "string"} {:type "null"}]}

;; OpenAPI3 support anyOf
(openapi/transform (s/cat :string string? :int integer?))
;;=> {:type "array", :items {:anyOf [{:type "integer"} {:type "string"}]}}
```

## OpenAPI3 Spec generation ##

`openapi/openapi-spec` function takes an extended OpenAPI3 spec as map and
transforms it into a valid [OpenAPI Object](https://swagger.io/specification/#openapi-object). Rules:
  * by default, data is passed through, allowing any valid OpenAPI3 data to be
    used
  * for qualified map keys, `openapi/expand` multimethod is invoked with the
    key, value and the map as arguments
    * dispatches on the key
    * returns a map that gets merged into original map, without the dispatched
      key

Predefined dispatch keys below.

### `::openapi/parameters` ###

Value should be a map with optional keys `:query`, `:header`, `:path` or
`:cookie`. For all keys value should be a `s/keys` spec (describing the ring
parameters).

Returns a map with key `:parameters` with value of vector of OpenAPI3
[Parameter object](https://swagger.io/specification/#parameter-object), merged
over the existing `:parameters`. Duplicate parameters (with identical `:in` and
`:name`) are overridden.

```clojure
(require '[clojure.spec.alpha :as s])

(s/def ::id int?)
(s/def ::name string?)
(s/def ::street string?)
(s/def ::city (s/nilable #{:tre :hki}))
(s/def ::filters (s/coll-of string? :into []))
(s/def ::address (s/keys :req-un [::street ::city]))
(s/def ::user (s/keys :req-un [::id ::name ::address]))
(s/def ::token string?)

(openapi/openapi-spec
 {:path
  {"/test"
   {:parameters
    [{:name        "username"
      :in          "path"
      :description "username to fetch"
      :required    true
      :schema      {:type "string"}
      :style       "simple"}
     {:name        "name"
      :in          "query"
      :description "This will be overridden"
      :required    true
      :schema      {:type "string"}
      :style       "simple"}]
    ::openapi/parameters
    {:path   (s/keys :req-un [::id])
     :query  (s/keys :req-un [::name] :opt-un [::street ::city ::filters])
     :header ::user}}}})

;; =>
;; {:path
;;  {"/test"
;;   {:parameters
;;    [{:name        "username",
;;      :in          "path",
;;      :description "username to fetch",
;;      :required    true,
;;      :schema      {:type "string"},
;;      :style       "simple"}
;;     {:name        "id",
;;      :in          "path",
;;      :description "",
;;      :required    true,
;;      :schema      {:type "integer", :format "int64"}}
;;     {:name        "name",
;;      :in          "query",
;;      :description "",
;;      :required    true,
;;      :schema      {:type "string"}}
;;     {:name        "street",
;;      :in          "query",
;;      :description "",
;;      :required    false,
;;      :schema      {:type "string"}}
;;     {:name        "city",
;;      :in          "query",
;;      :description "",
;;      :required    false,
;;      :schema      {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}}
;;     {:name        "filters",
;;      :in          "query",
;;      :description "",
;;      :required    false,
;;      :schema      {:type "array", :items {:type "string"}}}
;;     {:name        "id",
;;      :in          "header",
;;      :description "",
;;      :required    true,
;;      :schema      {:type "integer", :format "int64"}}
;;     {:name        "name",
;;      :in          "header",
;;      :description "",
;;      :required    true,
;;      :schema      {:type "string"}}
;;     {:name        "address",
;;      :in          "header",
;;      :description "",
;;      :required    true,
;;      :schema
;;      {:type     "object",
;;       :properties
;;       {"street" {:type "string"},
;;        "city"   {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;       :required ["street" "city"],
;;       :title    "spec-tools.openapi.core-test/address"}}]}}}(require '[clojure.spec.alpha :as s])
```

### `::openapi/schemas` ###

Value should be a map, where key is schema name and value is spec.

Returns a map with schema name as key and [Schema object](https://swagger.io/specification/#schema-object)
as value (should be used inside [Component object](https://swagger.io/specification/#components-object)).
Parameters with duplicated names will be overridden:

```clojure
(openapi/openapi-spec
 {:components
  {:schemas
   {:some-object
    {:type "object"
     :properties
     {"name" {:type "string"}
      "desc" {:type "string"}}}
    :user
    {:type  "string"
     :title "Will be overridden"}}
   ::openapi/schemas
   {:id           ::id
    :user         ::user
    :address      ::address
    :some-request (s/keys :req-un [::id ::name]
                          :opt-un [::street ::filters])}}})
;; =>
;; {:components
;;  {:schemas
;;   {:some-object
;;    {:type       "object",
;;     :properties {"name" {:type "string"}, "desc" {:type "string"}}},
;;    :user
;;    {:type     "object",
;;     :properties
;;     {"id"   {:type "integer", :format "int64"},
;;      "name" {:type "string"},
;;      "address"
;;      {:type     "object",
;;       :properties
;;       {"street" {:type "string"},
;;        "city"   {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;       :required ["street" "city"],
;;       :title    "spec-tools.openapi.core-test/address"}},
;;     :required ["id" "name" "address"],
;;     :title    "spec-tools.openapi.core-test/user"},
;;    :id {:type "integer", :format "int64"},
;;    :address
;;    {:type     "object",
;;     :properties
;;     {"street" {:type "string"},
;;      "city"   {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;     :required ["street" "city"],
;;     :title    "spec-tools.openapi.core-test/address"},
;;    :some-request
;;    {:type     "object",
;;     :properties
;;     {"id"      {:type "integer", :format "int64"},
;;      "name"    {:type "string"},
;;      "street"  {:type "string"},
;;      "filters" {:type "array", :items {:type "string"}}},
;;     :required ["id" "name"]}}}}
```

### `::openapi/content` ###

Value should be a map with content-type string as key and spec as value.

Returns a map with key `:content` and value of map with content-type string as
key and OpenAPI3 [Media type object](https://swagger.io/specification/#media-type-object)
as value. Duplicated content-types will be overridden.

```clojure
(openapi/openapi-spec
 {:content
  {"test/html"
   {:schema
    {:type "string"}}
   "application/json"
   {:schema
    {:type "null"}
    :example "Will be overridden"}}
  ::openapi/content
  {"application/json"
   (st/spec
    {:spec             ::user
     :openapi/example  "Some examples here"
     :openapi/examples {:admin
                        {:summary       "Admin user"
                         :description   "Super user"
                         :value         {:anything :here}
                         :externalValue "External value"}}
     :openapi/encoding {:contentType "application/json"}})
   "application/xml" ::address
   "*/*"             (s/keys :req-un [::id ::name]
                             :opt-un [::street ::filters])}})

;; =>
;; {:content
;;  {"test/html" {:schema {:type "string"}},
;;   "application/json"
;;   {:schema
;;    {:type     "object",
;;     :properties
;;     {"id"   {:type "integer", :format "int64"},
;;      "name" {:type "string"},
;;      "address"
;;      {:type     "object",
;;       :properties
;;       {"street" {:type "string"},
;;        "city"   {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;       :required ["street" "city"],
;;       :title    "spec-tools.openapi.core-test/address"}},
;;     :required ["id" "name" "address"],
;;     :title    "spec-tools.openapi.core-test/user",
;;     :example  "Some examples here",
;;     :examples
;;     {:admin
;;      {:summary       "Admin user",
;;       :description   "Super user",
;;       :value         {:anything :here},
;;       :externalValue "External value"}},
;;     :encoding {:contentType "application/json"}}},
;;   "application/xml"
;;   {:schema
;;    {:type     "object",
;;     :properties
;;     {"street" {:type "string"},
;;      "city"   {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;     :required ["street" "city"],
;;     :title    "spec-tools.openapi.core-test/address"}},
;;   "*/*"
;;   {:schema
;;    {:type     "object",
;;     :properties
;;     {"id"      {:type "integer", :format "int64"},
;;      "name"    {:type "string"},
;;      "street"  {:type "string"},
;;      "filters" {:type "array", :items {:type "string"}}},
;;     :required ["id" "name"]}}}}
```

### `::openapi/headers` ###

Value should be a map where key is a header name and value is clojure spec.

Returns map with key `:headers` and value of map of header name and OpenAPI3
[Header object](https://swagger.io/specification/#header-object) merged over
the existing `:headers`. All duplicated names will be overridden.

```clojure
(openapi/openapi-spec
 {:headers
  {:X-Rate-Limit-Limit
   {:description "The number of allowed requests in the current period"
    :schema      {:type "integer"}}}
  ::openapi/headers
  {:City          ::city
   :Authorization ::token
   :User          ::user}})

;; =>
;; {:headers
;;  {:X-Rate-Limit-Limit
;;   {:description "The number of allowed requests in the current period",
;;    :schema      {:type "integer"}},
;;   :City
;;   {:description "",
;;    :required    false,
;;    :schema      {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;   :Authorization {:description "", :required true, :schema {:type "string"}},
;;   :User
;;   {:description "",
;;    :required    true,
;;    :schema
;;    {:type     "object",
;;     :properties
;;     {"id"   {:type "integer", :format "int64"},
;;      "name" {:type "string"},
;;      "address"
;;      {:type     "object",
;;       :properties
;;       {"street" {:type "string"},
;;        "city"   {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;       :required ["street" "city"],
;;       :title    "spec-tools.openapi.core-test/address"}},
;;     :required ["id" "name" "address"],
;;     :title    "spec-tools.openapi.core-test/user"}}}}
```

### Full example ###

```clojure
(openapi/openapi-spec
 {:openapi "3.0.3"
  :info
  {:title          "Sample Pet Store App"
   :description    "This is a sample server for a pet store."
   :termsOfService "http://example.com/terms/"
   :contact
   {:name  "API Support",
    :url   "http://www.example.com/support"
    :email "support@example.com"}
   :license
   {:name "Apache 2.0",
    :url  "https://www.apache.org/licenses/LICENSE-2.0.html"}
   :version        "1.0.1"}
  :servers
  [{:url         "https://development.gigantic-server.com/v1"
    :description "Development server"}
   {:url         "https://staging.gigantic-server.com/v1"
    :description "Staging server"}
   {:url         "https://api.gigantic-server.com/v1"
    :description "Production server"}]
  :components
  {::openapi/schemas {:user    ::user
                      :address ::address}
   ::openapi/headers {:token ::token}}
  :paths
  {"/api/ping"
   {:get
    {:description "Returns all pets from the system that the user has access to"
     :responses   {200 {::openapi/content
                        {"application/xml" ::user
                         "application/json"
                         (st/spec
                          {:spec             ::address
                           :openapi/example  "Some examples here"
                           :openapi/examples {:admin
                                              {:summary       "Admin user"
                                               :description   "Super user"
                                               :value         {:anything :here}
                                               :externalValue "External value"}}
                           :openapi/encoding {:contentType "application/json"}})}}}}}
   "/user/:id"
   {:post
    {:tags                ["user"]
     :description         "Returns pets based on ID"
     :summary             "Find pets by ID"
     :operationId         "getPetsById"
     :requestBody         {::openapi/content {"application/json" ::user}}
     :responses           {200      {:description "pet response"
                                     ::openapi/content
                                     {"application/json" ::user}}
                           :default {:description "error payload",
                                     ::openapi/content
                                     {"text/html" ::user}}}
     ::openapi/parameters {:path   (s/keys :req-un [::id])
                           :header (s/keys :req-un [::token])}}}}})

;; =>
;; {:openapi "3.0.3",
;;  :info
;;  {:title          "Sample Pet Store App",
;;   :description    "This is a sample server for a pet store.",
;;   :termsOfService "http://example.com/terms/",
;;   :contact
;;   {:name  "API Support",
;;    :url   "http://www.example.com/support",
;;    :email "support@example.com"},
;;   :license
;;   {:name "Apache 2.0",
;;    :url  "https://www.apache.org/licenses/LICENSE-2.0.html"},
;;   :version        "1.0.1"},
;;  :servers
;;  [{:url         "https://development.gigantic-server.com/v1",
;;    :description "Development server"}
;;   {:url         "https://staging.gigantic-server.com/v1",
;;    :description "Staging server"}
;;   {:url         "https://api.gigantic-server.com/v1",
;;    :description "Production server"}],
;;  :components
;;  {:schemas
;;   {:user
;;    {:type     "object",
;;     :properties
;;     {"id"   {:type "integer", :format "int64"},
;;      "name" {:type "string"},
;;      "address"
;;      {:type     "object",
;;       :properties
;;       {"street" {:type "string"},
;;        "city"   {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;       :required ["street" "city"],
;;       :title    "spec-tools.openapi.core-test/address"}},
;;     :required ["id" "name" "address"],
;;     :title    "spec-tools.openapi.core-test/user"},
;;    :address
;;    {:type     "object",
;;     :properties
;;     {"street" {:type "string"},
;;      "city"   {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;     :required ["street" "city"],
;;     :title    "spec-tools.openapi.core-test/address"}},
;;   :headers
;;   {:token {:description "", :required true, :schema {:type "string"}}}},
;;  :paths
;;  {"/api/ping"
;;   {:get
;;    {:description
;;     "Returns all pets from the system that the user has access to",
;;     :responses
;;     {200
;;      {:content
;;       {"application/xml"
;;        {:schema
;;         {:type     "object",
;;          :properties
;;          {"id"   {:type "integer", :format "int64"},
;;           "name" {:type "string"},
;;           "address"
;;           {:type     "object",
;;            :properties
;;            {"street" {:type "string"},
;;             "city"
;;             {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;            :required ["street" "city"],
;;            :title    "spec-tools.openapi.core-test/address"}},
;;          :required ["id" "name" "address"],
;;          :title    "spec-tools.openapi.core-test/user"}},
;;        "application/json"
;;        {:schema
;;         {:type     "object",
;;          :properties
;;          {"street" {:type "string"},
;;           "city"
;;           {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;          :required ["street" "city"],
;;          :title    "spec-tools.openapi.core-test/address",
;;          :example  "Some examples here",
;;          :examples
;;          {:admin
;;           {:summary       "Admin user",
;;            :description   "Super user",
;;            :value         {:anything :here},
;;            :externalValue "External value"}},
;;          :encoding {:contentType "application/json"}}}}}}}},
;;   "/user/:id"
;;   {:post
;;    {:tags        ["user"],
;;     :description "Returns pets based on ID",
;;     :summary     "Find pets by ID",
;;     :operationId "getPetsById",
;;     :requestBody
;;     {:content
;;      {"application/json"
;;       {:schema
;;        {:type     "object",
;;         :properties
;;         {"id"   {:type "integer", :format "int64"},
;;          "name" {:type "string"},
;;          "address"
;;          {:type     "object",
;;           :properties
;;           {"street" {:type "string"},
;;            "city"
;;            {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;           :required ["street" "city"],
;;           :title    "spec-tools.openapi.core-test/address"}},
;;         :required ["id" "name" "address"],
;;         :title    "spec-tools.openapi.core-test/user"}}}},
;;     :responses
;;     {200
;;      {:description "pet response",
;;       :content
;;       {"application/json"
;;        {:schema
;;         {:type     "object",
;;          :properties
;;          {"id"   {:type "integer", :format "int64"},
;;           "name" {:type "string"},
;;           "address"
;;           {:type     "object",
;;            :properties
;;            {"street" {:type "string"},
;;             "city"
;;             {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;            :required ["street" "city"],
;;            :title    "spec-tools.openapi.core-test/address"}},
;;          :required ["id" "name" "address"],
;;          :title    "spec-tools.openapi.core-test/user"}}}},
;;      :default
;;      {:description "error payload",
;;       :content
;;       {"text/html"
;;        {:schema
;;         {:type     "object",
;;          :properties
;;          {"id"   {:type "integer", :format "int64"},
;;           "name" {:type "string"},
;;           "address"
;;           {:type     "object",
;;            :properties
;;            {"street" {:type "string"},
;;             "city"
;;             {:oneOf [{:enum [:tre :hki], :type "string"} {:type "null"}]}},
;;            :required ["street" "city"],
;;            :title    "spec-tools.openapi.core-test/address"}},
;;          :required ["id" "name" "address"],
;;          :title    "spec-tools.openapi.core-test/user"}}}}},
;;     :parameters
;;     [{:name        "id",
;;       :in          "path",
;;       :description "",
;;       :required    true,
;;       :schema      {:type "integer", :format "int64"}}
;;      {:name        "token",
;;       :in          "header",
;;       :description "",
;;       :required    true,
;;       :schema      {:type "string"}}]}}}}
```
