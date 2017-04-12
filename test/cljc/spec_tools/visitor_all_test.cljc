(ns spec-tools.visitor-all-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec :as s]
            [spec-tools.visitor :as visitor]
            [spec-tools.core :as st]))

;;
;; spec samples
;;

(s/def ::pred string?)

(s/def ::p1 string?)
(s/def ::p2 string?)
(s/def ::p3 string?)
(s/def ::keys (s/keys :req-un [(or ::p1 (and ::p2 ::p3))]))

(s/def ::p4 string?)
(s/def ::p5 string?)
(s/def ::or (s/or :p4 ::p4 :p5 ::p5))

(s/def ::p6 string?)
(s/def ::and (s/and ::p6 #(> % 18)))

(s/def ::p7 string?)
(s/def ::keys2 (s/keys :req-un [::p7]))
(s/def ::p8 string?)
(s/def ::keys3 (s/keys :req-un [::p8]))
(s/def ::merge (s/merge ::keys2 ::keys3))

(s/def ::p9 string?)
(s/def ::every (s/every ::p9))

(s/def ::p10 string?)
(s/def ::p11 string?)
(s/def ::every-kv (s/every-kv ::p10 ::p11))

(s/def ::p12 string?)
(s/def ::coll-of (s/coll-of ::p12))

(s/def ::p13 string?)
(s/def ::p14 string?)
(s/def ::map-of (s/map-of ::p13 ::p14))

(s/def ::p15 string?)
(s/def ::* (s/* ::p15))

(s/def ::p16 string?)
(s/def ::+ (s/* ::p16))

(s/def ::p17 string?)
(s/def ::? (s/* ::p17))

(s/def ::p18 string?)
(s/def ::p19 int?)
(s/def ::alt (s/alt :p18 ::p18 ::p19 ::p19))

(s/def ::p20 string?)
(s/def ::p21 string?)
(s/def ::cat (s/cat :p20 ::p20, :p21 ::p21))

;; BROKEN FORM
#_(s/def ::p22 string?)
#_(s/def ::& (s/& ::p22))

(s/def ::p23 string?)
(s/def ::p24 string?)
(s/def ::tuple (s/tuple ::p23 ::p24))

;; BROKEN FORM
#_(s/def ::p25 string?)
#_(s/def ::p26 string?)
#_(s/def ::p27 string?)
#_(s/def ::keys* (s/keys* :req-un [(or ::p25 (and ::p26 ::p27))]))

(s/def ::p28 string?)
(s/def ::nilable (s/nilable ::p28))

;;
;; everything together
;;

(s/def ::all
  (s/tuple
    ::pred
    ::keys
    ::or
    ::and
    ::merge
    ::every
    ::every-kv
    ::coll-of
    ::map-of
    ::*
    ::+
    ::?
    ::alt
    ::cat
    #_::&
    ::tuple
    #_::keys*
    ::nilable))

;;
;; tests
;;

(deftest visit-all-test
  (testing "visitor visits all specs"
    (is (= (set (keys (st/registry #"^spec-tools.visitor-all-test.*")))
           (->> (visitor/visit ::all (visitor/collect-specs))
                (keys)
                (set))))))
