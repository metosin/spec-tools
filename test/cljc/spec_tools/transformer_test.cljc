(ns spec-tools.transformer-test
  (:require [clojure.test :refer [deftest testing is]]
            [spec-tools.transformer :as stt]))

(def _ ::irrelevant)

(deftest string->long
  (is (= 1 (stt/string->long _ "1")))
  (is (= 1 (stt/string->long _ 1)))
  (is (= "abba" (stt/string->long _ "abba"))))

(deftest string->double
  (is (= 1.0 (stt/string->double _ "1")))
  (is (= 1.0 (stt/string->double _ 1.0)))
  (is (= 1 (stt/string->double _ 1)))
  (is (= "abba" (stt/string->double _ "abba"))))

(deftest string->keyword
  (is (= :abba (stt/string->keyword _ "abba")))
  (is (= :abba (stt/string->keyword _ :abba))))

(deftest string->boolean
  (is (= true (stt/string->boolean _ "true")))
  (is (= false (stt/string->boolean _ "false")))
  (is (= "abba" (stt/string->boolean _ "abba"))))

(deftest string->uuid
  (is (= #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce" (stt/string->uuid _ "5f60751d-9bf7-4344-97ee-48643c9949ce")))
  (is (= #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce" (stt/string->uuid _ #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce")))
  (is (= "abba" (stt/string->uuid _ "abba"))))

(deftest string->date
  (is (= #inst "2014-02-18T18:25:37Z" (stt/string->date _ "2014-02-18T18:25:37Z")))
  (is (= #inst "2018-04-27T00:00:00Z" (stt/string->date _ "2018-04-27")))
  (is (= #inst "2018-04-27T05:00:00Z" (stt/string->date _ "2018-04-27T08:00:00+03:00")))
  (is (= #inst "2014-02-18T18:25:37Z" (stt/string->date _ #inst "2014-02-18T18:25:37Z")))
  (is (= #inst "2018-04-27T00:00:00Z" (stt/string->date _ #inst "2018-04-27")))
  (is (= #inst "2018-04-27T05:00:00Z" (stt/string->date _ #inst "2018-04-27T08:00:00+03:00")))
  (is (= "abba" (stt/string->date _ "abba"))))

(deftest string->symbol
  (is (= 'inc (stt/string->symbol _ "inc")))
  (is (= 'inc (stt/string->symbol _ 'inc))))

(deftest string->nil
  (is (= nil (stt/string->nil _ "")))
  (is (= nil (stt/string->nil _ nil))))
