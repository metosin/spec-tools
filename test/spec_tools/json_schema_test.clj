(ns spec-tools.json-schema-test
  (:require  [clojure.test :refer [deftest testing is]]
             [clojure.spec :as s]
             [spec-tools.json-schema :as jsc]))

(s/def ::int int?)
(s/def ::string string?)
(s/def ::set #{1 2 3})

(deftest simple-spec-test
  (testing "primitive predicates"
    (is (= (jsc/to-json int?) {:type "integer"}))
    (is (= (jsc/to-json float?) {:type "number"}))
    (is (= (jsc/to-json string?) {:type "string"}))
    (is (= (jsc/to-json #{1 2 3}) {:enum [1 3 2]})))
  (testing "simple specs"
    (is (= (jsc/to-json ::int) {:type "integer"}))
    (is (= (jsc/to-json ::set) {:enum [1 3 2]})))
  (testing "composite objects"
    (is (= (jsc/to-json (s/keys :req-un [::int] :opt-un [::string]))
           {:type "object"
            :properties {"int" {:type "integer"} "string" {:type "string"}}
            :required ["int"]
            :additionalProperties false}))
    (is (= (jsc/to-json (s/tuple int? string?))
           {:type "array" :items [{:type "integer"} {:type "string"}] :minItems 2}))
    (is (= (jsc/to-json (s/every int?)) {:type "array" :items {:type "integer"}}))
    (is (= (jsc/to-json (s/* int?)) {:type "array" :items {:type "integer"}}))
    (is (= (jsc/to-json (s/+ int?)) {:type "array" :items {:type "integer"} :minItems 1})))
  (testing "composite specs"
    (is (= (jsc/to-json (s/or :int int? :string string?))
           {:anyOf [{:type "integer"} {:type "string"}]}))
    (is (= (jsc/to-json (s/and int? pos?))
           {:allOf [{:type "integer"} {:minimum 0 :exclusiveMinimum true}]}))))
