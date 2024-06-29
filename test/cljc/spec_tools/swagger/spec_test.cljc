(ns spec-tools.swagger.spec-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [spec-tools.swagger.spec :as swagger-spec]
            [spec-tools.swagger.spec3 :as open-api-spec]))

(deftest swagger-test
  (testing "gen works"
    (is (= 10 (count (s/exercise ::swagger-spec/swagger 10))))))

(deftest open-api-test
  (testing "gen works"
    (is (= 10 (count (s/exercise ::open-api-spec/open-api 10))))))
