(ns spec-tools.json-schema-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.data-spec :as ds]
            [spec-tools.spec :as spec]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [spec-tools.json-schema :as jsc]
            #?(:clj
               [scjsv.core :as scjsv])))

(s/def ::integer integer?)
(s/def ::string string?)
(s/def ::set #{1 2 3})

(s/def ::a string?)
(s/def ::b string?)
(s/def ::c string?)
(s/def ::d string?)
(s/def ::e string?)

(s/def ::keys (s/keys :opt [::e]
                      :opt-un [::e]
                      :req [::a (or ::b (and ::c ::d))]
                      :req-un [::a (or ::b (and ::c ::d))]))

(s/def ::keys-no-req (s/keys :opt [::e]
                             :opt-un [::e]))

(deftest simple-spec-test
  (testing "primitive predicates"
    ;; You're intented to call jsc/to-json with a registered spec, but to avoid
    ;; boilerplate, we do inline specization here.
    (is (= (jsc/transform (s/spec int?)) {:type "integer" :format "int64"}))
    (is (= (jsc/transform (s/spec integer?)) {:type "integer"}))
    (is (= (jsc/transform (s/spec float?)) {:type "number"}))
    (is (= (jsc/transform (s/spec double?)) {:type "number"}))
    (is (= (jsc/transform (s/spec string?)) {:type "string"}))
    (is (= (jsc/transform (s/spec boolean?)) {:type "boolean"}))
    #?(:clj (is (= (jsc/transform rational?) {:type "double"})))
    #?(:clj (is (= (jsc/transform (s/spec decimal?)) {:type "number" :format "double"})))
    #?(:clj (is (= (jsc/transform (s/spec bytes?)) {:type "string" :format "byte"})))
    (is (= (jsc/transform (s/spec inst?)) {:type "string", :format "date-time"}))
    (is (= (jsc/transform (s/spec nil?)) {:type "null"}))
    (is (= (jsc/transform #{1 2 3}) {:enum [1 3 2]})))
  (testing "clojure.spec predicates"
    (is (= (jsc/transform (s/nilable ::string)) {:oneOf [{:type "string"} {:type "null"}]}))
    (is (= (jsc/transform (s/int-in 1 10)) {:allOf [{:type "integer" :format "int64"} {:minimum 1 :maximum 10}]})))
  (testing "simple specs"
    (is (= (jsc/transform ::integer) {:type "integer"}))
    (is (= (jsc/transform ::set) {:enum [1 3 2]})))

  (testing "clojure.specs"
    (is (= (jsc/transform (s/keys :req-un [::integer] :opt-un [::string]))
           {:type "object"
            :properties {"integer" {:type "integer"} "string" {:type "string"}}
            :required ["integer"]}))
    (is (= (jsc/transform ::keys)
           {:type "object"
            :title "spec-tools.json-schema-test/keys"
            :properties {"spec-tools.json-schema-test/a" {:type "string"}
                         "spec-tools.json-schema-test/b" {:type "string"}
                         "spec-tools.json-schema-test/c" {:type "string"}
                         "spec-tools.json-schema-test/d" {:type "string"}
                         "spec-tools.json-schema-test/e" {:type "string"}
                         "a" {:type "string"}
                         "b" {:type "string"}
                         "c" {:type "string"}
                         "d" {:type "string"}
                         "e" {:type "string"}}
            :required ["spec-tools.json-schema-test/a"
                       "spec-tools.json-schema-test/b"
                       "spec-tools.json-schema-test/c"
                       "spec-tools.json-schema-test/d"
                       "a"
                       "b"
                       "c"
                       "d"]}))
    (is (= (jsc/transform ::keys-no-req)
           {:type "object"
            :title "spec-tools.json-schema-test/keys-no-req"
            :properties {"spec-tools.json-schema-test/e" {:type "string"}
                         "e" {:type "string"}}}))
    (is (= (jsc/transform (s/or :int integer? :string string?))
           {:anyOf [{:type "integer"} {:type "string"}]}))
    (is (= (jsc/transform (s/and integer? pos?))
           {:allOf [{:type "integer"} {:minimum 0 :exclusiveMinimum true}]}))
    (is (= (jsc/transform (s/and spec/integer? pos?))
           {:allOf [{:type "integer"} {:minimum 0 :exclusiveMinimum true}]}))
    (is (= (jsc/transform (s/merge (s/keys :req [::integer])
                                   (s/keys :req [::string])))
           {:type "object"
            :properties {"spec-tools.json-schema-test/integer" {:type "integer"}
                         "spec-tools.json-schema-test/string" {:type "string"}}
            :required ["spec-tools.json-schema-test/integer" "spec-tools.json-schema-test/string"]}))
    (is (= (jsc/transform (st/merge (s/keys :req [::integer])
                                    (s/keys :req [::string])))
           {:type "object"
            :properties {"spec-tools.json-schema-test/integer" {:type "integer"}
                         "spec-tools.json-schema-test/string" {:type "string"}}
            :required ["spec-tools.json-schema-test/integer" "spec-tools.json-schema-test/string"]}))
    (is (= (jsc/transform (s/every integer?)) {:type "array" :items {:type "integer"}}))
    (is (= (jsc/transform (s/every-kv string? integer?))
           {:type "object" :additionalProperties {:type "integer"}}))
    (is (= (jsc/transform (s/coll-of string?)) {:type "array" :items {:type "string"}}))
    (is (= (jsc/transform (s/coll-of string? :into '())) {:type "array" :items {:type "string"}}))
    (is (= (jsc/transform (s/coll-of string? :into [])) {:type "array" :items {:type "string"}}))
    (is (= (jsc/transform (s/coll-of string? :into #{})) {:type "array" :items {:type "string"}, :uniqueItems true}))
    (is (= (jsc/transform (s/map-of string? integer?))
           {:type "object" :additionalProperties {:type "integer"}}))
    (is (= (jsc/transform (s/* integer?)) {:type "array" :items {:type "integer"}}))
    (is (= (jsc/transform (s/+ integer?)) {:type "array" :items {:type "integer"} :minItems 1}))
    (is (= (jsc/transform (s/? integer?)) {:type "array" :items {:type "integer"} :minItems 0}))
    (is (= (jsc/transform (s/alt :int integer? :string string?))
           {:anyOf [{:type "integer"} {:type "string"}]}))
    (is (= (jsc/transform (s/cat :int integer? :string string?))
           {:type "array"
            :items {:anyOf [{:type "integer"} {:type "string"}]}}))
    ;; & is broken (http://dev.clojure.org/jira/browse/CLJ-2152)
    (is (= (jsc/transform (s/tuple integer? string?))
           {:type "array" :items [{:type "integer"} {:type "string"}]}))
    ;; keys* is broken (http://dev.clojure.org/jira/browse/CLJ-2152)
    (is (= (jsc/transform (s/map-of string? clojure.core/integer?))
           {:type "object" :additionalProperties {:type "integer"}}))
    (is (= (jsc/transform (s/nilable string?))
           {:oneOf [{:type "string"} {:type "null"}]})))
  (testing "failing clojure.specs"
    (is (not= (jsc/transform (s/coll-of (s/tuple string? any?) :into {}))
              {:type "object", :additionalProperties {:type "string"}}))))

(s/def ::id string?)
(s/def ::age string?)
(s/def ::zipcode string?)
(s/def ::user (s/keys :req-un [::id ::age]))
(s/def ::address (s/keys :req-un [::user ::zipcode]))

(deftest infer-schema-titles-test
  (is (some? (:title (jsc/transform ::address))))
  (is (nil? (:title (jsc/transform ::address {:infer-titles false}))))
  (is (some? (:title (jsc/transform ::address {:infer-titles true})))))

#?(:clj
   (defn test-spec-conversion [spec]
     (let [validate (scjsv/validator (jsc/transform spec))]
       (testing (str "with spec " spec)
         (checking "JSON schema accepts the data generated by the spec gen" 100
           [x (s/gen spec)]
           (is (nil? (validate x)) (str x " (" spec ") does not conform to JSON Schema")))))))

(s/def ::compound (s/keys :req-un [::integer] :opt-un [::string]))

#?(:clj
   (deftest validating-test
     (test-spec-conversion ::integer)
     (test-spec-conversion ::string)
     (test-spec-conversion ::set)
     (test-spec-conversion ::compound)
     (test-spec-conversion (s/nilable ::string))
     (test-spec-conversion (s/int-in 0 100))))

;; Test the example from README

(s/def ::age (s/and integer? #(> % 18)))

(def person-spec
  (ds/spec
    ::person
    {::id integer?
     :age ::age
     :name string?
     :likes {string? boolean?}
     (ds/req :languages) #{keyword?}
     (ds/opt :address) {:street string?
                        :zip string?}}))

(deftest readme-test
  (is (= {:type "object"
          :title "spec-tools.json-schema-test/person"
          :required ["spec-tools.json-schema-test/id" "age" "name" "likes" "languages"]
          :properties {"spec-tools.json-schema-test/id" {:type "integer"}
                       "age" {:type "integer"}
                       "name" {:type "string"}
                       "likes" {:type "object" :additionalProperties {:type "boolean"}}
                       "languages" {:type "array", :items {:type "string"}, :uniqueItems true}
                       "address" {:type "object"
                                  :required ["street" "zip"]
                                  :properties {"street" {:type "string"}
                                               "zip" {:type "string"}}}}}
         (jsc/transform person-spec))))

(deftest additional-json-schema-data-test
  (is (= {:type "integer"
          :title "integer"
          :description "it's an int"
          :default 42}
         (jsc/transform
           (st/spec
             {:spec integer?
              :name "integer"
              :description "it's an int"
              :json-schema/default 42})))))

(deftest deeply-nested-test
  (is (= {:type "array"
          :title "spec-tools.json-schema-test/nested"
          :items {:type "array"
                  :items {:type "array"
                          :items {:type "array"
                                  :items {:type "string"}}}}}
         (jsc/transform
           (ds/spec
             ::nested
             [[[[string?]]]])))))

(s/def ::user any?)
(s/def ::name string?)
(s/def ::parent (s/nilable ::user))
(s/def ::user (s/keys :req-un [::name ::parent]))

(deftest recursive-spec-test
  (is (= {:type "object",
          :properties {"name" {:type "string"}
                       "parent" {:oneOf [{} {:type "null"}]}},
          :required ["name" "parent"],
          :title "spec-tools.json-schema-test/user"}
         (jsc/transform ::user))))

(s/def ::a string?)
(s/def ::foo string?)
(s/def ::bar string?)

(deftest merge-comination-schemas-test
  (is (= {:anyOf [{:type "object"
                   :properties {"bar" {:type "string"}}
                   :required ["bar"]}
                  {:type "object"
                   :properties {"foo" {:type "string"}}
                   :required ["foo"]}]}
         (jsc/transform
           (s/or :bar (s/keys :req-un [::bar])
                 :foo (s/keys :req-un [::foo]))))
      "s/or generates anyOf")

  (is (= {:type "object"
          :properties {"a" {:type "string"}
                       "foo" {:type "string"}
                       "bar" {:type "string"}}
          :required ["a"]}
         (jsc/transform
           (s/merge (s/keys :req-un [::a])
                    (s/or :foo (s/keys :req-un [::foo])
                          :bar (s/keys :req-un [::bar])))))
      "anyOf properties are merged into properties")

  (is (= {:type "object"
          :properties {"a" {:type "string"}
                       "foo" {:type "string"}
                       "bar" {:type "string"}}
          :required ["a" "bar" "foo"]}
         (jsc/transform
           (s/merge (s/keys :req-un [::a])
                    (s/and (s/keys :req-un [::bar])
                           (s/keys :req-un [::foo])))))
      "allOf properties are merged into properties and required"))

(deftest backport-swagger-meta-unnamespaced
  (is (= (jsc/transform
           (st/spec {:spec string?
                     :json-schema {:type "string"
                                   :format "password"
                                   :random-value "42"}}))
         {:type "string" :format "password" :random-value "42"}))

  (is (= (jsc/transform
           (st/spec {:spec string?
                     :json-schema {:type "object"}
                     :json-schema/format "password"}))
         {:type "object"}))

  (is (= (jsc/transform
           (st/spec {:spec string?
                     :json-schema/type "string"
                     :json-schema/format "password"
                     :json-schema/random-value "42"}))
         {:type "string" :format "password" :random-value "42"})))
