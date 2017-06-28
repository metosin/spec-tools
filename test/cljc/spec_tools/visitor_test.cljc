(ns spec-tools.visitor-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.data-spec :as ds]
            [spec-tools.visitor :as visitor]))

(s/def ::str string?)
(s/def ::int integer?)
(s/def ::map (s/keys :req [::str] :opt [::int]))

(defn collect [dispatch _ children _] `[~dispatch ~@children])

(deftest test-visit
  (is (= (visitor/visit #(pos? %) collect) [::s/unknown]))
  (is (= (visitor/visit #{1 2 3} collect) [:spec-tools.visitor/set 1 3 2]))
  (is (= (visitor/visit int? collect) ['clojure.core/int?]))
  (is (= (visitor/visit ::map collect)
         '[clojure.spec.alpha/keys [clojure.core/string?] [clojure.core/integer?]])))

(s/def ::age number?)
(s/def ::first-name string?)
(s/def ::last-name string?)
(s/def ::name (s/keys :req-un [::first-name ::last-name]))
(s/def ::user (s/keys :req-un [::name ::age]))

; https://gist.github.com/shafeeq/c2ded8e71579a26e44c2191536e01c0d
(deftest recursive-visit
  (let [specs (atom {})
        collect (fn [_ spec _ _]
                  (if-let [registered (s/get-spec spec)]
                    (swap! specs assoc spec (s/form registered))
                    @specs))]
    (is (= (visitor/visit ::user collect)
           {::age (s/form ::age)
            ::first-name (s/form ::first-name)
            ::last-name (s/form ::last-name)
            ::name (s/form ::name)
            ::user (s/form ::user)}))))

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

(deftest readme-visitor-test
  (let [expected #{:spec-tools.visitor-test/id
                   :spec-tools.visitor-test$person/age
                   :spec-tools.visitor-test$person/name
                   :spec-tools.visitor-test$person/likes
                   :spec-tools.visitor-test$person/languages
                   :spec-tools.visitor-test$person$address/street
                   :spec-tools.visitor-test$person$address/zip
                   :spec-tools.visitor-test$person/address}
        specs (visitor/visit person-spec (visitor/spec-collector))]
    (testing "all specs are found"
      (is (= expected (-> specs keys set))))
    (testing "all spec forms are correct"
      (is (= (->> expected (map s/get-spec) set)
             (-> specs vals set))))

    (comment
      (testing "convert-specs! transforms all specs into Spec records"
        (visitor/convert-specs! person-spec)
        (is (true?
              (->> expected
                   (map s/get-spec)
                   (remove keyword?)
                   (every? st/spec?))))))))
