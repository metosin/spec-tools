(ns spec-tools.swagger.spec-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [spec-tools.swagger.spec :as swagger-spec]))

(deftest swagger-test
  (testing "gen works"
    (is (= 10 (count (s/exercise ::swagger-spec/spec 10))))))
