(ns spec-tools.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec :as s]
            [spec-tools.core :as st]
            [clojure.string :as str]))

(s/def ::age (s/and st/integer? #(> % 10)))
(s/def ::over-a-million (s/and st/int? #(> % 1000000)))
(s/def ::lat st/double?)
(s/def ::language (s/and st/keyword? #{:clojure :clojurescript}))
(s/def ::truth st/boolean?)
(s/def ::uuid st/uuid?)
(s/def ::birthdate st/inst?)

(deftest specs-test
  (let [my-integer? (st/spec :long integer?)]
    (testing "work as predicates"
      (is (true? (my-integer? 1)))
      (is (false? (my-integer? "1"))))

    (testing "adding info"
      (let [info {:description "desc"
                  :example 123}]
        (is (= info (:info (assoc my-integer? :info info))))))

    (testing "are specs"
      (is (true? (s/valid? my-integer? 1)))
      (is (false? (s/valid? my-integer? "1")))

      (testing "fully qualifed predicate symbol is returned with s/form"
        (is (= ['spec-tools.core/spec
                :long
                #?(:clj  'clojure.core/integer?
                   :cljs 'cljs.core/integer?)] (s/form my-integer?)))
        (is (= ['spec :long 'integer?] (s/describe my-integer?))))

      (testing "serialization"
        (let [spec (st/spec ::integer clojure.core/integer? {:description "cool"})]
          (is (= `(st/spec ::integer integer? {:description "cool"})
                 (s/form spec)
                 (st/deserialize (st/serialize spec))))))

      (testing "gen"
        (is (seq? (s/exercise my-integer?)))))))

(deftest doc-test
  (testing "just docs, #12"
    (let [spec (st/doc integer? {:description "kikka"})]
      (is (= "kikka" (:description spec)))
      (is (true? (s/valid? spec 1)))
      (is (false? (s/valid? spec "1")))
      (is (= `(st/spec nil integer? {:description "kikka"})
             (st/deserialize (st/serialize spec))
             (s/form spec))))))

(deftest spec-tools-conform-test
  (testing "in default mode"
    (testing "nothing is conformed"
      (is (= st/invalid (st/conform ::age "12")))
      (is (= st/invalid (st/conform ::over-a-million "1234567")))
      (is (= st/invalid (st/conform ::lat "23.1234")))
      (is (= st/invalid (st/conform ::language "clojure")))
      (is (= st/invalid (st/conform ::truth "false")))
      (is (= st/invalid (st/conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
      (is (= st/invalid (st/conform ::birthdate "2014-02-18T18:25:37.456Z")))
      (is (= st/invalid (st/conform ::birthdate "2014-02-18T18:25:37Z")))))

  (testing "string-conformers"
    (let [conform #(st/conform %1 %2 st/string-conformers)]
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
    (let [conform #(st/conform %1 %2 st/json-conformers)]
      (testing "some are not conformed"
        (is (= st/invalid (conform ::age "12")))
        (is (= st/invalid (conform ::over-a-million "1234567")))
        (is (= st/invalid (conform ::lat "23.1234")))
        (is (= st/invalid (conform ::truth "false"))))
      (testing "some are conformed"
        (is (= :clojure (conform ::language "clojure")))
        (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
               (conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
        (is (= #inst "2014-02-18T18:25:37.456Z"
               (conform ::birthdate "2014-02-18T18:25:37.456Z")))
        (is (= #inst "2014-02-18T18:25:37Z"
               (conform ::birthdate "2014-02-18T18:25:37Z")))))))

(s/def ::height integer?)
(s/def ::weight integer?)
(s/def ::person (st/spec ::human (s/keys :req-un [::height ::weight])))

(defn bmi [{:keys [height weight]}]
  (let [h (/ height 100)]
    (double (/ weight (* h h)))))

(deftest map-specs-test
  (let [person {:height 200, :weight 80}
        bmi-conformer (fn [_ human]
                        (assoc human :bmi (bmi human)))]

    (testing "conform"
      (is (= {:height 200, :weight 80}
             (s/conform ::person person)
             (st/conform ::person person))))

    (testing "bmi-conforming"
      (is (= {:height 200, :weight 80, :bmi 20.0}
             (st/conform ::person person {::human bmi-conformer}))))))

(deftest unform-test
  (let [unform-conform #(s/unform %1 (st/conform %1 %2 st/string-conformers))]
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
  (let [my-conformations (-> st/string-conformers
                             (assoc
                               :keyword
                               (fn [_ value]
                                 (-> value
                                     str/upper-case
                                     str/reverse
                                     keyword))))]
    (testing "string-conformers"
      (is (= :kikka (st/conform st/keyword? "kikka" st/string-conformers))))
    (testing "my-conformers"
      (is (= :AKKIK (st/conform st/keyword? "kikka" my-conformations))))))

(deftest map-test
  (testing "nested map spec"
    (let [st-map (st/coll-spec
                   ::my-map
                   {::id integer?
                    ::age ::age
                    :boss st/boolean?
                    (st/req :name) string?
                    (st/opt :description) string?
                    :languages #{keyword?}
                    :orders [{:id int?
                              :description string?}]
                    :address {:street string?
                              :zip string?}})
          s-keys (s/keys
                   :req [::id ::age]
                   :req-un [:spec-tools.core-test$my-map/boss
                            :spec-tools.core-test$my-map/name
                            :spec-tools.core-test$my-map/languages
                            :spec-tools.core-test$my-map/orders
                            :spec-tools.core-test$my-map/address]
                   :opt-un [:spec-tools.core-test$my-map/description])]

      (testing "vanilla keys-spec is generated"
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
                               :zip "33210"}}]
          (is (true? (s/valid? st-map value)))))))

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
