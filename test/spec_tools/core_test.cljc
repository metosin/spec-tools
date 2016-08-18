(ns spec-tools.core-test
  (:require #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :as test :refer-macros [deftest testing is]])))

(deftest a-test
  (is (= 1 1)))

