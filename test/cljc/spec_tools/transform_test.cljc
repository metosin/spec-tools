(ns spec-tools.transform-test
  (:require [clojure.test :refer [deftest testing is]]
            [spec-tools.transform :as stt]))
#?(:clj
   (:import java.net.URI))

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

(deftest keyword->string
  (is (= "abba" (stt/keyword->string _ :abba)))
  (is (= "jabba/abba" (stt/keyword->string _ :jabba/abba)))
  (is (= "abba" (stt/keyword->string _ "abba"))))

(deftest string->boolean
  (is (= true (stt/string->boolean _ "true")))
  (is (= false (stt/string->boolean _ "false")))
  (is (= "abba" (stt/string->boolean _ "abba")))
  (is (= nil (stt/string->boolean _ nil)))
  (is (= 42 (stt/string->boolean _ 42))))

(deftest string->uuid
  (is (= #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce" (stt/string->uuid _ "5f60751d-9bf7-4344-97ee-48643c9949ce")))
  (is (= #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce" (stt/string->uuid _ #uuid"5f60751d-9bf7-4344-97ee-48643c9949ce")))
  (is (= "abba" (stt/string->uuid _ "abba"))))

(deftest string->uri
  #?(:clj (is (= (java.net.URI/create "https://github.com/metosin/spec-tools.git")
                 (stt/string->uri _ "https://github.com/metosin/spec-tools.git"))))
  #?(:clj (is (uri? (stt/string->uri _ "https://github.com/metosin/spec-tools.git"))))
  #?(:clj (is (uri? (stt/string->uri _ "git://github.com/metosin/spec-tools.git"))))
  #?(:clj (is (uri? (stt/string->uri _ "image:lisp.gif"))))
  #?(:clj (is (not (uri? (stt/string->uri _ "f://[2001:db8::7:::::::::::]")))))
  #?(:clj (is (uri? (stt/string->uri _ "ldap://[2001:db8::7]/c=GB?objectClass?one"))))
  #?(:clj (is (uri? (stt/string->uri _ "mailto:John.Doe@example.com"))))
  #?(:clj (is (uri? (stt/string->uri _ "tel:+1-816-555-1212"))))
  #?(:clj (is (uri? (stt/string->uri _ "urn:oasis:names:specification:docbook:dtd:xml:4.1.2"))))
  #?(:cljs (is (= "https://github.com/metosin/spec-tools.git")
               (stt/string->uri _ "https://github.com/metosin/spec-tools.git"))))

(deftest string->date
  (is (= #inst "2018-04-27T18:25:37Z" (stt/string->date _ "2018-04-27T18:25:37Z")))
  (is (= #inst "2018-04-27T00:00:00Z" (stt/string->date _ "2018-04-27")))
  (is (= #inst "2018-04-27T05:00:00Z" (stt/string->date _ "2018-04-27T08:00:00+03:00")))
  (is (= #inst "2018-04-27T18:25:37Z" (stt/string->date _ "2018-04-27T18:25:37.000Z")))
  (is (= #inst "2018-04-27T18:25:37Z" (stt/string->date _ "2018-04-27T18:25:37.000+0000")))
  (is (= #inst "2014-02-18T18:25:37Z" (stt/string->date _ #inst "2014-02-18T18:25:37Z")))
  (is (= #inst "2018-04-27T00:00:00Z" (stt/string->date _ #inst "2018-04-27")))
  (is (= #inst "2018-04-27T05:00:00Z" (stt/string->date _ #inst "2018-04-27T08:00:00+03:00")))
  (is (= "abba" (stt/string->date _ "abba"))))

(deftest date->string
  (is (= "2014-02-18T18:25:37.000Z" (stt/date->string _ #inst "2014-02-18T18:25:37Z")))
  (is (= "abba" (stt/date->string _ "abba"))))

(deftest string->symbol
  (is (= 'inc (stt/string->symbol _ "inc")))
  (is (= 'inc (stt/string->symbol _ 'inc))))

(deftest string->nil
  (is (= nil (stt/string->nil _ "")))
  (is (= nil (stt/string->nil _ nil))))

(deftest number->double
  #?(:clj (is (= 0.5 (stt/number->double _ 1/2))))
  (is (= 1.0 (stt/number->double _ 1)))
  (is (= "kikka" (stt/number->double _ "kikka"))))

(deftest any->string
  #?(:clj (is (= "1/2" (stt/any->string _ 1/2))))
  #?(:clj (is (= "https://github.com/metosin/spec-tools.git"
                 (stt/any->string _ (java.net.URI/create "https://github.com/metosin/spec-tools.git")))))
  (is (= "0.5" (stt/any->string _ 0.5)))
  (is (= nil (stt/any->string _ nil))))

(deftest any->any
  #?(:clj (is (= 1/2 (stt/any->any _ 1/2))))
  (is (= 0.5 (stt/any->any _ 0.5)))
  (is (= nil (stt/any->any _ nil))))
