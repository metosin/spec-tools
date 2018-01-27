(ns spec-tools.data-spec-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [spec-tools.data-spec :as ds]
            [spec-tools.core :as st]
            [spec-tools.spec :as spec])
  #?(:clj (:import clojure.lang.ExceptionInfo)))

(def ignoring-spec #(dissoc % ::s/spec))

(deftest coll-of-spec-tests
  (let [spec (s/coll-of string? :into [])
        impl (#'ds/coll-of-spec string? [])]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/coll-of (st/spec {:spec string? :type :string}) :into [])
           (s/form (#'ds/coll-of-spec spec/string? []))))
    (is (= nil
           (s/explain-data spec ["1"])
           (s/explain-data impl ["1"])))
    (comment "CLJ-CLJ-2168"
             (is (= (ignoring-spec (s/explain-data spec [1]))
                    (ignoring-spec (s/explain-data impl [1])))))
    (is (= ["1"]
           (s/conform spec ["1"])
           (s/conform impl ["1"])))))

(deftest map-of-spec-tests
  (let [spec (s/map-of string? string? :conform-keys true)
        impl (#'ds/map-of-spec string? string?)]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/map-of
              (st/spec {:spec string? :type :string})
              (st/spec {:spec string? :type :string})
              :conform-keys true)
           (s/form (#'ds/map-of-spec spec/string? spec/string?))))
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
        impl (#'ds/keys-spec {:req [::int]
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
        impl (#'ds/nilable-spec string?)]
    (is (= (s/form spec)
           (s/form impl)))
    (is (= `(s/nilable (st/spec {:spec string? :type :string}))
           (s/form (#'ds/nilable-spec spec/string?))))
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

(s/def ::age (s/and spec/integer? #(> % 10)))

(deftest data-spec-tests
  (testing "nested data-spec"
    (let [person {::id integer?
                  ::age ::age
                  :boss boolean?
                  (ds/req :name) string?
                  (ds/opt :description) string?
                  :languages #{keyword?}
                  :orders [{:id int?
                            :description string?}]
                  :address (ds/maybe {:street string?
                                      :zip string?})}
          person-spec (ds/spec ::person person)
          person-keys-spec (st/spec
                             (s/keys
                               :req [::id ::age]
                               :req-un [:spec-tools.data-spec-test$person/boss
                                        :spec-tools.data-spec-test$person/name
                                        :spec-tools.data-spec-test$person/languages
                                        :spec-tools.data-spec-test$person/orders
                                        :spec-tools.data-spec-test$person/address]
                               :opt-un [:spec-tools.data-spec-test$person/description]))]

      (testing "normal keys-spec-spec is generated"
        (is (= (s/form person-keys-spec)
               (s/form person-spec))))

      (testing "nested keys are in the registry"
        (let [generated-keys (->> (st/registry #"spec-tools.data-spec-test\$person.*") (map first) set)]
          (is (= #{:spec-tools.data-spec-test$person/boss
                   :spec-tools.data-spec-test$person/name
                   :spec-tools.data-spec-test$person/description
                   :spec-tools.data-spec-test$person/languages
                   :spec-tools.data-spec-test$person/orders
                   :spec-tools.data-spec-test$person$orders/id
                   :spec-tools.data-spec-test$person$orders/description
                   :spec-tools.data-spec-test$person/address
                   :spec-tools.data-spec-test$person$address/zip
                   :spec-tools.data-spec-test$person$address/street}
                 generated-keys))
          (testing "all registered specs are Specs"
            (is (true? (every? st/spec? (map st/get-spec generated-keys)))))))
      (testing "validating"
        (let [value {::id 1
                     ::age 63
                     :boss true
                     :name "Liisa"
                     :languages #{:clj :cljs}
                     :orders [{:id 1, :description "cola"}
                              {:id 2, :description "kebab"}]
                     :description "Liisa is a valid boss"
                     :address {:street "Amurinkatu 2"
                               :zip "33210"}}
              bloated (-> value
                          (assoc-in [:KIKKA] true)
                          (assoc-in [:address :KIKKA] true))]

          (testing "data can be validated"
            (is (true? (s/valid? person-spec value))))

          (testing "fails with invalid data"
            (is (false? (s/valid? person-spec (dissoc value :boss)))))

          (testing "optional keys"
            (is (true? (s/valid? person-spec (dissoc value :description)))))

          (testing "maybe values"
            (is (true? (s/valid? person-spec (assoc value :address nil)))))

          (testing "map-conforming works recursively"
            (is (= value
                   (st/conform person-spec bloated st/strip-extra-keys-conforming))))))))

  (testing "or spec"
    (let [strings-or-keywords (ds/or {::ui-target {:id string?}
                                      ::data-target [keyword?]})]
      (is (thrown? ExceptionInfo
                   (#'spec-tools.data-spec/-or-spec ::foo :bar)))
      (is (s/valid?
            (ds/spec ::str-kw-vector strings-or-keywords)
            {:id "1"}))
      (is (s/valid?
            (ds/spec ::str-kw-vector strings-or-keywords)
            [:foo :bar]))
      (is (s/valid?
            (ds/spec ::str-kw-vector [strings-or-keywords])
            [{:id "1"}]))
      (is (s/valid?
            (ds/spec ::str-kw-map {:test strings-or-keywords})
            {:test {:id "1"}}))))
  (testing "top-level vector"
    (is (true?
          (s/valid?
            (ds/spec ::vector [{:olipa {:kerran string?}}])
            [{:olipa {:kerran "avaruus"}}
             {:olipa {:kerran "elämä"}}])))
    (is (false?
          (s/valid?
            (ds/spec ::vector [{:olipa {:kerran string?}}])
            [{:olipa {:kerran :muumuu}}]))))

  (testing "top-level set"
    (is (true?
          (s/valid?
            (ds/spec ::vector #{{:olipa {:kerran string?}}})
            #{{:olipa {:kerran "avaruus"}}
              {:olipa {:kerran "elämä"}}})))
    (is (false?
          (s/valid?
            (ds/spec ::vector #{{:olipa {:kerran string?}}})
            #{{:olipa {:kerran :muumuu}}}))))

  (testing "mega-nested"
    (is (true?
          (s/valid?
            (ds/spec ::vector [[[[[[[[[[string?]]]]]]]]]])
            [[[[[[[[[["kikka" "kakka" "kukka"]]]]]]]]]])))
    (is (false?
          (s/valid?
            (ds/spec ::vector [[[[[[[[[[string?]]]]]]]]]])
            [[[[[[[[[123]]]]]]]]]))))

  (testing "predicate keys"
    (is
      (true?
        (s/valid?
          (ds/spec ::pred-keys {string? {keyword? [integer?]}})
          {"winning numbers" {:are [1 12 46 45]}
           "empty?" {:is []}})))
    (is
      (false?
        (s/valid?
          (ds/spec ::pred-keys {string? {keyword? [integer?]}})
          {"invalid spec" "is this"}))))

  (testing "set keys"
    (let [spec (ds/spec ::pred-keys {#{:one :two} string?})]
      (is
        (= true
           (s/valid? spec {:one "beer"})
           (s/valid? spec {:two "beers"})))
      (is
        (= false
           (s/valid? spec {:three "beers"})))))

  (testing "map-of key conforming"
    (is (= {:thanks :alex}
           (st/conform
             (ds/spec ::kikka {keyword? keyword?})
             {"thanks" "alex"}
             st/string-conforming)))))

(deftest pithyless-test
  (is (map? (st/explain-data (ds/spec ::foo {:foo string?}) {:foo 42}))))
