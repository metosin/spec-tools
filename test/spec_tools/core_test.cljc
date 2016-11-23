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
  (let [my-integer? (st/spec ::st/long integer?)]
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
                ::st/long
                #?(:clj  'clojure.core/integer?
                   :cljs 'cljs.core/integer?)] (s/form my-integer?)))
        (is (= ['spec ::st/long 'integer?] (s/describe my-integer?))))

      (testing "serialization"
        (let [spec (st/spec ::integer clojure.core/integer? {:description "cool"})]
          (is (= `(st/spec ::integer integer? {:description "cool"})
                 (s/form spec)
                 #?(:clj (s/form (eval (s/form spec))))))))

      (testing "gen"
        (is (seq? (s/exercise my-integer?)))))))

(deftest spec-tools-conform-test
  (testing "in default mode"
    (testing "nothing is conformed"
      (is (= ::s/invalid (st/conform ::age "12")))
      (is (= ::s/invalid (st/conform ::over-a-million "1234567")))
      (is (= ::s/invalid (st/conform ::lat "23.1234")))
      (is (= ::s/invalid (st/conform ::language "clojure")))
      (is (= ::s/invalid (st/conform ::truth "false")))
      (is (= ::s/invalid (st/conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
      (is (= ::s/invalid (st/conform ::birthdate "2006-01-02T15:04:05.999999-07:00")))))

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
        (is (= #inst "2006-01-02T15:04:05.999999-07:00"
               (conform ::birthdate "2006-01-02T15:04:05.999999-07:00"))))))

  (testing "json-conformers"
    (let [conform #(st/conform %1 %2 st/json-conformers)]
      (testing "some are not conformed"
        (is (= ::s/invalid (conform ::age "12")))
        (is (= ::s/invalid (conform ::over-a-million "1234567")))
        (is (= ::s/invalid (conform ::lat "23.1234")))
        (is (= ::s/invalid (conform ::truth "false"))))
      (testing "some are conformed"
        (is (= :clojure (conform ::language "clojure")))
        (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
               (conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
        (is (= #inst "2006-01-02T15:04:05.999999-07:00"
               (conform ::birthdate "2006-01-02T15:04:05.999999-07:00")))))))

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
      (is (= #inst "2006-01-02T15:04:05.999999-07:00"
             (unform-conform ::birthdate "2006-01-02T15:04:05.999999-07:00"))))))

(deftest extending-test
  (let [my-conformations (-> st/string-conformers
                             (assoc
                               ::st/keyword
                               (comp
                                 keyword
                                 str/reverse
                                 str/upper-case)))]
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
        (let [generated-keys (->> (s/registry)
                                  (filter #(-> % first str (str/starts-with? ":spec-tools.core-test$my-map")))
                                  (map first)
                                  set)]
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
             {:olipa {:kerran "elämä"}}]))))

  (testing "top-level set"
    (is (true?
          (s/valid?
            (st/coll-spec ::vector #{{:olipa {:kerran string?}}})
            #{{:olipa {:kerran "avaruus"}}
              {:olipa {:kerran "elämä"}}})))))
