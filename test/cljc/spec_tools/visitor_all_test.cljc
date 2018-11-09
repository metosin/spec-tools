(ns spec-tools.visitor-all-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [spec-tools.visitor :as visitor]))

;;
;; spec samples
;;

(s/def ::pred string?)

(s/def ::p1 string?)
(s/def ::p2 string?)
(s/def ::p3 string?)
(s/def ::p4 string?)
(s/def ::p5 string?)
(s/def ::p6 string?)
(s/def ::keys (s/keys
                :req [(or ::p1 (and ::p2 ::p3))]
                :opt [::p4]
                :req-un [::p5]
                :opt-un [::p6]))

(s/def ::p7 string?)
(s/def ::p8 string?)
(s/def ::or (s/or :a ::p7 :b ::p8))

(s/def ::p9 string?)
(s/def ::and (s/and ::p9 #(> % 18)))

(s/def ::p10 string?)
(s/def ::keys2 (s/keys :req-un [::p10]))
(s/def ::p11 string?)
(s/def ::keys3 (s/keys :req-un [::p11]))
(s/def ::merge (s/merge ::keys2 ::keys3))

(s/def ::p12 string?)
(s/def ::every (s/every ::p12))

(s/def ::p13 string?)
(s/def ::p14 string?)
(s/def ::every-kv (s/every-kv ::p13 ::p14))

(s/def ::p15 string?)
(s/def ::coll-of (s/coll-of ::p15))

(s/def ::p16 string?)
(s/def ::p17 string?)
(s/def ::map-of (s/map-of ::p16 ::p17))

(s/def ::p18 string?)
(s/def ::* (s/* ::p18))

(s/def ::p19 string?)
(s/def ::+ (s/* ::p19))

(s/def ::p20 string?)
(s/def ::? (s/* ::p20))

(s/def ::p21 string?)
(s/def ::p22 int?)
(s/def ::alt (s/alt :a ::p21 :b ::p22))

(s/def ::p23 string?)
(s/def ::p24 string?)
(s/def ::cat (s/cat :a ::p23, :b ::p24))

;; BROKEN FORM (http://dev.clojure.org/jira/browse/CLJ-2152)
(s/def ::p25 string?)
(s/def ::& (s/& ::p25))

(s/def ::p26 string?)
(s/def ::p27 string?)
(s/def ::tuple (s/tuple ::p26 ::p27))

;; BROKEN FORM (http://dev.clojure.org/jira/browse/CLJ-2152)
(s/def ::p28 string?)
(s/def ::p29 string?)
(s/def ::p30 string?)
(s/def ::p31 string?)
(s/def ::p32 string?)
(s/def ::p33 string?)
(s/def ::keys* (s/keys*
                 :req [(or ::p28 (and ::p29 ::p30))]
                 :opt [::p31]
                 :req-un [::p32]
                 :opt-un [::p33]))

(s/def ::p34 string?)
(s/def ::nilable (s/nilable ::p34))

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
    ::&
    ::tuple
    ::keys*
    ::nilable))

;;
;; tests
;;

(deftest nested-regexp-test
  (is (= #{::p1 ::p2 ::p3}
         (->> (visitor/visit
                (s/* (s/cat :prop ::p1 :val (s/alt :s ::p2 :b ::p3)))
                (visitor/spec-collector))
              (keys)
              (set)))))

(deftest visit-all-test
  (testing "visitor visits all specs but not s/& & s/keys* inner specs"
    (is (= #{::p1 ::p2 ::p3 ::p4 ::p5 ::p6 ::p7 ::p8 ::p9 ::p10
             ::p11 ::p12 ::p13 ::p14 ::p15 ::p16 ::p17 ::p18 ::p19 ::p20
             ::p21 ::p22 ::p23 ::p24 ::p25 ::p26 ::p27 #_::p28 #_::p29 #_::p30
             #_::p31 #_::p32 #_:p33 ::p34
             ::pred ::keys ::keys2 ::keys3
             ::or ::and ::merge ::every ::every-kv ::coll-of ::map-of
             ::* ::+ ::? ::alt ::cat ::& ::tuple ::keys* ::nilable ::all}

           (->> (visitor/visit ::all (visitor/spec-collector))
                (keys)
                (set))))))
