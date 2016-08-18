(ns spec-tools.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec :as s]
            [spec-tools.core :as stc]))

(s/def ::age (s/and stc/aInt #(> % 10)))
(s/def ::truth stc/aBool)
(s/def ::over-a-million (s/and stc/aLong #(> % 1000000)))
(s/def ::language (s/and stc/aKeyword #{:clojure :clojurescript}))

(deftest normal-mode-test
  (is (= (s/conform ::age "12") :clojure.spec/invalid))
  (is (= (s/conform ::truth "false") :clojure.spec/invalid))
  (is (= (s/conform ::over-a-million "1234567") :clojure.spec/invalid))
  (is (= (s/conform ::language "clojure") :clojure.spec/invalid)))

(deftest string-mode-test
  (binding [stc/*conform-mode* ::stc/string]
    (is (= (s/conform ::truth "false") false))
    (is (= (s/conform ::over-a-million "1234567") 1234567))
    (is (= (s/conform ::language "clojure") :clojure))
    (is (= (s/conform ::age "12") 12))))
