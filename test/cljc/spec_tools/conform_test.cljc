(ns spec-tools.conform-test
  (:require [clojure.test :refer [deftest testing is]]
            [spec-tools.core :as st]
            [spec-tools.conform :as conform]))

(def _ ::irrelevant)

(deftest string->long
  (is (= 1 (conform/string->long _ "1")))
  (is (= 1 (conform/string->long _ 1)))
  (is (= st/+invalid+ (conform/string->long _ "abba"))))

(deftest string->double
  (is (= 1.0 (conform/string->double _ "1")))
  (is (= 1.0 (conform/string->double _ 1.0)))
  (is (= 1 (conform/string->double _ 1)))
  (is (= st/+invalid+ (conform/string->double _ "abba"))))

(deftest string->keyword
  (is (= :abba (conform/string->keyword _ "abba")))
  (is (= :abba (conform/string->keyword _ :abba))))

(deftest string->boolean
  (is (= true (conform/string->boolean _ "true")))
  (is (= false (conform/string->boolean _ "false")))
  (is (= st/+invalid+ (conform/string->boolean _ "abba"))))

(deftest string->uuid
  (is (= #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce" (conform/string->uuid _ "5f60751d-9bf7-4344-97ee-48643c9949ce")))
  (is (= #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce" (conform/string->uuid _ #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce")))
  (is (= st/+invalid+ (conform/string->uuid _ "abba"))))

(deftest string->date
  (is (= #inst "2014-02-18T18:25:37Z" (conform/string->date _ "2014-02-18T18:25:37Z")))
  (is (= #inst "2014-02-18T18:25:37Z" (conform/string->date _ #inst "2014-02-18T18:25:37Z")))
  (is (= st/+invalid+ (conform/string->date _ "abba"))))

(deftest string->symbol
  (is (= 'inc (conform/string->symbol _ "inc")))
  (is (= 'inc (conform/string->symbol _ 'inc))))

(deftest string->nil
  (is (= nil (conform/string->nil _ "")))
  (is (= nil (conform/string->nil _ nil))))
