(ns spec-tools.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec :as s]
            [spec-tools.core :as st]))

(s/def ::age (s/and st/x-integer? #(> % 10)))
(s/def ::over-a-million (s/and st/x-int? #(> % 1000000)))
(s/def ::lat st/x-double?)
(s/def ::language (s/and st/x-keyword? #{:clojure :clojurescript}))
(s/def ::truth st/x-boolean?)

(defmacro invalid? [value]
  `(= st/+error-code+ ~value))

(deftest spec-confrom-test
  (testing "normal"
    (is (invalid? (s/conform ::age "12")))
    (is (invalid? (s/conform ::over-a-million "1234567")))
    (is (invalid? (s/conform ::lat "23,1234")))
    (is (invalid? (s/conform ::language "clojure")))
    (is (invalid? (s/conform ::truth "false"))))
  (testing "with *conform-mode*"
    (binding [st/*conform-mode* ::st/string]
      (is (= (s/conform ::age "12") 12))
      (is (= (s/conform ::over-a-million "1234567") 1234567))
      (is (= (s/conform ::lat "23.1234") 23.1234))
      (is (= (s/conform ::truth "false") false))
      (is (= (s/conform ::language "clojure") :clojure)))))

(deftest spec-tools-conform-test
  (testing "no-opts"
    (is (invalid? (st/conform ::age "12")))
    (is (invalid? (st/conform ::over-a-million "1234567")))
    (is (invalid? (st/conform ::lat "23,1234")))
    (is (invalid? (st/conform ::language "clojure")))
    (is (invalid? (st/conform ::truth "false"))))
  (testing "string-mode"
    (let [conform (partial st/conform ::st/string)]
      (is (= (conform ::age "12") 12))
      (is (= (conform ::over-a-million "1234567") 1234567))
      (is (= (conform ::lat "23.1234") 23.1234))
      (is (= (conform ::truth "false") false))
      (is (= (conform ::language "clojure") :clojure)))))
