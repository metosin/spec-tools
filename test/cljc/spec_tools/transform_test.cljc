(ns spec-tools.transform-test
  (:require [clojure.test :refer [deftest testing is]]
            [spec-tools.transform :as stt]
            #?(:clj [clojure.test.check.generators :as gen])
            #?(:clj [com.gfredericks.test.chuck.clojure-test :refer [checking]])
            #?@(:cljs [[goog.Uri]])))
#?(:clj
   (:import java.net.URI))

#?(:clj (def gen-bigdecimal
          (gen/fmap #(BigDecimal. %) (gen/double* {:infinite? false :NaN? false}))))

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

(defn uri [x]
  #?(:clj  (java.net.URI/create x)
     :cljs (goog.Uri.parse x)))

(defn equal-uris? [a b]
  #?(:clj  (= a b)
     :cljs (= (.toString a) (.toString b))))

(deftest string->uri
  (is (equal-uris? (uri "https://github.com/metosin/spec-tools.git")
                   (stt/string->uri _ "https://github.com/metosin/spec-tools.git")))
  (is (uri? (stt/string->uri _ "https://github.com/metosin/spec-tools.git")))
  (is (uri? (stt/string->uri _ "git://github.com/metosin/spec-tools.git")))
  (is (uri? (stt/string->uri _ "image:lisp.gif")))
  ; goog.Uri validation is pretty liberal, this does not fail in cljs
  #?(:clj (is (not (uri? (stt/string->uri _ "f://[2001:db8::7:::::::::::]")))))
  (is (uri? (stt/string->uri _ "ldap://[2001:db8::7]/c=GB?objectClass?one")))
  (is (uri? (stt/string->uri _ "mailto:John.Doe@example.com")))
  (is (uri? (stt/string->uri _ "tel:+1-816-555-1212")))
  (is (uri? (stt/string->uri _ "urn:oasis:names:specification:docbook:dtd:xml:4.1.2")))
  (is (not (uri? (stt/string->uri _ nil)))))

#?(:clj (deftest string->decimal
          (is (decimal? (stt/string->decimal _ "42")))
          (is (decimal? (stt/string->decimal _ "42.24")))
          (is (not (decimal? (stt/string->decimal _ nil))))
          (is (string? (stt/string->decimal _ "42.42M")))))

(deftest number->string
  (is (string? (stt/number->string _ 42.42)))
  (is (string? (stt/number->string _ 42)))
  (is (string? #?(:clj (stt/number->string _ (BigDecimal. 23123))
                  :cljs (stt/number->string _ js/Math.PI)))))

#?(:clj (deftest number->decimal
          (letfn [(num->decimal [n] ((stt/number-or-string-> stt/string->decimal) _ n))]
            (is (decimal? (num->decimal 42)))
            (is (decimal? (num->decimal 42.4224)))
            (is (decimal? (num->decimal (Float. 42.4223))))
            (is (decimal? (num->decimal (BigDecimal. 42.4222)))))))

#?(:clj (deftest properties-string->decimal
          (checking "Scale and Precision must be preserved" 200
            [original-bigdec gen-bigdecimal]
            (let [new-bigdec (stt/string->decimal _ (str original-bigdec))]
              (is (= (.scale original-bigdec) (.scale new-bigdec)))
              (is (= (.precision original-bigdec) (.precision new-bigdec)))))))

#?(:clj (deftest string->ratio
          (is (ratio? (stt/string->ratio _ "1/2")))
          (is (string? (stt/string->ratio _ "0.5")))
          (is (ratio? (stt/string->ratio _ "4242424242424421424242422/14242422121212121212")))
          (is (not (ratio? (stt/string->ratio _ "1/1"))))))

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
