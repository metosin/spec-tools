(ns spec-tools.decode-test
  (:require [clojure.test :refer [deftest testing is]]
            [spec-tools.decode :as std]))

(def _ ::irrelevant)

(deftest string->long
  (is (= 1 (std/string->long _ "1")))
  (is (= 1 (std/string->long _ 1)))
  (is (= "abba" (std/string->long _ "abba"))))

(deftest string->double
  (is (= 1.0 (std/string->double _ "1")))
  (is (= 1.0 (std/string->double _ 1.0)))
  (is (= 1 (std/string->double _ 1)))
  (is (= "abba" (std/string->double _ "abba"))))

(deftest string->keyword
  (is (= :abba (std/string->keyword _ "abba")))
  (is (= :abba (std/string->keyword _ :abba))))

(deftest string->boolean
  (is (= true (std/string->boolean _ "true")))
  (is (= false (std/string->boolean _ "false")))
  (is (= "abba" (std/string->boolean _ "abba"))))

(deftest string->uuid
  (is (= #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce" (std/string->uuid _ "5f60751d-9bf7-4344-97ee-48643c9949ce")))
  (is (= #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce" (std/string->uuid _ #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce")))
  (is (= "abba" (std/string->uuid _ "abba"))))

(deftest string->date
  (is (= #inst "2014-02-18T18:25:37Z" (std/string->date _ "2014-02-18T18:25:37Z")))
  (is (= #inst "2018-04-27T00:00:00Z" (std/string->date _ "2018-04-27")))
  (is (= #inst "2018-04-27T05:00:00Z" (std/string->date _ "2018-04-27T08:00:00+03:00")))
  (is (= #inst "2014-02-18T18:25:37Z" (std/string->date _ #inst "2014-02-18T18:25:37Z")))
  (is (= #inst "2018-04-27T00:00:00Z" (std/string->date _ #inst "2018-04-27")))
  (is (= #inst "2018-04-27T05:00:00Z" (std/string->date _ #inst "2018-04-27T08:00:00+03:00")))
  (is (= "abba" (std/string->date _ "abba"))))

(deftest string->symbol
  (is (= 'inc (std/string->symbol _ "inc")))
  (is (= 'inc (std/string->symbol _ 'inc))))

(deftest string->nil
  (is (= nil (std/string->nil _ "")))
  (is (= nil (std/string->nil _ nil))))
