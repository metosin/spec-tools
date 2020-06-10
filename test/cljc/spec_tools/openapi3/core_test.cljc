(ns spec-tools.openapi3.core-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [spec-tools.core :as st]
            [spec-tools.openapi3.core :as openapi]))

(s/def ::integer-spec integer?)
(s/def ::string-spec string?)
(s/def ::nilable-spec (s/nilable string?))
(s/def ::keys-spec (s/keys :req-un [::integer-spec]))
(s/def ::spec (st/spec
               {:spec string?
                :description "Spec description"
                :name "spec-name"}))
(s/def ::keys-spec2 (s/keys :req-un [::integer-spec ::spec]))


(s/def ::id int?)
(s/def ::name string?)
(s/def ::street string?)
(s/def ::city (s/nilable #{:tre :hki}))
(s/def ::filters (s/coll-of string? :into []))
(s/def ::address (s/keys :req-un [::street ::city]))
(s/def ::user (s/keys :req-un [::id ::name ::address]))

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
                "city" {:oneOf [{:enum [:tre :hki] :type "string"}
                                {:type "null"}]}}
               :required ["street" "city"]
               :title    "spec-tools.openapi3.core-test/address"}}]}
           (openapi/openapi3-spec
            {:parameters
             [{:name        "username",
               :in          "path",
               :description "username to fetch",
               :required    true,
               :schema      {:type "string"}
               :style       "simple"}]
             ::openapi/parameters
             {:path   (s/keys :req-un [::id])
              :query  (s/keys :req-un [::name] :opt-un [::street ::city ::filters])
              :header ::user}})))
    ;; (is (= {}
    ;;        (openapi/openapi3-spec
    ;;         {:parameters
    ;;          [{:in          "query"
    ;;            :name        "name"
    ;;            :description "this will be overriden"
    ;;            :required    false}]})))
    ))
