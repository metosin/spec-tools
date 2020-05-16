(ns spec-tools.swagger.core-test
  (:require
    [clojure.test :refer [deftest testing is are]]
    [spec-tools.swagger.core :as swagger]
    [clojure.spec.alpha :as s]
    [spec-tools.spec :as spec]
    #?(:clj [ring.swagger.validator :as v])
    [spec-tools.core :as st]))

(s/def ::integer integer?)
(s/def ::string string?)
(s/def ::set #{1 2 3})
(s/def ::keys (s/keys :req-un [::integer]))
(s/def ::spec (st/spec
                {:spec string?
                 :description "description"
                 :json-schema/default "123"
                 :json-schema/example "json-schema-example"
                 :swagger/example "swagger-example"}))
(s/def ::keys2 (s/keys :req-un [::integer ::spec]))

(def exceptations
  {int?
   {:type "integer", :format "int64"}

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
   {}

   #{1 2 3}
   {:enum [1 3 2], :type "string"}

   (s/int-in 1 10)
   {:type "integer"
    :format "int64"
    :x-allOf [{:type "integer"
               :format "int64"}
              {:minimum 1
               :maximum 10}]}

   (s/keys :req-un [::integer] :opt-un [::string])
   {:type "object"
    :properties {"integer" {:type "integer"}
                 "string" {:type "string"}}
    :required ["integer"]}

   ::keys
   {:type "object",
    :properties {"integer" {:type "integer"}},
    :required ["integer"],
    :title "spec-tools.swagger.core-test/keys"}

   (s/and int? pos?)
   {:type "integer"
    :format "int64",
    :x-allOf [{:type "integer"
               :format "int64"}
              {:minimum 0
               :exclusiveMinimum true}]}

   (s/and spec/int?)
   {:type "integer"
    :format "int64",
    :x-allOf [{:type "integer"
               :format "int64"}]}

   (s/or :int int? :pos pos?)
   {:type "integer"
    :format "int64",
    :x-anyOf [{:type "integer"
               :format "int64"}
              {:minimum 0
               :exclusiveMinimum true}]}

   (s/merge (s/keys :req-un [::integer])
            (s/keys :req-un [::string]))
   {:type "object",
    :properties {"integer" {:type "integer"},
                 "string" {:type "string"}},
    :required ["integer" "string"]}

   (st/merge (s/keys :req-un [::integer])
             (s/keys :req-un [::string]))
   {:type "object",
    :properties {"integer" {:type "integer"},
                 "string" {:type "string"}},
    :required ["integer" "string"]}

   (s/merge (s/keys :req-un [::integer])
            (s/or :foo (s/keys :req-un [::string])
                  :bar (s/keys :req-un [::set])))
   {:type "object",
    :properties {"integer" {:type "integer"},
                 "string" {:type "string"}
                 "set" {:enum [1 3 2]
                        :type "string"}},
    :required ["integer"]}

   sequential?
   {:type "array" :items {}}

   (s/every integer?)
   {:type "array", :items {:type "integer"}}

   (s/every-kv string? integer?)
   {:type "object", :additionalProperties {:type "integer"}}

   (s/coll-of string?)
   {:type "array", :items {:type "string"}}

   (s/coll-of string? :into '())
   {:type "array", :items {:type "string"}}

   (s/coll-of string? :into [])
   {:type "array", :items {:type "string"}}

   (s/coll-of string? :into #{})
   {:type "array", :items {:type "string"}, :uniqueItems true}

   (s/map-of string? integer?)
   {:type "object", :additionalProperties {:type "integer"}}

   (s/* integer?)
   {:type "array", :items {:type "integer"}}

   (s/+ integer?)
   {:type "array", :items {:type "integer"}, :minItems 1}

   (s/? integer?)
   {:type "array", :items {:type "integer"}, :minItems 0}

   (s/alt :int integer? :string string?)
   {:type "integer", :x-anyOf [{:type "integer"} {:type "string"}]}

   (s/cat :int integer? :string string?)
   {:type "array"
    :items {:type "integer"
            :x-anyOf [{:type "integer"}
                      {:type "string"}]}}

   (s/tuple integer? string?)
   {:type "array"
    :items {}
    :x-items [{:type "integer"} {:type "string"}]}

   (s/map-of string? clojure.core/integer?)
   {:type "object", :additionalProperties {:type "integer"}}

   (s/nilable string?)
   {:type "string", :x-nullable true}

   ::spec
   {:type "string"
    :description "description"
    :default "123"
    :title "spec-tools.swagger.core-test/spec"
    :example "swagger-example"}})

(deftest test-expectations
  (doseq [[spec swagger-spec] exceptations]
    (is (= swagger-spec (swagger/transform spec)))))

(deftest parameter-test
  (testing "nilable body is not required"
    (is (= [{:in "body",
             :name "body",
             :description "",
             :required false,
             :schema {:type "object",
                      :title "spec-tools.swagger.core-test/keys2",
                      :properties {"integer" {:type "integer"}
                                   "spec" {:default "123"
                                           :description "description"
                                           :example "swagger-example"
                                           :title "spec-tools.swagger.core-test/spec"
                                           :type "string"}},
                      :required ["integer" "spec"],
                      :x-nullable true}}]
           (swagger/extract-parameter :body (s/nilable ::keys2))))))

#?(:clj
   (deftest test-parameter-validation
     (let [swagger-spec (fn [schema]
                          {:swagger "2.0"
                           :info {:title "" :version ""}
                           :paths {"/hello" {:get
                                             {:responses
                                              {200 {:description ""
                                                    :schema schema}}}}}})]

       (testing "invalid schema fails on swagger spec validation"
         (is (-> {:type "invalid"} swagger-spec v/validate)))

       (testing "all expectations pass the swagger spec validation"
         (doseq [[spec] exceptations]
           (is (= nil (-> spec swagger/transform swagger-spec v/validate))))))))

(s/def ::id string?)
(s/def ::name string?)
(s/def ::street string?)
(s/def ::city (s/nilable #{:tre :hki}))
(s/def ::address (s/keys :req-un [::street ::city]))
(s/def ::user (s/keys :req-un [::id ::name ::address]))

(deftest expand-test

  (testing "::parameters"
    (is (= {:parameters [{:in "query"
                          :name "name2"
                          :description "this survives the merge"
                          :type "string"
                          :required true}
                         {:in "query"
                          :name "name"
                          :description ""
                          :type "string"
                          :required false}
                         {:in "query"
                          :name "street"
                          :description ""
                          :type "string"
                          :required false}
                         {:in "query"
                          :name "city"
                          :description ""
                          :type "string"
                          :required false
                          :enum [:tre :hki]
                          :allowEmptyValue true}
                         {:in "path"
                          :name "spec-tools.swagger.core-test/id"
                          :description ""
                          :type "string"
                          :required true}
                         {:in "body",
                          :name "spec-tools.swagger.core-test/address",
                          :description "",
                          :required true,
                          :schema {:type "object",
                                   :title "spec-tools.swagger.core-test/address",
                                   :properties {"street" {:type "string"},
                                                "city" {:enum [:tre :hki],
                                                        :type "string"
                                                        :x-nullable true}},
                                   :required ["street" "city"]}}]}
           (swagger/swagger-spec
             {:parameters [{:in "query"
                            :name "name"
                            :description "this will be overridden"
                            :required false}
                           {:in "query"
                            :name "name2"
                            :description "this survives the merge"
                            :type "string"
                            :required true}]
              ::swagger/parameters
              {:query (s/keys :opt-un [::name ::street ::city])
               :path (s/keys :req [::id])
               :body ::address}})))
    (is (= {:parameters [{:in "query"
                          :name "name2"
                          :description "this survives the merge"
                          :type "string"
                          :required true}
                         {:in "query"
                          :name "name"
                          :description ""
                          :type "string"
                          :required false}
                         {:in "query"
                          :name "street"
                          :description ""
                          :type "string"
                          :required false}
                         {:in "query"
                          :name "city"
                          :description ""
                          :type "string"
                          :required false
                          :enum [:tre :hki]
                          :allowEmptyValue true}
                         {:in "path"
                          :name "spec-tools.swagger.core-test/id"
                          :description ""
                          :type "string"
                          :required true}
                         {:in "body",
                          :name "spec-tools.swagger.core-test/address",
                          :description "",
                          :required true,
                          :schema {:type "object",
                                   :title "spec-tools.swagger.core-test/address",
                                   :properties {"street" {:type "string"},
                                                "city" {:enum [:tre :hki],
                                                        :type "string"
                                                        :x-nullable true}},
                                   :required ["street" "city"]}}]}
           (swagger/swagger-spec
             {:parameters [{:in "query"
                            :name "name"
                            :description "this will be overridden"
                            :required false}
                           {:in "query"
                            :name "name2"
                            :description "this survives the merge"
                            :type "string"
                            :required true}]
              ::swagger/parameters
              {:query (st/create-spec {:spec (s/keys :opt-un [::name ::street ::city])})
               :path (st/create-spec {:spec (s/keys :req [::id])})
               :body (st/create-spec {:spec ::address})}}))))

  (testing "::responses"
    (is (= {:responses
            {200 {:schema
                  {:type "object"
                   :properties
                   {"id" {:type "string"}
                    "name" {:type "string"}
                    "address" {:type "object"
                               :properties {"street" {:type "string"}
                                            "city" {:enum [:tre :hki]
                                                    :type "string"
                                                    :x-nullable true}}
                               :required ["street" "city"]
                               :title "spec-tools.swagger.core-test/address"}}
                   :required ["id" "name" "address"]
                   :title "spec-tools.swagger.core-test/user"}
                  :description ""}
             404 {:description "Ohnoes."}
             500 {:description "fail"}}}
           (swagger/swagger-spec
             {:responses {404 {:description "fail"}
                          500 {:description "fail"}}
              ::swagger/responses {200 {:schema ::user}
                                   404 {:description "Ohnoes."}}})))))

#?(:clj
   (deftest test-schema-validation
     (let [data {:swagger "2.0"
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
                                                                  404 {:description "Ohnoes."}}}}}}]
       (is (nil? (-> data swagger/swagger-spec v/validate))))))

(deftest backport-swagger-meta-unnamespaced
  (is (= (swagger/transform
           (st/spec {:spec string?
                     :swagger {:type "string"
                               :format "password"
                               :random-value "42"}}))
         {:type "string" :format "password" :random-value "42"}))

  (is (= (swagger/transform
           (st/spec {:spec string?
                     :swagger {:type "object"}
                     :swagger/format "password"}))
         {:type "object"}))

  (is (= (swagger/transform
           (st/spec {:spec string?
                     :swagger/type "string"
                     :swagger/format "password"
                     :swagger/random-value "42"}))
         {:type "string" :format "password" :random-value "42"})))
