(ns spec-tools.impl-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.spec :as spec]
            [spec-tools.impl :as impl]))

(deftest coll-of-spec-tests
  (let [spec (s/coll-of string? :into [])
        impl (impl/coll-of-spec string? [])]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/coll-of (st/spec string? {:type :string}) :into [])
           (s/form (impl/coll-of-spec spec/string? []))))
    (is (= nil
           (s/explain-data spec ["1"])
           (s/explain-data impl ["1"])))
    (is (= (s/explain-data spec [1])
           (s/explain-data impl [1])))
    (is (= ["1"]
           (s/conform spec ["1"])
           (s/conform impl ["1"])))))

(s/form (impl/coll-of-spec spec/string? []))

(deftest map-of-spec-tests
  (let [spec (s/map-of string? string? :conform-keys true)
        impl (impl/map-of-spec string? string?)]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/map-of
             (st/spec string? {:type :string})
             (st/spec string? {:type :string})
             :conform-keys true)
           (s/form (impl/map-of-spec spec/string? spec/string?))))
    (is (= nil
           (s/explain-data spec {"key" "value"})
           (s/explain-data impl {"key" "value"})))
    (is (= (s/explain-data spec {"key" "value"})
           (s/explain-data impl {"key" "value"})))
    (is (= {"key" "value"}
           (s/conform spec {"key" "value"})
           (s/conform impl {"key" "value"})))))

(s/def ::int int?)
(s/def ::str string?)
(s/def ::bool boolean?)

(deftest keys-spec-tests
  (let [spec (s/keys :req [::int]
                     :opt [::str]
                     :req-un [::bool]
                     :opt-un [::int])
        impl (impl/keys-spec {:req [::int]
                              :opt [::str]
                              :req-un [::bool]
                              :opt-un [::int]})]

    (is (= (s/form spec)
           (s/form impl)))
    (is (= nil
           (s/explain-data spec {::int 1, :bool true})
           (s/explain-data impl {::int 1, :bool true})))
    (is (= (s/explain-data spec {::int "1"})
           (s/explain-data impl {::int "1"})))
    (is (= {::int 1, :bool true, :kikka "kakka"}
           (s/conform spec {::int 1, :bool true, :kikka "kakka"})
           (s/conform impl {::int 1, :bool true, :kikka "kakka"})))))

(deftest nilable-spec-tst
  (let [spec (s/nilable string?)
        impl (impl/nilable-spec string?)]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/nilable (st/spec string? {:type :string}))
           (s/form (impl/nilable-spec spec/string?))))
    (is (= nil
           (s/explain-data spec "1")
           (s/explain-data spec nil)
           (s/explain-data impl "1")
           (s/explain-data impl nil)))
    (is (= (s/explain-data spec [1])
           (s/explain-data impl [1])))
    (is (= "1"
           (s/conform spec "1")
           (s/conform impl "1")))))
