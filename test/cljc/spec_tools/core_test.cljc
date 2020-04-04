(ns spec-tools.core-test
  (:require [clojure.test :refer [deftest testing is are run-tests]]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [spec-tools.core :as st]
            [spec-tools.spec :as spec]
            [spec-tools.parse :as info]
            [spec-tools.form :as form]
            [spec-tools.transform :as transform]
            [spec-tools.parse :as parse]
            [#?(:clj  clojure.spec.gen.alpha
                :cljs cljs.spec.gen.alpha) :as gen]
            [spec-tools.transform :as stt]))

(s/def ::age (s/and spec/integer? #(> % 10)))
(s/def ::over-a-million (s/and spec/int? #(> % 1000000)))
(s/def ::lat spec/double?)
(s/def ::language (s/and spec/keyword? #{:clojure :clojurescript}))
(s/def ::truth spec/boolean?)
(s/def ::uuid spec/uuid?)
(s/def ::birthdate spec/inst?)

(s/def ::a spec/int?)
(s/def ::b ::a)

(s/def ::string string?)
(s/def ::alias ::string)

(deftest get-spec-test
  (is (= spec/int? (st/get-spec ::a)))
  (is (= spec/int? (st/get-spec ::b))))

(deftest coerce-test
  (is (= spec/boolean? (st/coerce-spec ::truth)))
  (is (= spec/boolean? (st/coerce-spec spec/boolean?)))
  (is (thrown? #?(:clj Exception, :cljs js/Error) (st/coerce-spec ::INVALID))))

(s/def ::regex (s/or :int spec/int? :string string?))
(s/def ::spec (s/spec int?))

(deftest spec-name-test
  (is (= nil (st/spec-name #{1 2})))
  (is (= :kikka (st/spec-name :kikka)))
  (is (= ::regex (st/spec-name (s/get-spec ::regex))))
  (is (= ::spec (st/spec-name (s/get-spec ::spec))))
  (is (= ::overridden (st/spec-name
                        (st/spec
                          {:spec (s/get-spec ::spec)
                           :name ::overridden})))))

(deftest spec-description-test
  (is (= nil (st/spec-description #{1 2})))
  (is (= "description" (st/spec-description
                         (st/spec
                           {:spec (s/get-spec ::spec)
                            :description "description"})))))

(deftest spec?-test
  (testing "spec"
    (let [spec (s/spec integer?)]
      (is (= spec (s/spec? spec)))
      (is (nil? (st/spec? spec)))))
  (testing "Spec"
    (let [spec (st/spec integer?)]
      (is (= spec (s/spec? spec)))
      (is (= spec (st/spec? spec))))))

(deftest spec-test
  (let [my-integer? (st/spec integer?)]

    (testing "creation"
      (testing "succeeds"
        (is (= spec/integer?
               (st/spec integer?)
               (st/spec {:spec integer?})
               (st/spec {:spec integer?, :type :long})
               (st/spec integer? {:type :long})
               (st/create-spec {:spec integer?})
               (st/create-spec {:spec integer? :type :long})
               (st/create-spec {:spec integer?, :form `integer?})
               (st/create-spec {:spec integer?, :form `integer?, :type :long}))))
      (testing "fails"
        (is (thrown? #?(:clj AssertionError, :cljs js/Error)
                     (st/create-spec {:spec :un-existent/keyword-spec}))))

      (testing "::s/name is retained"
        (is (= ::age (::s/name (meta (st/create-spec {:spec ::age}))))))

      (testing "anonymous functions"

        (testing ":form default to ::s/unknown"
          (let [spec (st/create-spec
                       {:name "positive?"
                        :spec (fn [x] (pos? x))})]
            (is (st/spec? spec))
            (is (= (:form spec) ::s/unknown))))

        (testing ":form and :type can be provided"
          (let [spec (st/create-spec
                       {:name "positive?"
                        :spec (fn [x] (pos? x))
                        :type :long
                        :form `(fn [x] (pos? x))})]
            (is (st/spec? spec))))))

    (testing "registered specs are inlined"
      (is (= (s/get-spec ::string)
             (:spec (st/spec ::string))))
      (is (= (s/form ::string)
             (:form (st/spec ::string)))))

    (testing "nested specs are inlined"
      (is (= (s/get-spec ::string)
             (:spec (st/spec ::alias))))
      (is (= (s/form ::string)
             (:form (st/spec ::alias)))))

    (testing "forms"
      (are [spec form]
        (= form (s/form spec))

        (st/spec integer?)
        `(spec-tools.core/spec
           {:spec integer?
            :type :long
            :leaf? true})

        (st/spec #{pos? neg?})
        `(spec-tools.core/spec
           {:spec #{neg? pos?}
            :type nil
            :leaf? true})

        (st/spec ::string)
        `(spec-tools.core/spec
           {:spec string?
            :type :string
            :leaf? true})

        (st/spec ::lat)
        `(spec-tools.core/spec
           {:spec (spec-tools.core/spec
                    {:spec double?
                     :type :double
                     :leaf? true})
            :type :double
            :leaf? true})

        (st/spec (fn [x] (> x 10)))
        `(spec-tools.core/spec
           {:spec (clojure.core/fn [~'x] (> ~'x 10))
            :type nil
            :leaf? true})

        (st/spec #(> % 10))
        `(spec-tools.core/spec
           {:spec (clojure.core/fn [~'%] (> ~'% 10))
            :type nil
            :leaf? true})))

    (testing "wrapped predicate work as a predicate"
      (is (true? (my-integer? 1)))
      (is (false? (my-integer? "1")))
      (testing "ifn's work too"
        (let [spec (st/spec #{1 2 3})]
          (is (= 1 (spec 1)))
          (is (= nil (spec "1"))))))

    (testing "wrapped spec does not work as a predicate"
      (let [my-spec (st/spec (s/keys :req [::age]))]
        (is (thrown? #?(:clj Exception, :cljs js/Error) (my-spec {::age 20})))))

    (testing "adding info"
      (let [info {:description "desc"
                  :example 123}]
        (is (= info (:info (assoc my-integer? :info info))))))

    (testing "are specs"
      (is (true? (s/valid? my-integer? 1)))
      (is (false? (s/valid? my-integer? "1")))

      (testing "fully qualifed predicate symbol is returned with s/form"
        (is (= ['spec-tools.core/spec
                {:spec #?(:clj  'clojure.core/integer?
                          :cljs 'cljs.core/integer?)
                 :type :long
                 :leaf? true}] (s/form my-integer?)))
        (is (= ['spec {:spec 'integer? :type :long :leaf? true}] (s/describe my-integer?))))

      (testing "type resolution"
        (is (= (st/spec integer?)
               (st/spec integer? {:type :long}))))

      (testing "serialization"
        (let [spec (st/spec {:spec integer? :description "cool", :type ::integer})]
          (is (= `(st/spec {:spec integer? :description "cool", :type ::integer :leaf? true})
                 (s/form spec)
                 (st/deserialize (st/serialize spec))))))

      (testing "gen"
        (is (seq? (s/exercise my-integer?)))
        (is (every? #{:kikka :kukka} (-> spec/keyword?
                                         (s/with-gen #(s/gen #{:kikka :kukka}))
                                         (s/exercise)
                                         (->> (map first)))))

        (is (-> (st/spec {:spec (s/+ (s/tuple #{:a :b :c} integer?))}) (s/gen) (gen/generate)))))))

(deftest reason-test
  (let [expected-problem {:path [] :pred `pos-int?, :val -1, :via [], :in []}]
    (testing "explain-data"
      (let [spec (st/spec pos-int?)]
        (is (= #?(:clj  #:clojure.spec.alpha{:problems [expected-problem]
                                             :spec spec
                                             :value -1}
                  :cljs #:cljs.spec.alpha{:problems [expected-problem]
                                          :spec spec
                                          :value -1})
               (st/explain-data spec -1)
               (s/explain-data spec -1)))))
    (testing "explain-data with reason"
      (let [spec (st/spec pos-int? {:reason "positive"})]
        (is (= #?(:clj  #:clojure.spec.alpha{:problems [(assoc expected-problem :reason "positive")]
                                             :spec spec
                                             :value -1}
                  :cljs #:cljs.spec.alpha{:problems [(assoc expected-problem :reason "positive")]
                                          :spec spec
                                          :value -1})
               (st/explain-data spec -1)
               (s/explain-data spec -1)))))))

(deftest spec-tools-transform-test
  (testing "in default mode"
    (testing "nothing is conformed"
      (is (s/invalid? (st/conform ::age "12")))
      (is (s/invalid? (st/conform ::over-a-million "1234567")))
      (is (s/invalid? (st/conform ::lat "23.1234")))
      (is (s/invalid? (st/conform ::language "clojure")))
      (is (s/invalid? (st/conform ::truth "false")))
      (is (s/invalid? (st/conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
      (is (s/invalid? (st/conform ::birthdate "2014-02-18T18:25:37.456Z")))
      (is (s/invalid? (st/conform ::birthdate "2014-02-18T18:25:37Z")))))

  (testing "string-transformer"
    (let [conform #(st/conform %1 %2 st/string-transformer)]
      (testing "everything gets conformed"
        (is (= 12 (conform ::age "12")))
        (is (= 1234567 (conform ::over-a-million "1234567")))
        (is (= 23.1234 (conform ::lat "23.1234")))
        (is (= false (conform ::truth "false")))
        (is (= :clojure (conform ::language "clojure")))
        (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
               (conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
        (is (= #inst "2014-02-18T18:25:37.456Z"
               (conform ::birthdate "2014-02-18T18:25:37.456Z")))
        (is (= #inst "2014-02-18T18:25:37Z"
               (conform ::birthdate "2014-02-18T18:25:37Z"))))))

  (testing "json-transformer"
    (let [conform #(st/conform %1 %2 st/json-transformer)]
      (testing "some are not conformed"
        (is (s/invalid? (conform ::age "12")))
        (is (s/invalid? (conform ::over-a-million "1234567")))
        (is (s/invalid? (conform ::lat "23.1234")))
        (is (s/invalid? (conform ::truth "false"))))
      (testing "some are conformed"
        (is (= :clojure (conform ::language "clojure")))
        (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
               (conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
        (is (= #inst "2014-02-18T18:25:37.456Z"
               (conform ::birthdate "2014-02-18T18:25:37.456Z")))
        (is (= #inst "2014-02-18T18:25:37Z"
               (conform ::birthdate "2014-02-18T18:25:37Z")))))))

(s/def ::my-spec
  (st/spec
    {:spec #(and (simple-keyword? %) (-> % name str/lower-case keyword (= %)))
     :description "a lowercase keyword, encoded in uppercase in string-mode"
     :decode/string #(-> %2 name str/lower-case keyword)
     :encode/string #(-> %2 name str/upper-case)}))
(s/def ::my-spec-map (s/keys :req [::my-spec]))

(s/def ::my-type (st/spec keyword?))
(s/def ::my-type-map (s/keys :req [::my-type]))

(deftest encode-decode-test
  (let [my-type-transformer (st/type-transformer {:name ::my})]
    (testing "spec-driven encode & decode"
      (let [invalid {::my-spec "kikka"}
            encoded {::my-spec "KIKKA"}
            decoded {::my-spec :kikka}]
        (testing "without transformer"
          (testing "decode works just like s/conform"
            (is (= ::s/invalid (st/decode ::my-spec-map encoded)))
            (is (= decoded (st/decode ::my-spec-map decoded))))
          (testing "encode fails if no encoder is defined"
            (is (= ::s/invalid (st/encode ::my-spec-map invalid nil)))))
        (testing "with transformer"
          (testing "decoding is applied before validation, if defined"
            (is (= ::s/invalid (st/decode ::my-spec-map encoded my-type-transformer)))
            (is (= decoded (st/decode ::my-spec-map decoded st/string-transformer)))
            (is (= decoded (st/decode ::my-spec-map encoded st/string-transformer))))
          (testing "encoding is applied without validation, if defined"
            (is (= decoded (st/encode ::my-spec-map decoded my-type-transformer)))
            (is (= encoded (st/encode ::my-spec-map encoded st/string-transformer)))
            (is (= encoded (st/encode ::my-spec-map decoded st/string-transformer)))))))
    (testing "type-driven encode & decode"
      (let [invalid {::my-type 123456789}
            encoded {::my-type "kikka/kukka"}
            decoded {::my-type :kikka/kukka}]
        (testing "without transformer"
          (testing "decode works just like s/conform"
            (is (= ::s/invalid (st/decode ::my-type-map encoded)))
            (is (= decoded (st/decode ::my-type-map decoded))))
          (testing "encode fails if no encoder is defined"
            (is (= ::s/invalid (st/encode ::my-type-map invalid nil)))))
        (testing "with transformer"
          (testing "decoding is applied before validation, if defined"
            (is (= ::s/invalid (st/decode ::my-type-map encoded my-type-transformer)))
            (is (= decoded (st/decode ::my-type-map decoded st/string-transformer)))
            (is (= decoded (st/decode ::my-type-map encoded st/string-transformer))))
          (testing "encoding is applied without validation, if defined"
            (is (= decoded (st/encode ::my-type-map decoded my-type-transformer)))
            (is (= encoded (st/encode ::my-type-map encoded st/string-transformer)))
            (is (= encoded (st/encode ::my-type-map decoded st/string-transformer)))))))
    (testing "roundtrip"
      (is (= :kikka (as-> "KikKa" $
                          (st/decode ::my-spec $ st/string-transformer))))
      (is (= "KIKKA" (as-> "KikKa" $
                           (st/decode ::my-spec $ st/string-transformer)
                           (st/encode ::my-spec $ st/string-transformer))))
      (is (= :kikka (as-> "KikKa" $
                          (st/decode ::my-spec $ st/string-transformer)
                          (st/encode ::my-spec $ st/string-transformer)
                          (st/decode ::my-spec $ st/string-transformer)))))
    (testing "encode and decode also unform"
      (is (= "1" (st/encode ::regex 1 st/string-transformer)))
      (is (= 1 (st/decode ::regex "1" st/string-transformer))))))

(deftest late-bind-transformers-on-decode
  (let [times (atom 0)
        spec (st/spec
               {:spec int?
                :decode/string (fn [_ value] (swap! times inc) value)})]
    (st/decode spec 1 st/string-transformer)
    (is (= 1 @times))))

(s/def ::c1 int?)
(s/def ::c2 keyword?)
(s/def ::c3 symbol?)

(deftest composing-type-transformers
  (is (= (st/-options st/json-transformer)
         (st/-options (st/type-transformer st/json-transformer))
         (st/-options (st/type-transformer st/json-transformer st/json-transformer))))
  (is (= {:c3 'kikka, :c2 :abba}
         (st/coerce
           (s/keys :req-un [::c3 ::c2])
           {:c3 "kikka", :c2 "abba"}
           (st/type-transformer st/json-transformer st/string-transformer))
         (st/coerce
           (s/keys :req-un [::c3 ::c2])
           {:c3 "kikka", :c2 "abba"}
           (st/type-transformer st/string-transformer st/json-transformer))))
  (is (= :bumblebee (st/-name (st/type-transformer
                                st/string-transformer
                                st/json-transformer
                                {:name :bumblebee})))))

(deftest coercion-test
  (testing "predicates"
    (is (= 1 (st/coerce int? "1" st/string-transformer)))
    (is (= "1" (st/coerce int? "1" st/json-transformer)))
    (is (= :user/kikka (st/coerce keyword? "user/kikka" st/string-transformer))))
  (testing "s/and"
    (is (= 1 (st/coerce (s/and int?) "1" st/string-transformer)))
    (is (= :1 (st/coerce (s/and keyword?) "1" st/string-transformer)))
    (is (= 1 (st/coerce (s/and int? keyword?) "1" st/string-transformer)))
    (is (= 1 (st/coerce (s/and int? #{1 2}) "1" st/string-transformer)))
    (is (= 1 (st/coerce (s/and keyword? int?) "1" st/string-transformer)))
    (is (= [1] (st/coerce (s/and (s/coll-of int?)) ["1"] st/string-transformer)))
    (is (= [1] (st/coerce (s/and (s/coll-of int?) (comp boolean not-empty)) ["1"] st/string-transformer))))
  (testing "s/or"
    (is (= 1 (st/coerce (s/or :int int? :keyword keyword?) "1" st/string-transformer)))
    (is (= 1 (st/coerce (s/and :int int? :enum #{1 2}) "1" st/string-transformer)))
    (is (= :1 (st/coerce (s/or :keyword keyword? :int int?) "1" st/string-transformer))))
  (testing "s/coll-of"
    (is (= #{1 2 3} (st/coerce (s/coll-of int? :into #{}) ["1" 2 "3"] st/string-transformer)))
    (is (= #{"1" 2 "3"} (st/coerce (s/coll-of #{1 2} :into #{}) ["1" 2 "3"] st/string-transformer)))
    (is (= #{"1" 2 "3"} (st/coerce (s/coll-of int? :into #{}) ["1" 2 "3"] st/json-transformer)))
    (is (= [:1 2 :3] (st/coerce (s/coll-of keyword?) ["1" 2 "3"] st/string-transformer)))
    (is (= '(:1 2 :3) (st/coerce (s/coll-of keyword?) '("1" 2 "3") st/string-transformer)))
    (is (= '(:1 2 :3) (st/coerce (s/coll-of keyword?) (seq '("1" 2 "3")) st/string-transformer)))
    (is (= '(:1 2 :3) (st/coerce (s/coll-of keyword?) (lazy-seq '("1" 2 "3")) st/string-transformer)))
    (is (= ::invalid (st/coerce (s/coll-of keyword?) ::invalid st/string-transformer))))
  (testing "s/keys"
    (is (= {:c1 1, ::c2 :kikka} (st/coerce (s/keys :req-un [::c1]) {:c1 "1", ::c2 "kikka"} st/string-transformer)))
    (is (= {:c1 1, ::c2 :kikka} (st/coerce (s/keys :req-un [(and ::c1 ::c2)]) {:c1 "1", ::c2 "kikka"} st/string-transformer)))
    (is (= {:c1 "1", ::c2 :kikka} (st/coerce (s/keys :req-un [::c1]) {:c1 "1", ::c2 "kikka"} st/json-transformer)))
    (is (= ::invalid (st/coerce (s/keys :req-un [::c1]) ::invalid st/json-transformer))))
  (testing "s/map-of"
    (is (= {1 :abba, 2 :jabba} (st/coerce (s/map-of int? keyword?) {"1" "abba", "2" "jabba"} st/string-transformer)))
    (is (= {"1" :abba, "2" :jabba} (st/coerce (s/map-of int? keyword?) {"1" "abba", "2" "jabba"} st/json-transformer)))
    (is (= ::invalid (st/coerce (s/map-of int? keyword?) ::invalid st/json-transformer))))
  (testing "s/nillable"
    (is (= 1 (st/coerce (s/nilable int?) "1" st/string-transformer)))
    (is (= nil (st/coerce (s/nilable int?) nil st/string-transformer))))
  (testing "s/every"
    (is (= [1] (st/coerce (s/every int?) ["1"] st/string-transformer))))
  (testing "s/tuple"
    (is (= [1] (st/coerce (s/tuple int?) ["1"] st/string-transformer)))
    (is (= [1 :kikka] (st/coerce (s/tuple int? keyword?) ["1" "kikka"] st/string-transformer)))
    (is (= [:kikka 1] (st/coerce (s/tuple keyword? int?) ["kikka" "1"] st/string-transformer)))
    (is (= "1" (st/coerce (s/tuple keyword? int?) "1" st/string-transformer)))
    (is (= [:kikka 1 "2"] (st/coerce (s/tuple keyword? int?) ["kikka" "1" "2"] st/string-transformer)))
    (is (= [:kikka 1] (st/coerce (s/tuple keyword? int?) ["kikka" "1" "2"] (st/type-transformer
                                                                             st/string-transformer
                                                                             st/strip-extra-values-transformer)))))
  (testing "referenced specs, #165"
    (s/def ::pos? (st/spec {:spec (partial pos?), :decode/string transform/string->long}))
    (is (= 1 (st/coerce (s/and ::pos?) "1" st/string-transformer)))
    (is (= 1 (st/coerce (s/or :default ::pos?) "1" st/string-transformer)))
    (is (= [1 2 3 :4] (st/coerce (s/coll-of ::pos?) ["1" "2" "3" :4] st/string-transformer)))
    (is (= {1 :2, :3 4} (st/coerce (s/map-of ::pos? ::pos?) {"1" :2, :3 "4"} st/string-transformer)))
    (is (= [1 :2 3 "4"] (st/coerce (s/tuple ::pos? keyword? ::pos?) ["1" "2" "3" "4"] st/string-transformer)))
    (is (= 1 (st/coerce (s/nilable ::pos?) "1" st/string-transformer))))
  (testing "composed"
    (let [spec (s/nilable
                 (s/nilable
                   (s/map-of
                     keyword?
                     (s/or :keys (s/keys :req-un [::c1])
                           :ks (s/coll-of (s/and int?) :into #{})))))
          value {"keys" {:c1 "1" ::c2 "kikka"}
                 "keys2" {:c1 true}
                 "ints" [1 "1" "invalid" "3"]}]
      (is (= {:keys {:c1 1 ::c2 :kikka}
              :keys2 {:c1 true}
              :ints #{1 "invalid" 3}}
             (st/coerce spec value st/string-transformer)))
      (is (= {:keys {:c1 "1" ::c2 :kikka}
              :keys2 {:c1 true}
              :ints #{1 "1" "invalid" "3"}}
             (st/coerce spec value st/json-transformer)))))
  (testing "from keywords"
    (doseq [transformer [st/string-transformer st/json-transformer]]
      (are [spec value coerced]
        (is (= {coerced coerced} (st/coerce spec {value value} transformer)))

        (s/map-of keyword? keyword?) :kikka :kikka
        (s/map-of symbol? symbol?) :kikka 'kikka
        (s/map-of int? int?) :1 1
        (s/map-of double? double?) :1.0 1.0
        (s/map-of boolean? boolean?) :true true
        (s/map-of string? string?) :kikka "kikka"

        (s/map-of uuid? uuid?)
        (keyword "90b1f607-be38-46f4-ba6b-afd663914c22")
        #uuid "90b1f607-be38-46f4-ba6b-afd663914c22"

        (s/map-of inst? inst?)
        (keyword "2019-02-23T08:13:21.400-00:00")
        #inst "2019-02-23T08:13:21.400-00:00")))

  #?(:clj
     (testing "multi-specs"
       (s/def ::group (s/and keyword? #{:a :b}))
       (defmulti multi-test :group)
       (defmethod multi-test :a [_] (s/keys :req-un [::group ::lat]))
       (defmethod multi-test :b [_] (s/keys :req-un [::group ::language]))
       (s/def ::multi-test (s/multi-spec multi-test :group))

       (is (= {:group :a
               :lat   12.0}
              (st/coerce ::multi-test {:group "a"
                                       :lat   "12"}
                         st/string-transformer)))
       (is (= {:group    :b
               :language :clojure}
              (st/coerce ::multi-test {:group    "b"
                                       :language "clojure"}
                         st/string-transformer))))))

(deftest conform!-test
  (testing "suceess"
    (is (= 12 (st/conform! ::age "12" st/string-transformer))))
  (testing "failing"
    (is (thrown? #?(:clj Exception, :cljs js/Error) (st/conform! ::age "12")))
    (try
      (st/conform! ::age "12")
      (catch #?(:clj Exception, :cljs js/Error) e
        (let [data (ex-data e)]
          (is (= {:type ::st/conform
                  :problems [{:path [], :pred `integer?, :val "12", :via [::age], :in []}]
                  :spec :spec-tools.core-test/age
                  :value "12"}
                 data)))))))

(deftest explain-tests
  (testing "without transformer"
    (let [expected-problem {:path [], :pred `int?, :val "12", :via [], :in []}]
      (is (s/invalid? (st/conform spec/int? "12")))
      (is (= #?(:clj  #:clojure.spec.alpha{:problems [expected-problem]
                                           :spec spec/int?
                                           :value "12"}
                :cljs #:cljs.spec.alpha{:problems [expected-problem]
                                        :spec spec/int?
                                        :value "12"})
             (st/explain-data spec/int? "12")))
      (is (any? (with-out-str (st/explain spec/int? "12"))))
      (is (any? (with-out-str (st/explain spec/int? "12" nil))))))
  (testing "with transformer"
    (is (= 12 (st/conform spec/int? "12" st/string-transformer)))
    (is (= nil (st/explain-data spec/int? "12" st/string-transformer)))
    (is (= "Success!\n"
           (with-out-str (st/explain spec/int? "12" st/string-transformer))))))

(deftest conform-unform-explain-tests
  (testing "specs"
    (let [spec (st/spec (s/or :int spec/int? :bool spec/boolean?))
          value "1"]
      (is (s/invalid? (st/conform spec value)))
      (is (= [:int 1] (st/conform spec value st/string-transformer)))
      (is (= 1 (s/unform spec (st/conform spec value st/string-transformer))))
      (is (= nil (st/explain-data spec value st/string-transformer)))))
  (testing "regexs"
    (let [spec (st/spec (s/* (s/cat :key spec/keyword? :val spec/int?)))
          value [:a "1" :b "2"]]
      (is (s/invalid? (st/conform spec value)))
      (is (= [{:key :a, :val 1} {:key :b, :val 2}] (st/conform spec value st/string-transformer)))
      (is (= [:a 1 :b 2] (s/unform spec (st/conform spec value st/string-transformer))))
      (is (= nil (st/explain-data spec value st/string-transformer))))))

(s/def ::height integer?)
(s/def ::weight integer?)
(s/def ::person (s/keys :req-un [::height ::weight]))
(s/def ::person-spec (st/spec (s/keys :req-un [::height ::weight])))
(s/def ::persons (s/coll-of ::person :into []))

(deftest map-specs-test
  (let [person {:height 200, :weight 80, :age 36}]

    (testing "conform"
      (is (= {:height 200, :weight 80, :age 36}
             (s/conform ::person person)
             (s/conform ::person-spec person)
             (st/conform ::person person)
             (st/conform ::person-spec person))))

    (testing "stripping extra keys"
      (is (= {:height 200, :weight 80}
             ;; via conform
             (st/conform ::person person st/strip-extra-keys-transformer)
             (st/conform ::person-spec person st/strip-extra-keys-transformer)
             ;; via coerce
             (st/coerce ::person person st/strip-extra-keys-transformer)
             (st/coerce ::person-spec person st/strip-extra-keys-transformer)
             ;; via decode
             (st/decode ::person person st/strip-extra-keys-transformer)
             (st/decode ::person-spec person st/strip-extra-keys-transformer)
             ;; simplified (via coerce)
             (st/select-spec ::person person)
             (st/select-spec ::person-spec person))))

    (testing "deeply nested"
      (is (= {:persons [{:weight 80, :height 200}]}
             (st/select-spec
               (s/keys :req-un [::persons])
               {:TOO "MUCH"
                :persons [{:INFOR "MATION"
                           :height 200
                           :weight 80}]}))))

    (testing "failing on extra keys"
      (is (not (s/invalid? (st/conform ::person
                                       {:height 200, :weight 80}
                                       st/fail-on-extra-keys-transformer))))
      (is (s/invalid? (st/conform ::person person st/fail-on-extra-keys-transformer)))
      (is (s/invalid? (st/conform ::person-spec person st/fail-on-extra-keys-transformer))))

    (testing "explain works too"
      (is (is (seq (st/explain-data ::person person st/fail-on-extra-keys-transformer)))))))

(s/def ::human (st/spec (s/keys :req-un [::height ::weight]) {:type ::human}))

(defn bmi [{:keys [height weight]}]
  (let [h (/ height 100)]
    (double (/ weight (* h h)))))

(deftest custom-map-specs-test
  (let [person {:height 200, :weight 80}
        bmi-conformer (fn [_ human]
                        (assoc human :bmi (bmi human)))]

    (testing "conform"
      (is (= {:height 200, :weight 80}
             (s/conform ::human person)
             (st/conform ::human person))))

    (testing "bmi-transformer"
      (is (= {:height 200, :weight 80, :bmi 20.0}
             (st/conform ::human person (st/type-transformer
                                          {:decoders {::human bmi-conformer}})))))))

(deftest unform-test
  (let [unform-conform #(s/unform %1 (st/conform %1 %2 st/string-transformer))]
    (testing "conformed values can be unformed"
      (is (= 12 (unform-conform ::age "12")))
      (is (= 1234567 (unform-conform ::age "1234567")))
      (is (= 23.1234 (unform-conform ::lat "23.1234")))
      (is (= false (unform-conform ::truth "false")))
      (is (= :clojure (unform-conform ::language "clojure")))
      (is (= #uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e"
             (unform-conform ::uuid "07dbf30f-c99e-4e5d-b76e-5cbdac3b381e")))
      (is (= #inst "2014-02-18T18:25:37.456Z"
             (unform-conform ::birthdate "2014-02-18T18:25:37.456Z")))
      (is (= #inst "2014-02-18T18:25:37.456Z"
             (unform-conform ::birthdate "2014-02-18T18:25:37.456Z"))))))

(deftest extending-test
  (let [my-transformer (st/type-transformer
                         {:decoders
                          (assoc
                            stt/string-type-decoders
                            :keyword
                            (fn [_ value]
                              (-> value
                                  str/upper-case
                                  str/reverse
                                  keyword)))})]
    (testing "string-transformer"
      (is (= :kikka (st/conform spec/keyword? "kikka" st/string-transformer))))
    (testing "my-transformer"
      (is (= :AKKIK (st/conform spec/keyword? "kikka" my-transformer))))))

(s/def ::collect-info-spec (s/keys
                             :req [::age]
                             :req-un [::lat]
                             :opt [::truth]
                             :opt-un [::uuid]))

(deftest collect-info-test
  (testing "doesn't fail with ::s/unknown"
    (is (= nil
           (info/parse-spec
             ::s/unknown))))

  (testing "all keys types are extracted"
    (is (= {:type :map
            ::parse/key->spec {:lat ::lat
                               ::age ::age
                               ::truth ::truth
                               :uuid ::uuid}

            ::parse/keys #{::age :lat ::truth :uuid}
            ::parse/keys-req #{::age :lat}
            ::parse/keys-opt #{::truth :uuid}}

           ;; named spec
           (info/parse-spec
             ::collect-info-spec)

           ;; spec
           (info/parse-spec
             (s/keys
               :req [::age]
               :req-un [::lat]
               :opt [::truth]
               :opt-un [::uuid]))

           ;; form
           (info/parse-spec
             (s/form
               (s/keys
                 :req [::age]
                 :req-un [::lat]
                 :opt [::truth]
                 :opt-un [::uuid]))))))

  (testing "ands and ors are flattened"
    (is (= {:type :map
            ::parse/key->spec {::age ::age
                               ::lat ::lat
                               ::uuid ::uuid}

            ::parse/keys #{::age ::lat ::uuid}
            ::parse/keys-req #{::age ::lat ::uuid}}
           (info/parse-spec
             (s/keys
               :req [(or ::age (and ::uuid ::lat))]))))))

(deftest type-inference-test
  (testing "works for core predicates"
    (is (= :long (:type (info/parse-spec `integer?)))))
  (testing "works for conjunctive predicates"
    (is (= [:and [:long]] (:type (info/parse-spec `(s/and integer? #(> % 42))))))
    (is (= [:and [:long]] (:type (info/parse-spec `(s/and #(> % 42) integer?))))))
  (testing "unknowns return nil"
    (is (= nil (:type (info/parse-spec #(> % 2))))))
  (testing "available types"
    (is (not (empty? (info/types))))
    (is (contains? (info/types) :boolean)))
  (testing "available type-symbols"
    (is (not (empty? (info/type-symbols))))
    (is (contains? (info/type-symbols) 'clojure.spec.alpha/keys))
    (is (contains? (info/type-symbols) 'clojure.core/integer?))))

(deftest form-inference-test
  (testing "works for core predicates"
    (is (= `integer? (form/resolve-form integer?))))
  (testing "lists return identity"
    (is (= `(s/coll-of integer?) (form/resolve-form `(s/coll-of integer?)))))
  (testing "qualified keywords return identity"
    (is (= ::kikka (form/resolve-form ::kikka))))
  (testing "unqualified keywords return unknown"
    (is (= ::s/unknown (form/resolve-form :kikka))))
  (testing "unknowns return unknown"
    (is (= ::s/unknown (form/resolve-form #(> % 2))))))

(s/def ::kw1 spec/keyword?)
(s/def ::m1 (s/keys :req-un [::kw1]))
(s/def ::kw2 spec/keyword?)
(s/def ::map (st/merge ::m1 (s/keys :opt-un [::kw2])))
(s/def ::core-map (s/merge ::m1 (s/keys :opt-un [::kw2])))

(s/def ::or (s/or :int int? :string string?))
(s/def ::or-map (st/merge (s/keys :req-un [::or])
                          (s/keys :opt-un [::kw2])))

(deftest merge-test
  (let [input {:kw1 "kw1"
               :kw2 "kw2"}
        bad-input {:kw2 :kw2}
        output {:kw1 :kw1
                :kw2 :kw2}]
    (testing "clojure.spec.alpha/merge"
      (testing "fails to conform all values with spec-tools.core/conform"
        (is (= {:kw1 "kw1"
                :kw2 :kw2}
               (st/conform ::core-map input st/json-transformer)))))
    (testing "spec-tools.core/merge"
      (testing "creates a conformer that conforms maps inside merge with spec-tools.core/conform"
        (is (= output (st/conform ::map input st/json-transformer)))
        (testing "also for non-spectools specs"
          (is (= {:or [:int 1]} (st/conform ::or-map {:or 1})))
          (is (= {:or [:string "1"]} (st/conform ::or-map {:or "1"}))))
        (testing "also for nested spec-tools.core/merge"
          (is (= output (st/conform (st/merge ::map) input st/json-transformer)))))
      (testing "fails with bad input"
        (is (not (s/valid? ::map bad-input))))
      (testing "doesn't strip extra keys from input"
        (is (= (assoc output :foo true)
               (st/conform ::map (assoc input :foo true) st/json-transformer))))
      (testing "works with strip-extra-keys-transformer"
        (is (= output
               (st/conform ::map (assoc output :foo true) st/strip-extra-keys-transformer))))
      (testing "has proper unform"
        (is (= output (s/conform ::map (s/unform ::map (st/conform ::map input st/json-transformer)))))
        (testing "also for non-spectools specs"
          (is (= {:or 1} (s/unform ::or-map (st/conform ::or-map {:or 1} st/json-transformer))))
          (is (= {:or "1"} (s/unform ::or-map (st/conform ::or-map {:or "1"} st/json-transformer))))))
      (testing "has a working generator"
        (is (s/valid? ::map (gen/generate (s/gen ::map)))))
      (testing "has a working with-gen"
        (let [new-spec (s/with-gen ::map #(gen/return output))]
          (testing "that creates a conformer that conforms maps inside merge"
            (is (= output (st/conform new-spec input st/json-transformer))))
          (testing "that uses the given generator"
            (is (= output (gen/generate (s/gen new-spec)))))))
      (testing "has the same explain as clojure.spec.alpha/merge"
        (let [expected-explanation (s/explain-data ::core-map bad-input)
              actual-explanation (s/explain-data ::map bad-input)]
          (is (= (select-keys (first (::s/problems expected-explanation))
                              [:path :pred :val :in])
                 (select-keys (first (::s/problems actual-explanation))
                              [:path :pred :val :in])))))
      (testing "has a working describe"
        (is (= (s/describe ::core-map)
               (:spec (second (s/describe ::map)))))))))

(s/def :resource.user/id int?)
(s/def :resource/user (s/keys :req-un [:resource.user/id]))

(s/def :response.user/data :resource/user)
(s/def :response/user (s/keys :req-un [:response.user/data]))

(deftest issue-145
  (is (= {:data {:id 41, :type "user", :attributes {:name "string"}}}
         (st/coerce
           :response/user
           {:data {:id "41", :type "user", :attributes {:name "string"}}}
           st/string-transformer))))

(deftest issue-123
  (testing "s/conform can transform composite types"
    (let [spec (s/double-in :min 0 :NaN? false :infinite? false)]
      (is (= 114.0
             (st/decode spec "114.0" st/string-transformer)
             (st/conform spec "114.0" st/string-transformer)
             (st/coerce spec "114.0" st/string-transformer))))))

(s/def ::car (s/keys :req-un [::doors]))
(s/def ::bike (s/keys :req-un [::wheels]))
(s/def ::tires (s/coll-of (s/and int?) :into #{}))
(s/def ::vehicle (s/or :car ::car
                       :bike ::bike))

(s/def ::new-vehicle (s/map-of
                      keyword?
                      (s/or :vehicle ::vehicle
                            :tires (s/coll-of (s/and int?) :into #{}))))

(s/def ::keyword keyword?)
(s/def ::int int?)
(s/def ::date inst?)
(s/def ::s (s/or :x (s/keys :req-un [::keyword ::int])
                 :y (s/keys :req-un [::keyword ::date])))

(deftest issue-179
  (testing "st/coerce can work properly with s/or specs"
    (let [chevy {:doors 4}]
      (is (= (st/coerce ::car chevy st/strip-extra-keys-transformer)
             {:doors 4}))
      (is (= (st/coerce ::vehicle chevy st/strip-extra-keys-transformer)
             {:doors 4}))
      (is (= (st/coerce ::new-vehicle {:rodas [1 "1" 3]} st/strip-extra-keys-transformer)
             {:rodas #{1 "1" 3}}))
      (is (= (st/coerce ::s {:keyword "a" :date "2020-02-22"} st/json-transformer)
             {:keyword :a :date #inst "2020-02-22T00:00:00.000-00:00"})))))

(s/def ::foo string?)
(s/def ::bar string?)
(s/def ::qix (s/keys :req-un [::foo]))
(s/def ::qux (s/keys :req-un [::bar]))
(def qix ::qix)

#?(:clj
   (deftest issue-201
     (testing "merge should work with symbols too"
       (is (st/spec? (st/merge ::qix ::qux)))
       (is (st/spec? (st/merge qix ::qux))))))

