(ns spec-tools.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.spec :as s]
            [spec-tools.core :as st]
            [clojure.spec :as s]))

(s/def ::age (s/and st/x-integer? #(> % 10)))
(s/def ::over-a-million (s/and st/x-int? #(> % 1000000)))
(s/def ::lat st/x-double?)
(s/def ::language (s/and st/x-keyword? #{:clojure :clojurescript}))
(s/def ::truth st/x-boolean?)
(s/def ::uuid st/x-uuid?)
(s/def ::birthdate st/x-inst?)

(defmacro invalid? [value]
  `(= st/+error-code+ ~value))

(deftest spec-tools-conform-test
  (testing "normally"
    (is (invalid? (st/conform ::age "12")))
    (is (invalid? (st/conform ::over-a-million "1234567")))
    (is (invalid? (st/conform ::lat "23,1234")))
    (is (invalid? (st/conform ::language "clojure")))
    (is (invalid? (st/conform ::truth "false")))
    (is (invalid? (st/conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
    (is (invalid? (st/conform ::birthdate "2006-01-02T15:04:05.999999-07:00"))))

  (testing ":string mode"
    (let [conform (partial st/conform :string)]
      (is (= 12 (conform ::age "12")))
      (is (= 1234567 (conform ::over-a-million "1234567")))
      (is (= 23.1234 (conform ::lat "23.1234")))
      (is (= false (conform ::truth "false")))
      (is (= :clojure (conform ::language "clojure")))
      (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
             (conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
      (is (= #inst "2006-01-02T15:04:05.999999-07:00"
             (conform ::birthdate "2006-01-02T15:04:05.999999-07:00")))))

  (testing ":json mode"
    (let [conform (partial st/conform :json)]
      (is (invalid? (conform ::age "12")))
      (is (invalid? (conform ::over-a-million "1234567")))
      (is (invalid? (conform ::lat "23.1234")))
      (is (invalid? (conform ::truth "false")))
      (is (= :clojure (conform ::language "clojure")))
      (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
             (conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
      (is (= #inst "2006-01-02T15:04:05.999999-07:00"
             (conform ::birthdate "2006-01-02T15:04:05.999999-07:00"))))))
