(ns spec-tools.visitor-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.spec :as s]
            [spec-tools.visitor :refer [visit]]
            [clojure.set :as set]))

(s/def ::str string?)
(s/def ::int integer?)
(s/def ::map (s/keys :req [::str] :opt [::int]))

(defn collect [dispatch spec children] `[~dispatch ~@children])

(deftest test-visit
  (is (= (visit #{1 2 3} collect) [:spec-tools.visitor/set 1 3 2]))
  (is (= (visit ::map collect)
         '[clojure.spec/keys [clojure.core/string?] [clojure.core/integer?]])))
