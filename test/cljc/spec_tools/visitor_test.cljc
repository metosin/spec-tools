(ns spec-tools.visitor-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.spec :as s]
            [spec-tools.visitor :refer [visit]]))

(s/def ::str string?)
(s/def ::int integer?)
(s/def ::map (s/keys :req [::str] :opt [::int]))

(defn collect [dispatch spec children] `[~dispatch ~@children])

(deftest test-visit
  (is (= (visit #{1 2 3} collect) [:spec-tools.visitor/set 1 3 2]))
  (is (= (visit ::map collect)
         '[clojure.spec/keys [clojure.core/string?] [clojure.core/integer?]])))

(s/def ::age number?)
(s/def ::first-name string?)
(s/def ::last-name string?)
(s/def ::name (s/keys :req-un [::first-name ::last-name]))
(s/def ::user (s/keys :req-un [::name ::age]))

; https://gist.github.com/shafeeq/c2ded8e71579a26e44c2191536e01c0d
(deftest recursive-visit
  (let [specs (atom {})
        collect (fn [_ spec _]
                  (swap! specs assoc spec (s/form (s/get-spec spec))))]
    (is (= (visit ::user collect)
           {::age (s/form ::age)
            ::first-name (s/form ::first-name)
            ::last-name (s/form ::last-name)
            ::name (s/form ::name)
            ::user (s/form ::user)}))))
