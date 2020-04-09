(ns spec-tools.data-spec-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [spec-tools.data-spec :as ds]
            #?(:clj [clojure.test.check.generators :as gen])
            #?(:clj [com.gfredericks.test.chuck.clojure-test :refer [checking]])
            [spec-tools.core :as st]
            [spec-tools.spec :as spec])
  #?(:clj
     (:import clojure.lang.ExceptionInfo)))

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
        (is (= (s/form (dissoc person-keys-spec :name))
               (s/form (dissoc person-spec :name)))))

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

          (testing "map-transformer works recursively"
            (is (= value
                   (st/conform person-spec bloated st/strip-extra-keys-transformer))))))))

  (testing "heterogenous lists"
    (is (thrown-with-msg?
          #?(:clj Exception, :cljs js/Error)
          #"should be homogeneous"
          (ds/spec {:spec [int? int?]}))))

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
            {:test {:id "1"}}))
      (testing "non-qualified keywords are ok too"
        (is (= {:values [[:strings ["1" "2"]] [:ints [3]]]}
               (s/conform
                 (ds/spec ::values {:values [(ds/or {:ints [int?], :strings [string?]})]})
                 {:values [["1" "2"] [3]]}))))))

  (testing "encoding with or spec"
    (testing "when value matches first form of the or"
      (let [spec (ds/spec {:name ::int-or-double :spec (ds/or {:a {:an-int int?} :b {:a-double double?}})})
            value {:an-int 217322}
            value-string {:an-int "217322"}]
        (is (= value-string (st/encode spec value st/string-transformer)))))
    (testing "when value matches second form of the or"
      (let [spec (ds/spec {:name ::int-or-double :spec (ds/or {:a {:an-int int?} :b {:a-double double?}})})
            value {:a-double 217322.123}
            value-string {:a-double "217322.123"}]
        (is (= value-string (st/encode spec value st/string-transformer))))))

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
    (let [spec (ds/spec ::pred-keys {(s/spec #{:one :two}) string?})]
      (is
        (= true
           (s/valid? spec {:one "beer"})
           (s/valid? spec {:two "beers"})))
      (is
        (= false
           (s/valid? spec {:three "beers"})))))

  (testing "map-of key transformer"
    (is (= {:thanks :alex}
           (st/conform
             (ds/spec ::kikka {keyword? keyword?})
             {"thanks" "alex"}
             st/string-transformer)))))

(deftest top-level-maybe-test
  (let [spec (ds/spec ::maybe (ds/maybe {:n int?}))]
    (is (= true
           (s/valid? spec nil)
           (s/valid? spec {:n 1})))
    (is (= false
           (s/valid? spec {:n "1"})))))

(deftest alternative-syntax-test
  (testing "both ways produce same specs"
    (let [spec1 (ds/spec ::spec1 {::i int?})
          spec2 (ds/spec {:name ::spec2, :spec {::i int?}})]
      (is (= true
             (s/valid? spec1 {::i 1})
             (s/valid? spec2 {::i 1})))
      (is (= `(spec-tools.core/spec
                {:spec (clojure.spec.alpha/keys :req [::i])
                 :type :map
                 :leaf? false})
             (s/form (dissoc spec1 :name))
             (s/form (dissoc spec2 :name))))))

  (testing ":name can be ommitted if no specs are registered"
    (is (ds/spec {:spec {::i int?}})))

  (testing ":name is required if specs are registered"
    (is (thrown? #?(:clj Error, :cljs js/Error) (ds/spec {:spec {:i int?}})))))

(deftest keys-spec-extra-options-test
  (testing "keys-default"
    (let [data {(ds/req :a) any?
                (ds/opt :b) any?
                :c any?}]
      (testing "by default, plain keyword keys are required"
        (let [spec (ds/spec
                     {:name ::kikka
                      :spec data})]
          (is (s/valid? spec {:a 1, :b 1, :c 1}))
          (is (not (s/valid? spec {:a 1})))))
      (testing "plain keyword keys can be made optional by default"
        (let [spec (ds/spec
                     {:name ::kikka
                      :spec data
                      :keys-default ds/opt})]
          (is (s/valid? spec {:a 1, :b 1, :c 1}))
          (is (s/valid? spec {:a 1}))))))
  ;; TODO
  (testing "keys-spec"))

(deftest pithyless-test
  (is (map? (st/explain-data (ds/spec ::foo {:foo string?}) {:foo 42}))))

(deftest encode-decode-test
  (let [spec (ds/spec
               {:name ::order
                :spec {:id int?
                       :address {:street string?
                                 :country keyword?}
                       :tags #{keyword?}
                       :symbol symbol?
                       :price double?
                       :uuid uuid?
                       :shipping inst?
                       :secret (st/spec
                                 {:spec string?
                                  :encode/string #(apply str (reverse %2))
                                  :decode/string #(apply str (reverse %2))})}})
        value {:id 1
               :address {:street "Pellavatehtaankatu 10b"
                         :country :fi}
               :tags #{:bean :coffee :good}
               :symbol 'metosin
               :price 9.99
               :uuid #uuid"655b4976-9b2e-4c4a-b9b5-fa6efa909de6"
               :shipping #inst "2014-02-18T18:25:37.000-00:00"
               :secret "salaisuus-on-turvassa"}
        value-string {:id "1"
                      :address {:street "Pellavatehtaankatu 10b"
                                :country "fi"}
                      :tags #{"bean" "coffee" "good"}
                      :symbol "metosin"
                      :price "9.99"
                      :uuid "655b4976-9b2e-4c4a-b9b5-fa6efa909de6"
                      :shipping "2014-02-18T18:25:37.000Z"
                      :secret "assavrut-no-suusialas"}]

    (testing "encode"
      (is (= value-string (st/encode spec value st/string-transformer))))
    (testing "decode"
      (is (= value (st/decode spec value-string st/string-transformer))))
    (testing "roundtrip"
      (is (= value-string (as-> value-string $
                                (st/decode spec $ st/string-transformer)
                                (st/encode spec $ st/string-transformer)))))))

(deftest spec-name-test

  (testing "anonymous"
    (is (nil? (st/spec-name (ds/spec ::irrelevant (ds/or {:int int?})))))
    (is (nil? (st/spec-name (ds/spec ::irrelevant (ds/maybe int?)))))
    (is (nil? (st/spec-name (ds/spec ::irrelevant (s/cat :int int?))))))

  (testing "named"
    (is (= ::named1 (st/spec-name (ds/spec ::named1 int?))))
    (is (= ::named1 (st/spec-name (ds/spec ::named1 (st/spec int?)))))
    (is (= ::named1 (st/spec-name (ds/spec ::named1 [int?]))))
    (is (= ::named2 (st/spec-name (ds/spec ::named2 #{int?}))))
    (is (= ::named3 (st/spec-name (ds/spec ::named3 {:ints [int?]
                                                     :map {:ints [int?]}}))))
    (testing "nested vectors are anonymous"
      (let [spec (st/get-spec :spec-tools.data-spec-test$named3/ints)]
        (is (and spec (nil? (:name spec)))))
      (let [spec (st/get-spec :spec-tools.data-spec-test$named3$map/ints)]
        (is (and spec (nil? (:name spec))))))
    (testing "nested maps have a name"
      (let [spec (st/get-spec :spec-tools.data-spec-test$named3/map)]
        (is (and spec (:name spec)))))))

#?(:clj
   (deftest unspecing
     (testing "simpler possible specs"
       (is (= int? (ds/unspec (ds/spec {:name ::t1 :spec int?}))))
       (is (= string? (ds/unspec (ds/spec {:name ::t1 :spec string?}))))
       (is (= float? (ds/unspec (ds/spec {:name ::t1 :spec float?}))))
       (is (= boolean? (ds/unspec (ds/spec {:name ::t1 :spec boolean?}))))
       (is (= keyword? (ds/unspec (ds/spec {:name ::t1 :spec keyword?})))))

     (testing "simple map using data-spec"
       (let [ds1 {:street          string?
                  :number          int?
                  :value           float?
                  :is_main?        boolean?
                  :clj-programmer? keyword?}]
         (is (= ds1 (ds/unspec (ds/spec {:name ::ds1 :spec ds1}))))

         (testing "we can handle nillable keywords"
           (let [ds2 (merge ds1 {:address (ds/maybe {:street string?
                                                     :number int?})
                                 :city    string?})]
             (is (= ds2 (ds/unspec (ds/spec {:name ::ds2 :spec ds2}))))))

         (testing "also vector field, that also should be homogeneous."
           (let [ds3 (merge ds1 {:orders [{:id          int?
                                           :description string?}]})]
             (is (= ds3 (ds/unspec (ds/spec {:name ::ds3 :spec ds3}))))))

         (testing "support for a set"
           (let [ds4 (assoc ds1 :languages #{keyword?})
                 ds5 (assoc ds1 :languages #{string?})
                 ds6 (assoc ds1 :languages #{int?})
                 ds7 (assoc ds1 :languages #{boolean?})]
             (is (= ds4 (ds/unspec (ds/spec {:name ::ds4 :spec ds4}))))
             (is (= ds5 (ds/unspec (ds/spec {:name ::ds5 :spec ds5}))))
             (is (= ds6 (ds/unspec (ds/spec {:name ::ds6 :spec ds6}))))
             (is (= ds7 (ds/unspec (ds/spec {:name ::ds7 :spec ds7}))))))

         (testing "support for or operator"
           (let [ds8 (merge ds1 {:aliases [(or {:maps    {:alias string?}
                                                :strings string?})]})
                 ds9 (merge ds1 {:testing [(or {:ints int?
                                                :bol  boolean?
                                                :val  float?
                                                :key  keyword?})]})]
             (is (= ds8 (ds/unspec (ds/spec {:name ::ds8 :spec ds8}))))
             (is (= ds9 (ds/unspec (ds/spec {:name ::ds9 :spec ds9}))))))))))

#?(:clj
   (def unspec-gen (let [-kw?      (gen/return keyword?)
                         -str?     (gen/return string?)
                         -bol?     (gen/return boolean?)
                         -flt?     (gen/return float?)
                         -int?     (gen/return integer?)
                         -compound [-kw? -str? -bol? -flt? -int?]
                         -vec?     (gen/vector (gen/one-of -compound) 1)
                         -set?     (gen/set (gen/one-of -compound) {:num-elements 1})
                         -map?     (fn [inner-gen] (gen/not-empty (gen/map gen/keyword inner-gen)))
                         -map-opt? (fn [inner-gen] (gen/not-empty (gen/map (gen/bind gen/keyword
                                                                                    (fn [k]
                                                                                      (gen/return (ds/opt k))))
                                                                          inner-gen)))]
                     (gen/recursive-gen (fn [inner-gen]
                                          (gen/frequency
                                           [[3 (-map? inner-gen)]
                                            [3 (-map-opt? inner-gen)]
                                            [2 (gen/fmap (fn [v] (ds/or v)) (-map? inner-gen))]
                                            [2 (gen/fmap (fn [v] (ds/maybe v)) (-map? inner-gen))]]))
                                        (gen/one-of (concat -compound [-vec? -set?]))))))

#?(:clj
   (deftest property-spec->unspec->spec
     (checking "able to perform a complete round-trip going from data-spec -> spec -> data-spec again" 200
               [data-spec unspec-gen]
               (is (ds/unspec (ds/spec {:name ::property-based :spec data-spec}))))))
