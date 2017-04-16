(ns spec-tools.impl-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec :as s]
            [spec-tools.impl :as impl]))

(deftest coll-of-spec-tests
  (let [spec (s/coll-of string? :into [])
        impl (impl/coll-of-spec string? `string? [])]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= nil
           (s/explain-data spec ["1"])
           (s/explain-data impl ["1"])))
    (is (= (s/explain-data spec [1])
           (s/explain-data impl [1])))
    (is (= ["1"]
           (s/conform spec ["1"])
           (s/conform impl ["1"])))))

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
