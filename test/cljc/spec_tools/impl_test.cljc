(ns spec-tools.impl-test
  (:require [clojure.test :refer [deftest testing is]]
            [spec-tools.impl :as impl]
            [clojure.spec.alpha :as s]))

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
