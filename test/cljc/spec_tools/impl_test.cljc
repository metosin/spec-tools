(ns spec-tools.impl-test
  (:require [clojure.test :refer [deftest testing is]]
            [spec-tools.impl :as impl]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [spec-tools.core :as st]))

(deftest namespaced-name-test
  (is (= nil (impl/qualified-name nil)))
  (is (= "kikka" (impl/qualified-name :kikka)))
  (is (= "spec-tools.impl-test/kikka" (impl/qualified-name ::kikka))))

(deftest nilable-spec-test
  (is (= false (impl/nilable-spec? nil)))
  (is (= false (impl/nilable-spec? string?)))
  (is (= true (impl/nilable-spec? (s/nilable string?)))))

(deftest deep-merge-test
  (is (= {:a 2
          :b [1 2 3 4]
          :c {:a 2, :b 1}}
         (impl/deep-merge
           {:a 1
            :b [1 2]
            :c {:a 1}}
           {:a [1 2]
            :c {:a 2, :b 1}}
           {:a 2
            :b [3 4]}))))

(deftest unlift-keys-test
  (is (= {:olut 0.5
          :sielu true}
         (impl/unlift-keys
           {:iso/olut 0.5
            :iso/sielu true
            :olipa "kerran"
            :kikka/kukka "kakka"}
           "iso"))))

(def ignoring-spec #(dissoc % ::s/spec))

(deftest coll-of-spec-tests
  (let [spec (s/coll-of string? :into [])
        impl (impl/coll-of-spec string? [])]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/coll-of (st/spec {:spec string? :type :string :leaf? true}) :into [])
           (s/form (impl/coll-of-spec spec/string? []))))
    (is (= nil
           (s/explain-data spec ["1"])
           (s/explain-data impl ["1"])))
    (comment "CLJ-CLJ-2168"
             (is (= (ignoring-spec (s/explain-data spec [1]))
                    (ignoring-spec (s/explain-data impl [1])))))
    (is (= ["1"]
           (s/conform spec ["1"])
           (s/conform impl ["1"])))))

(deftest coll-of-specs-distinguish-between-data-types
  (let [spec-set (impl/coll-of-spec int? #{})
        spec-vec (impl/coll-of-spec int? [])
        spec-map (impl/map-of-spec int? int?)]
    (is (= true (s/valid? spec-set #{1 2 3})))
    (is (= false (s/valid? spec-set [1 2 3])))
    (is (= true (s/valid? spec-vec [1 2 3])))
    (is (= false (s/valid? spec-vec #{1 2 3})))
    (is (= true (s/valid? spec-map {4 2})))
    (is (= false (s/valid? spec-map #{[1 2]})))
    (is (= false (s/valid? spec-map #{1 2 3 4})))))

(deftest map-of-spec-tests
  (let [spec (s/map-of string? string? :conform-keys true)
        impl (impl/map-of-spec string? string?)]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/map-of
              (st/spec {:spec string? :type :string :leaf? true})
              (st/spec {:spec string? :type :string :leaf? true})
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
    (is (= (ignoring-spec (s/explain-data spec {::int "1"}))
           (ignoring-spec (s/explain-data impl {::int "1"}))))
    (is (= {::int 1, :bool true, :kikka "kakka"}
           (s/conform spec {::int 1, :bool true, :kikka "kakka"})
           (s/conform impl {::int 1, :bool true, :kikka "kakka"})))))

(deftest nilable-spec-tst
  (let [spec (s/nilable string?)
        impl (impl/nilable-spec string?)]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/nilable (st/spec {:spec string? :type :string :leaf? true}))
           (s/form (impl/nilable-spec spec/string?))))
    (is (= nil
           (s/explain-data spec "1")
           (s/explain-data spec nil)
           (s/explain-data impl "1")
           (s/explain-data impl nil)))
    (is (= (ignoring-spec (s/explain-data spec [1]))
           (ignoring-spec (s/explain-data impl [1]))))
    (is (= "1"
           (s/conform spec "1")
           (s/conform impl "1")))))

(deftest or-spec-tst
  (let [spec (s/or :int int?, :string string?)
        impl (impl/or-spec {:int int?, :string string?})]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/or :int (st/spec {:spec int? :type :long :leaf? true})
                  :string (st/spec {:spec string? :type :string :leaf? true}))
           (s/form (impl/or-spec {:int spec/int?, :string spec/string?}))))
    (is (= nil
           (s/explain-data spec "1")
           (s/explain-data spec 1)
           (s/explain-data impl "1")
           (s/explain-data impl 1)))
    (is (= (ignoring-spec (s/explain-data spec [1]))
           (ignoring-spec (s/explain-data impl [1]))))
    (is (= [:string "1"]
           (s/conform spec "1")
           (s/conform impl "1")))))
