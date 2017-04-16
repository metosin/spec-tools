(ns spec-tools.data-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec :as s]
            [spec-tools.core :as st]
            [spec-tools.spec :as spec]
            [spec-tools.data :refer [opt req] :as data]
            [spec-tools.conform :as conform]))

(deftest map-test
  (testing "nested map spec"
    (let [person {::id integer?
                  ::age ::age
                  :boss boolean?
                  (req :name) string?
                  (opt :description) string?
                  :languages #{keyword?}
                  :orders [{:id int?
                            :description string?}]
                  :address {:street string?
                            :zip string?}}
          person-spec (data/data-spec ::person person)
          person-keys-spec (st/spec
                             (s/keys
                               :req [::id ::age]
                               :req-un [:spec-tools.data-test$person/boss
                                        :spec-tools.data-test$person/name
                                        :spec-tools.data-test$person/languages
                                        :spec-tools.data-test$person/orders
                                        :spec-tools.data-test$person/address]
                               :opt-un [:spec-tools.data-test$person/description]))]

      (testing "normal keys-spec-spec is generated"
        (is (= (s/form person-keys-spec)
               (s/form person-spec))))

      (testing "nested keys are in the registry"
        (let [generated-keys (->> (st/registry #"spec-tools.data-test\$person.*") (map first) set)]
          (is (= #{:spec-tools.data-test$person/boss
                   :spec-tools.data-test$person/name
                   :spec-tools.data-test$person/description
                   :spec-tools.data-test$person/languages
                   :spec-tools.data-test$person/orders
                   :spec-tools.data-test$person$orders/id
                   :spec-tools.data-test$person$orders/description
                   :spec-tools.data-test$person/address
                   :spec-tools.data-test$person$address/zip
                   :spec-tools.data-test$person$address/street}
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

          (testing "map-conforming works recursively"
            (is (= value
                   (st/conform person-spec bloated {:map conform/strip-extra-keys}))))))))

  (testing "top-level vector"
    (is (true?
          (s/valid?
            (data/data-spec ::vector [{:olipa {:kerran string?}}])
            [{:olipa {:kerran "avaruus"}}
             {:olipa {:kerran "elämä"}}])))
    (is (false?
          (s/valid?
            (data/data-spec ::vector [{:olipa {:kerran string?}}])
            [{:olipa {:kerran :muumuu}}]))))

  (testing "top-level set"
    (is (true?
          (s/valid?
            (data/data-spec ::vector #{{:olipa {:kerran string?}}})
            #{{:olipa {:kerran "avaruus"}}
              {:olipa {:kerran "elämä"}}})))
    (is (false?
          (s/valid?
            (data/data-spec ::vector #{{:olipa {:kerran string?}}})
            #{{:olipa {:kerran :muumuu}}}))))

  (testing "mega-nested"
    (is (true?
          (s/valid?
            (data/data-spec ::vector [[[[[[[[[[string?]]]]]]]]]])
            [[[[[[[[[["kikka" "kakka" "kukka"]]]]]]]]]])))
    (is (false?
          (s/valid?
            (data/data-spec ::vector [[[[[[[[[[string?]]]]]]]]]])
            [[[[[[[[[123]]]]]]]]]))))

  (testing "predicate keys"
    (is
      (true?
        (s/valid?
          (data/data-spec ::pred-keys {string? {keyword? [integer?]}})
          {"winning numbers" {:are [1 12 46 45]}
           "empty?" {:is []}})))
    (is
      (false?
        (s/valid?
          (data/data-spec ::pred-keys {string? {keyword? [integer?]}})
          {"invalid spec" "is this"})))))
