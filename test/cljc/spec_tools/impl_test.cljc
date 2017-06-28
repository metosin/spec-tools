(ns spec-tools.impl-test
  (:require [clojure.test :refer [deftest testing is]]
            [spec-tools.impl :as impl]))

(deftest namespaced-name-test
  (is (= nil (impl/qualified-name nil)))
  (is (= "kikka" (impl/qualified-name :kikka)))
  (is (= "spec-tools.impl-test/kikka" (impl/qualified-name ::kikka))))
