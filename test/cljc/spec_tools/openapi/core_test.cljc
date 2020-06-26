(ns spec-tools.openapi.core-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [spec-tools.core :as st]
            [spec-tools.openapi.core :as openapi]
            [spec-tools.spec :as spec]))

(s/def ::integer-spec integer?)
(s/def ::string-spec string?)
(s/def ::nilable-spec (s/nilable string?))
(s/def ::keys-spec (s/keys :req-un [::integer-spec]))
(s/def ::spec (st/spec
               {:spec                string?
                :description         "Spec description"
                :name                "spec-name"
                :json-schema/default "123"
                :json-schema/example "json-schema-exapmle"
                :swagger/example     "swagger-example"
                :openapi/example     "openapi3-example"}))
(s/def ::keys-spec2 (s/keys :req-un [::integer-spec ::spec]))

(def expectations
  "Pairs of spec and expected transform result."
  {int?
   {:type "integer" :format "int64"}

   integer?
   {:type "integer"}

   float?
   {:type "number" :format "float"}

   double?
   {:type "number" :format "double"}

   string?
   {:type "string"}

   boolean?
   {:type "boolean"}

   nil?
   {:type "null"}

   #{1 2 3}
   {:enum [1 3 2] :type "string"}

   (s/int-in 1 10)
   {:allOf [{:type "integer" :format "int64"}
            {:minimum 1 :maximum 10}]}

   (s/keys :req-un [::integer-spec] :opt-un [::string-spec])
   {:type       "object"
    :properties {"integer-spec" {:type "integer"}
                 "string-spec"  {:type "string"}}
    :required   ["integer-spec"]}

   ::keys-spec
   {:type       "object"
    :properties {"integer-spec" {:type "integer"}}
    :required   ["integer-spec"]
    :title      "spec-tools.openapi.core-test/keys-spec"}

   (s/and int? pos?)
   {:allOf [{:type "integer" :format "int64"}
            {:minimum 0 :exclusiveMinimum true}]}

   (s/and spec/int?)
   {:type "integer" :format "int64"}

   (s/or :int int? :string string?)
   {:anyOf [{:type "integer" :format "int64"}
            {:type "string"}]}

   (s/merge (s/keys :req-un [::integer-spec])
            (s/keys :req-un [::string-spec]))
   {:type       "object"
    :properties {"integer-spec" {:type "integer"}
                 "string-spec"  {:type "string"}}
    :required   ["integer-spec" "string-spec"]}

   sequential?
   {:type "array" :items {}}

   (s/every integer?)
   {:type "array" :items {:type "integer"}}

   (s/every-kv string? integer?)
   {:type                 "object"
    :additionalProperties {:type "integer"}}

   (s/coll-of string?)
   {:type  "array"
    :items {:type "string"}}

   (s/coll-of string? :into '())
   {:type  "array"
    :items {:type "string"}}

   (s/coll-of string? :into [])
   {:type  "array"
    :items {:type "string"}}

   (s/coll-of string? :into #{})
   {:type        "array"
    :items       {:type "string"}
    :uniqueItems true}

   (s/map-of string? integer?)
   {:type                 "object"
    :additionalProperties {:type "integer"}}

   (s/* integer?)
   {:type  "array"
    :items {:type "integer"}}

   (s/+ integer?)
   {:type     "array"
    :items    {:type "integer"}
    :minItems 1}

   (s/? integer?)
   {:type     "array"
    :items    {:type "integer"}
    :minItems 0}

   (s/alt :int integer? :string string?)
   {:type  "array"
    :items {:oneOf [{:type "integer"}
                    {:type "string"}]}}

   (s/cat :int integer? :string string?)
   {:type  "array"
    :items {:anyOf [{:type "integer"}
                    {:type "string"}]}}

   (s/tuple integer? string?)
   {:type  "array"
    :items {:anyOf [{:type "integer"}
                    {:type "string"}]}}

   (s/map-of string? clojure.core/integer?)
   {:type                 "object"
    :additionalProperties {:type "integer"}}

   (s/nilable string?)
   {:oneOf [{:type "string"}
            {:type "null"}]}

   ::spec
   {:type        "string"
    :description "Spec description"
    :title       "spec-tools.openapi.core-test/spec"
    :example     "openapi3-example",
    :default     "123"}})

(deftest transform-test
  (doseq [[spec openapi-spec] expectations]
    (testing "transform"
      (is (= openapi-spec (openapi/transform spec))))))

(s/def ::id int?)
(s/def ::name string?)
(s/def ::street string?)
(s/def ::city (s/nilable #{:tre :hki}))
(s/def ::filters (s/coll-of string? :into []))
(s/def ::address (s/keys :req-un [::street ::city]))
(s/def ::user (s/keys :req-un [::id ::name ::address]))
(s/def ::token string?)

(deftest expand-test
  (testing "::parameters"
    (is (= {:parameters
            [{:name        "username"
              :in          "path"
              :description "username to fetch"
              :required    true
              :schema      {:type "string"}
              :style       "simple"}
             {:name        "id"
              :in          "path"
              :description ""
              :required    true
              :schema      {:type   "integer"
                            :format "int64"}}
             {:name        "name"
              :in          "query"
              :description ""
              :required    true
              :schema      {:type "string"}}
             {:name        "street"
              :in          "query"
              :description ""
              :required    false
              :schema      {:type "string"}}
             {:name        "city"
              :in          "query"
              :description ""
              :required    false
              :schema      {:oneOf [{:enum [:tre :hki], :type "string"}
                                    {:type "null"}]}}
             {:name        "filters"
              :in          "query"
              :description ""
              :required    false
              :schema      {:type  "array"
                            :items {:type "string"}}}
             {:name        "id"
              :in          "header"
              :description ""
              :required    true
              :schema      {:type   "integer"
                            :format "int64"}}
             {:name        "name"
              :in          "header"
              :description ""
              :required    true
              :schema      {:type "string"}}
             {:name        "address"
              :in          "header"
              :description ""
              :required    true
              :schema
              {:type     "object"
               :properties
               {"street" {:type "string"}
                "city"   {:oneOf [{:enum [:tre :hki] :type "string"}
                                  {:type "null"}]}}
               :required ["street" "city"]
               :title    "spec-tools.openapi.core-test/address"}}]}
           (openapi/openapi-spec
            {:parameters
             [{:name        "username"
               :in          "path"
               :description "username to fetch"
               :required    true
               :schema      {:type "string"}
               :style       "simple"}]
             ::openapi/parameters
             {:path   (s/keys :req-un [::id])
              :query  (s/keys :req-un [::name] :opt-un [::street ::city ::filters])
              :header ::user}})))
    (is (= {:parameters
            [{:name        "name2"
              :in          "query"
              :description "Will be the same"
              :required    true
              :schema      {:type "string"}}
             {:name        "spec-tools.openapi.core-test/id"
              :in          "path"
              :description ""
              :required    true
              :schema      {:type "integer" :format "int64"}}
             {:name        "city"
              :in          "query"
              :description ""
              :required    true
              :schema
              {:oneOf [{:enum [:tre :hki] :type "string"} {:type "null"}]}}
             {:name        "name"
              :in          "query"
              :description ""
              :required    false
              :schema      {:type "string"}}
             {:name        "street"
              :in          "query"
              :description ""
              :required    false
              :schema      {:type "string"}}
             {:name        "filters"
              :in          "query"
              :description ""
              :required    false
              :schema      {:type "array" :items {:type "string"}}}
             {:name        "street"
              :in          "cookie"
              :description ""
              :required    true
              :schema      {:type "string"}}
             {:name        "city"
              :in          "cookie"
              :description ""
              :required    true
              :schema
              {:oneOf [{:enum [:tre :hki] :type "string"} {:type "null"}]}}]}
           (openapi/openapi-spec
            {:parameters
             [{:name        "name"
               :in          "query"
               :description "Will be overridden"
               :required    false
               :schema      {:type "string"}}
              {:name        "name2"
               :in          "query"
               :description "Will be the same"
               :required    true
               :schema      {:type "string"}}]
             ::openapi/parameters
             {:path   (st/create-spec
                       {:spec
                        (s/keys :req [::id])})
              :query  (st/create-spec
                       {:spec
                        (s/keys :req-un [::city]
                                :opt-un [::name ::street ::filters])})
              :cookie (st/create-spec
                       {:spec
                        ::address})}}))))

  (testing "::schemas"
    (is (= {:components
            {:schemas
             {:some-object
              {:type "object"
               :properties
               {"name" {:type "string"}
                "desc" {:type "string"}}}
              :id {:type "integer" :format "int64"}
              :user
              {:type     "object"
               :properties
               {"id"      {:type "integer" :format "int64"},
                "name"    {:type "string"}
                "address" {:type     "object"
                           :properties
                           {"street" {:type "string"},
                            "city"   {:oneOf [{:enum [:tre :hki] :type "string"}
                                              {:type "null"}]}}
                           :required ["street" "city"]
                           :title    "spec-tools.openapi.core-test/address"}}
               :required ["id" "name" "address"]
               :title    "spec-tools.openapi.core-test/user"}
              :address
              {:type     "object"
               :properties
               {"street" {:type "string"}
                "city"   {:oneOf [{:enum [:tre :hki] :type "string"}
                                  {:type "null"}]}}
               :required ["street" "city"]
               :title    "spec-tools.openapi.core-test/address"}
              :some-request
              {:type     "object"
               :properties
               {"id"      {:type "integer" :format "int64"}
                "name"    {:type "string"}
                "street"  {:type "string"}
                "filters" {:type "array" :items {:type "string"}}}
               :required ["id" "name"]}}}}
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
                                     :opt-un [::street ::filters])}}}))))

  (testing "::content"
    (is (= {:content
            {"test/html"
             {:schema
              {:type "string"}}
             "application/json"
             {:schema
              {:type     "object"
               :properties
               {"id"   {:type "integer" :format "int64"}
                "name" {:type "string"}
                "address"
                {:type     "object"
                 :properties
                 {"street" {:type "string"}
                  "city"
                  {:oneOf [{:enum [:tre :hki] :type "string"}
                           {:type "null"}]}}
                 :required ["street" "city"]
                 :title    "spec-tools.openapi.core-test/address"}}
               :required ["id" "name" "address"]
               :title    "spec-tools.openapi.core-test/user"}}
             "application/xml"
             {:schema
              {:type     "object"
               :properties
               {"street" {:type "string"}
                "city"
                {:oneOf [{:enum [:tre :hki] :type "string"}
                         {:type "null"}]}}
               :required ["street" "city"]
               :title    "spec-tools.openapi.core-test/address"}}
             "*/*"
             {:schema
              {:type     "object"
               :properties
               {"id"      {:type "integer" :format "int64"}
                "name"    {:type "string"}
                "street"  {:type "string"}
                "filters" {:type "array" :items {:type "string"}}}
               :required ["id" "name"]}}}}
           (openapi/openapi-spec
            {:content
             {"test/html"
              {:schema
               {:type "string"}}}
             ::openapi/content {"application/json" ::user
                                "application/xml"  ::address
                                "*/*"              (s/keys :req-un [::id ::name]
                                                           :opt-un [::street ::filters])}})))

    (is (= {:content
            {"application/json"
             {:schema
              {:type     "object"
               :properties
               {"id"   {:type "integer" :format "int64"}
                "name" {:type "string"}
                "address"
                {:type     "object"
                 :properties
                 {"street" {:type "string"}
                  "city"
                  {:oneOf [{:enum [:tre :hki] :type "string"}
                           {:type "null"}]}}
                 :required ["street" "city"]
                 :title    "spec-tools.openapi.core-test/address"}}
               :required ["id" "name" "address"]
               :title    "spec-tools.openapi.core-test/user"
               :example  "Some examples here"
               :examples
               {:admin
                {:summary       "Admin user"
                 :description   "Super user"
                 :value         {:anything :here}
                 :externalValue "External value"}}
               :encoding {:contentType "application/json"}}}}}
           (openapi/openapi-spec
            {::openapi/content
             {"application/json"
              (st/spec
               {:spec             ::user
                :openapi/example  "Some examples here"
                :openapi/examples {:admin
                                   {:summary       "Admin user"
                                    :description   "Super user"
                                    :value         {:anything :here}
                                    :externalValue "External value"}}
                :openapi/encoding {:contentType "application/json"}})}}))))

  (testing "::headers"
    (is (= {:headers
            {:X-Rate-Limit-Limit
             {:description "The number of allowed requests in the current period",
              :schema      {:type "integer"}},
             :City
             {:description "",
              :required    false,
              :schema
              {:enum [:tre :hki] :type "string"}}
             :Authorization
             {:description ""
              :required    true
              :schema      {:type "string"}}
             :User
             {:description ""
              :required    true
              :schema
              {:type     "object"
               :properties
               {"id"   {:type "integer" :format "int64"}
                "name" {:type "string"}
                "address"
                {:type     "object"
                 :properties
                 {"street" {:type "string"}
                  "city"
                  {:oneOf [{:enum [:tre :hki] :type "string"}
                           {:type "null"}]}}
                 :required ["street" "city"]
                 :title    "spec-tools.openapi.core-test/address"}}
               :required ["id" "name" "address"]
               :title    "spec-tools.openapi.core-test/user"}}}}
           (openapi/openapi-spec
            {:headers
             {:X-Rate-Limit-Limit
              {:description "The number of allowed requests in the current period"
               :schema      {:type "integer"}}}
             ::openapi/headers
             {:City          ::city
              :Authorization ::token
              :User          ::user}})))))

;; TODO: This test is not really validate schema
#?(:clj
   (deftest test-schema-validation
     (is (not
          (nil?
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
                                      :header (s/keys :req-un [::token])}}}}}))))))

(deftest backport-openapi-meta-unnamespaced
  (is (= (openapi/transform
          (st/spec
           {:spec    string?
            :openapi {:type         "string"
                      :format       "password"
                      :random-value "42"}}))
         {:type "string" :format "password" :random-value "42"}))
  (is (= (openapi/transform
          (st/spec
           {:spec           string?
            :openapi        {:type "object"}
            :openapi/format "password"}))
         {:type "object"}))
  (is (= (openapi/transform
          (st/spec
           {:spec                 string?
            :openapi/type         "string"
            :openapi/format       "password"
            :openapi/random-value "42"}))
         {:type "string" :format "password" :random-value "42"})))
