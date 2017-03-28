(ns spec-tools.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec :as s]
            [spec-tools.core :as st]
            [spec-tools.specs :as sts]
            [spec-tools.conform :as stc]
            [clojure.string :as str]
            [spec-tools.conform :as conform]))

(s/def ::age (s/and sts/integer? #(> % 10)))
(s/def ::over-a-million (s/and sts/int? #(> % 1000000)))
(s/def ::lat sts/double?)
(s/def ::language (s/and sts/keyword? #{:clojure :clojurescript}))
(s/def ::truth sts/boolean?)
(s/def ::uuid sts/uuid?)
(s/def ::birthdate sts/inst?)

(deftest extract-extra-info-test
  (testing "keys are extracted from keys-specs"
    (let [spec (st/spec
                 (s/keys
                   :req [::age]
                   :opt [::lat]
                   :req-un [::uuid]
                   :opt-un [::truth]))]
      (is (= #{::age ::lat :uuid :truth}
             (:spec/keys spec))))))

(deftest spec?-test
  (testing "spec"
    (let [spec (s/spec integer?)]
      (is (= spec (s/spec? spec)))
      (is (nil? (st/spec? spec)))))
  (testing "Spec"
    (let [spec (st/spec integer?)]
      (is (= spec (s/spec? spec)))
      (is (= spec (st/spec? spec))))))

(deftest specs-test
  (let [my-integer? (st/spec integer?)]

    (testing "creation"
      (is (= (st/spec integer?)
             (st/spec {:pred integer?})
             (st/spec integer? {}))))

    (testing "wrapped predicate work as a predicate"
      (is (true? (my-integer? 1)))
      (is (false? (my-integer? "1"))))

    (testing "wrapped spec does not work as a predicate"
      (let [my-spec (st/spec (s/keys :req [::age]))]
        (is (thrown? #?(:clj Exception, :cljs js/Error) (my-spec {::age 20})))))

    (testing "adding info"
      (let [info {:description "desc"
                  :example 123}]
        (is (= info (:info (assoc my-integer? :info info))))))

    (testing "are specs"
      (is (true? (s/valid? my-integer? 1)))
      (is (false? (s/valid? my-integer? "1")))

      (testing "fully qualifed predicate symbol is returned with s/form"
        (is (= ['spec-tools.core/spec
                #?(:clj  'clojure.core/integer?
                   :cljs 'cljs.core/integer?)
                {:spec/type :long}] (s/form my-integer?)))
        (is (= ['spec 'integer? {:spec/type :long}] (s/describe my-integer?))))

      (testing "type resolution"
        (is (= (st/spec integer?)
               (st/spec integer? {:spec/type :long}))))

      (testing "serialization"
        (let [spec (st/spec integer? {:description "cool", :spec/type ::integer})]
          (is (= `(st/spec integer? {:description "cool", :spec/type ::integer})
                 (s/form spec)
                 (st/deserialize (st/serialize spec))))))

      (testing "gen"
        (is (seq? (s/exercise my-integer?)))))))

(deftest doc-test

  (testing "creation"
    (is (= (st/doc integer? {:description "kikka"})
           (st/doc {:pred integer?, :description "kikka"})
           (st/doc integer? {:description "kikka"})
           (st/spec {:pred integer?, :description "kikka", :spec/type nil}))))

  (testing "just docs, #12"
    (let [spec (st/doc integer? {:description "kikka"})]
      (is (= "kikka" (:description spec)))
      (is (true? (s/valid? spec 1)))
      (is (false? (s/valid? spec "1")))
      (is (= `(st/spec integer? {:description "kikka", :spec/type nil})
             (st/deserialize (st/serialize spec))
             (s/form spec))))))

(deftest reason-test
  (let [expected-problem {:path [] :pred 'pos-int?, :val -1, :via [], :in []}]
    (testing "explain-data"
      (is (= #?(:clj  #:clojure.spec{:problems [expected-problem]}
                :cljs #:cljs.spec{:problems [expected-problem]})
             (st/explain-data (st/spec pos-int?) -1)
             (s/explain-data (st/spec pos-int?) -1))))
    (testing "explain-data with reason"
      (is (= #?(:clj  #:clojure.spec{:problems [(assoc expected-problem :reason "positive")]}
                :cljs #:cljs.spec{:problems [(assoc expected-problem :reason "positive")]})
             (st/explain-data (st/spec pos-int? {:spec/reason "positive"}) -1)
             (s/explain-data (st/spec pos-int? {:spec/reason "positive"}) -1))))))

(deftest spec-tools-conform-test
  (testing "in default mode"
    (testing "nothing is conformed"
      (is (= st/+invalid+ (st/conform ::age "12")))
      (is (= st/+invalid+ (st/conform ::over-a-million "1234567")))
      (is (= st/+invalid+ (st/conform ::lat "23.1234")))
      (is (= st/+invalid+ (st/conform ::language "clojure")))
      (is (= st/+invalid+ (st/conform ::truth "false")))
      (is (= st/+invalid+ (st/conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
      (is (= st/+invalid+ (st/conform ::birthdate "2014-02-18T18:25:37.456Z")))
      (is (= st/+invalid+ (st/conform ::birthdate "2014-02-18T18:25:37Z")))))

  (testing "string-conformers"
    (let [conform #(st/conform %1 %2 stc/string-conformers)]
      (testing "everything gets conformed"
        (is (= 12 (conform ::age "12")))
        (is (= 1234567 (conform ::over-a-million "1234567")))
        (is (= 23.1234 (conform ::lat "23.1234")))
        (is (= false (conform ::truth "false")))
        (is (= :clojure (conform ::language "clojure")))
        (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
               (conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
        (is (= #inst "2014-02-18T18:25:37.456Z"
               (conform ::birthdate "2014-02-18T18:25:37.456Z")))
        (is (= #inst "2014-02-18T18:25:37Z"
               (conform ::birthdate "2014-02-18T18:25:37Z"))))))

  (testing "json-conformers"
    (let [conform #(st/conform %1 %2 stc/json-conformers)]
      (testing "some are not conformed"
        (is (= st/+invalid+ (conform ::age "12")))
        (is (= st/+invalid+ (conform ::over-a-million "1234567")))
        (is (= st/+invalid+ (conform ::lat "23.1234")))
        (is (= st/+invalid+ (conform ::truth "false"))))
      (testing "some are conformed"
        (is (= :clojure (conform ::language "clojure")))
        (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
               (conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
        (is (= #inst "2014-02-18T18:25:37.456Z"
               (conform ::birthdate "2014-02-18T18:25:37.456Z")))
        (is (= #inst "2014-02-18T18:25:37Z"
               (conform ::birthdate "2014-02-18T18:25:37Z")))))))

(deftest conform!-test
  (testing "suceess"
    (is (= 12 (st/conform! ::age "12" stc/string-conformers))))
  (testing "failing"
    (is (thrown? #?(:clj Exception, :cljs js/Error) (st/conform! ::age "12")))
    (try
      (st/conform! ::age "12")
      (catch #?(:clj Exception, :cljs js/Error) e
        (let [data (ex-data e)]
          (is (= {:type :spec/problems
                  :problems [{:path [], :pred 'integer?, :val "12", :via [::age], :in []}]
                  :spec :spec-tools.core-test/age
                  :value "12"}
                 data)))))))

(deftest explain-tests
  (testing "without conforming"
    (is (= st/+invalid+ (st/conform sts/int? "12")))
    (is (= {::s/problems [{:path [], :pred 'int?, :val "12", :via [], :in []}]}
           (st/explain-data sts/int? "12"))))
  (testing "with conforming"
    (is (= 12 (st/conform sts/int? "12" stc/string-conformers)))
    (is (= nil
           (st/explain-data sts/int? "12" stc/string-conformers)))))

(s/def ::height integer?)
(s/def ::weight integer?)
(s/def ::person (st/spec (s/keys :req-un [::height ::weight])))

(st/spec (s/keys :req-un [::height ::weight]))

(deftest map-specs-test
  (let [person {:height 200, :weight 80, :age 36}]

    (testing "conform"
      (is (= {:height 200, :weight 80, :age 36}
             (s/conform ::person person)
             (st/conform ::person person))))

    (testing "stripping extra keys"
      (is (= {:height 200, :weight 80}
             (st/conform ::person person {:map conform/strip-extra-keys}))))))

(s/def ::human (st/spec (s/keys :req-un [::height ::weight]) {:spec/type ::human}))

(defn bmi [{:keys [height weight]}]
  (let [h (/ height 100)]
    (double (/ weight (* h h)))))

(deftest custom-map-specs-test
  (let [person {:height 200, :weight 80}
        bmi-conformer (fn [_ human]
                        (assoc human :bmi (bmi human)))]

    (testing "conform"
      (is (= {:height 200, :weight 80}
             (s/conform ::human person)
             (st/conform ::human person))))

    (testing "bmi-conforming"
      (is (= {:height 200, :weight 80, :bmi 20.0}
             (st/conform ::human person {::human bmi-conformer}))))))

(deftest unform-test
  (let [unform-conform #(s/unform %1 (st/conform %1 %2 stc/string-conformers))]
    (testing "conformed values can be unformed"
      (is (= 12 (unform-conform ::age "12")))
      (is (= 1234567 (unform-conform ::age "1234567")))
      (is (= 23.1234 (unform-conform ::lat "23.1234")))
      (is (= false (unform-conform ::truth "false")))
      (is (= :clojure (unform-conform ::language "clojure")))
      (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
             (unform-conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
      (is (= #inst "2014-02-18T18:25:37.456Z"
             (unform-conform ::birthdate "2014-02-18T18:25:37.456Z")))
      (is (= #inst "2014-02-18T18:25:37.456Z"
             (unform-conform ::birthdate "2014-02-18T18:25:37.456Z"))))))

(deftest extending-test
  (let [my-conformations (-> stc/string-conformers
                             (assoc
                               :keyword
                               (fn [_ value]
                                 (-> value
                                     str/upper-case
                                     str/reverse
                                     keyword))))]
    (testing "string-conformers"
      (is (= :kikka (st/conform sts/keyword? "kikka" stc/string-conformers))))
    (testing "my-conformers"
      (is (= :AKKIK (st/conform sts/keyword? "kikka" my-conformations))))))

(deftest map-test
  (testing "nested map spec"
    (let [st-map (st/coll-spec
                   ::my-map
                   {::id integer?
                    ::age ::age
                    :boss sts/boolean?
                    (st/req :name) string?
                    (st/opt :description) string?
                    :languages #{keyword?}
                    :orders [{:id int?
                              :description string?}]
                    :address {:street string?
                              :zip string?}})
          s-keys (st/spec
                   (s/keys
                     :req [::id ::age]
                     :req-un [:spec-tools.core-test$my-map/boss
                              :spec-tools.core-test$my-map/name
                              :spec-tools.core-test$my-map/languages
                              :spec-tools.core-test$my-map/orders
                              :spec-tools.core-test$my-map/address]
                     :opt-un [:spec-tools.core-test$my-map/description]))]

      (testing "normal keys-spec-spec is generated"
        (is (= (s/form s-keys) (s/form st-map))))
      (testing "nested keys are in the registry"
        (let [generated-keys (->> (st/registry #"spec-tools.core-test\$my-map.*") (map first) set)]
          (is (= #{:spec-tools.core-test$my-map/boss
                   :spec-tools.core-test$my-map/name
                   :spec-tools.core-test$my-map/description
                   :spec-tools.core-test$my-map/languages
                   :spec-tools.core-test$my-map/orders
                   :spec-tools.core-test$my-map$orders/id
                   :spec-tools.core-test$my-map$orders/description
                   :spec-tools.core-test$my-map/address
                   :spec-tools.core-test$my-map$address/zip
                   :spec-tools.core-test$my-map$address/street}
                 generated-keys))))
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
            (is (true? (s/valid? st-map value))))

          (testing "map-conforming works recursively"
            (is (= value
                   (st/conform st-map bloated {:map conform/strip-extra-keys}))))))))

  (testing "top-level vector"
    (is (true?
          (s/valid?
            (st/coll-spec ::vector [{:olipa {:kerran string?}}])
            [{:olipa {:kerran "avaruus"}}
             {:olipa {:kerran "elämä"}}])))
    (is (false?
          (s/valid?
            (st/coll-spec ::vector [{:olipa {:kerran string?}}])
            [{:olipa {:kerran :muumuu}}]))))

  (testing "top-level set"
    (is (true?
          (s/valid?
            (st/coll-spec ::vector #{{:olipa {:kerran string?}}})
            #{{:olipa {:kerran "avaruus"}}
              {:olipa {:kerran "elämä"}}})))
    (is (false?
          (s/valid?
            (st/coll-spec ::vector #{{:olipa {:kerran string?}}})
            #{{:olipa {:kerran :muumuu}}}))))

  (testing "mega-nested"
    (is (true?
          (s/valid?
            (st/coll-spec ::vector [[[[[[[[[[string?]]]]]]]]]])
            [[[[[[[[[["kikka" "kakka" "kukka"]]]]]]]]]])))
    (is (false?
          (s/valid?
            (st/coll-spec ::vector [[[[[[[[[[string?]]]]]]]]]])
            [[[[[[[[[123]]]]]]]]]))))

  (testing "predicate keys"
    (is
      (true?
        (s/valid?
          (st/coll-spec ::pred-keys {string? {keyword? [integer?]}})
          {"winning numbers" {:are [1 12 46 45]}
           "empty?" {:is []}})))
    (is
      (false?
        (s/valid?
          (st/coll-spec ::pred-keys {string? {keyword? [integer?]}})
          {"invalid spec" "is this"})))))
