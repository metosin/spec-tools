(ns spec-tools.core
  (:refer-clojure :exclude [string? integer? int? double? keyword? boolean? uuid? inst?
                            #?@(:cljs [Inst Keyword UUID])])
  #?(:cljs (:require-macros [spec-tools.core :refer [spec coll-spec]]))
  (:require
    [spec-tools.impl :as impl]
    [spec-tools.types :as types]
    [spec-tools.convert :as convert]
    [clojure.spec :as s]
    #?@(:clj  [[clojure.spec.gen :as gen]
               [clojure.edn]]
        :cljs [[goog.date.UtcDateTime]
               [cljs.reader]
               [goog.date.Date]
               [clojure.test.check.generators]
               [cljs.spec.impl.gen :as gen]]))
  (:import
    #?@(:clj
        [(clojure.lang AFn IFn Var)
         (java.io Writer)])))

;;
;; helpers
;;

(defn ^:skip-wiki registry
  ([]
   (s/registry))
  ([re]
   (->> (s/registry)
        (filter #(-> % first str (subs 1) (->> (re-matches re))))
        (into {}))))

(defn ^:skip-wiki eq [value]
  #{value})

(defn ^:skip-wiki set-of [value]
  (s/coll-of
    value
    :kind set?))

(defn ^:skip-wiki enum [& values]
  (s/spec (set values)))

(defn ^:skip-wiki serialize
  "Writes specs into a string that can be read by the reader.
  TODO: Should optionally write the realated Registry entries."
  [spec]
  (pr-str (s/form spec)))

(defn ^:skip-wiki deserialize
  "Reads specs from a string.
  TODO: Should optionally read the realated Registry entries."
  [s]
  #?(:clj  (clojure.edn/read-string s)
     :cljs (cljs.reader/read-string s)))

(def invalid '::s/invalid)

;;
;; Dynamic conforming
;;

(def ^:dynamic ^:private *conformers* nil)

(def string-conformers
  {:long convert/string->long
   :double convert/string->double
   :keyword convert/string->keyword
   :boolean convert/string->boolean
   :uuid convert/string->uuid
   :date convert/string->date})

(def json-conformers
  {:keyword convert/string->keyword
   :uuid convert/string->uuid
   :date convert/string->date})

(defn conform
  ([spec value]
   (s/conform spec value))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (s/conform spec value))))

;;
;; Spec Record
;;

(defn- extra-spec-map [t]
  (dissoc t :form :pred :gfn))

(defrecord Spec [hint form pred gfn]
  #?@(:clj
      [s/Specize
       (specize* [s] s)
       (specize* [s _] s)])

  s/Spec
  (conform* [_ x]
    ;; function predicate
    (if (and (fn? pred) (pred x))
      x
      ;; there is a dynamic conformer
      (if-let [conformer (get *conformers* hint)]
        (conformer pred x)
        ;; spec predicate
        (if (s/spec? pred)
          (s/conform pred x)
          ;; invalid
          '::s/invalid))))
  (unform* [_ x]
    x)
  (explain* [_ path via in x]
    (when (= ::s/invalid (if (pred x) x ::s/invalid))
      [{:path path :pred (s/abbrev form) :val x :via via :in in}]))
  (gen* [_ _ _ _]
    (if gfn
      (gfn)
      (gen/gen-for-pred pred)))
  (with-gen* [this gfn]
    (assoc this :gfn gfn))
  (describe* [this]
    (let [info (extra-spec-map this)]
      `(spec ~form ~info)))
  IFn
  #?(:clj  (invoke [_ x] (pred x))
     :cljs (-invoke [_ x] (pred x))))

#?(:clj
   (defmethod print-method Spec
     [^Spec t ^Writer w]
     (.write w (str "#Spec"
                    (merge
                      {:pred (:form t)}
                      (if (:hint t) (select-keys t [:hint]))
                      (extra-spec-map t))))))

#?(:clj
   (defmacro spec
     ([pred]
      (if (impl/in-cljs? &env)
        `(let [form# (if (symbol? '~pred)
                       '~(or (and (symbol? pred) (some->> pred (impl/cljs-resolve &env) impl/->sym)) pred)
                       (s/form ~pred))]
           (map->Spec
             {:hint (types/resolve-type form#)
              :pred ~pred
              :form form#}))
        `(let [form# (if (symbol? '~pred)
                       '~(or (and (symbol? pred) (some->> pred resolve impl/->sym)) pred)
                       (s/form ~pred))]
           (map->Spec
             {:hint (types/resolve-type form#)
              :pred ~pred
              :form form#}))))
     ([pred info]
      (if (impl/in-cljs? &env)
        `(let [info# ~info
               form# (if (symbol? '~pred)
                       '~(or (and (symbol? pred) (some->> pred (impl/cljs-resolve &env) impl/->sym)) pred)
                       (s/form ~pred))]
           (assert (map? info#) (str "spec info should be a map, was: " info#))
           (map->Spec
             (merge
               ~info
               (if-not (contains? info# :hint)
                 {:hint (types/resolve-type form#)})
               {:form form#
                :pred ~pred})))
        `(let [info# ~info
               form# (if (symbol? '~pred)
                       '~(or (and (symbol? pred) (some->> pred resolve impl/->sym)) pred)
                       (s/form ~pred))]
           (assert (map? info#) (str "spec info should be a map, was: " info#))
           (map->Spec
             (merge
               ~info
               (if-not (contains? info# :hint)
                 {:hint (types/resolve-type form#)})
               {:form form#
                :pred ~pred})))))))

#?(:clj
   (defmacro doc [pred info]
     `(spec ~pred (merge ~info {:hint nil}))))

#?(:clj
   (defmacro typed-spec
     ([hint pred]
      `(spec ~pred {:hint ~hint}))
     ([hint pred info]
      `(spec ~pred (merge ~info {:hint ~hint})))))

;;
;; Map Spec
;;

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))

#?(:clj (declare ^:private coll-spec-fn))

(defn- -vector [env n v]
  (if-not (= 1 (count v))
    (throw
      (ex-info
        "only single maps allowed in nested vectors"
        {:k n :v v}))
    `(s/coll-of ~(coll-spec-fn env n (first v)) :into [])))

(defn- -set [env n v]
  (if-not (= 1 (count v))
    (throw
      (ex-info
        "only single maps allowed in nested sets"
        {:k n :v v}))
    `(s/coll-of ~(coll-spec-fn env n (first v)) :into #{})))

#?(:clj
   (defn- -map [env n m]
     (let [resolve (if (impl/in-cljs? env) (partial impl/cljs-resolve env) resolve)
           resolved-opt (resolve `opt)
           resolved-req (resolve `req)]

       ;; predicate keys
       (if-let [key-spec (and (= 1 (count m))
                              (let [k (first (keys m))]
                                (and
                                  (not
                                    (or (clojure.core/keyword? k)
                                        (and (seq? k)
                                             (let [resolved (resolve (first k))]
                                               (#{resolved-opt resolved-req} resolved)))))
                                  k)))]
         `(s/map-of ~key-spec ~(coll-spec-fn env n (first (vals m))))
         ;; keyword keys
         (let [m (reduce-kv
                   (fn [acc k v]
                     (let [[req? kv] (if (seq? k) [(not= resolved-opt (resolve (first k))) (second k)] [true k])
                           k1 (if req? "req" "opt")
                           k2 (if-not (qualified-keyword? kv) "-un")
                           ak (keyword (str k1 k2))
                           [k' v'] (if (qualified-keyword? kv)
                                     [kv (if (not= kv v) v)]
                                     (let [k' (keyword (str (str (namespace n) "$" (name n)) "/" (name kv)))]
                                       [k' (if (or (map? v) (vector? v) (set? v))
                                             (coll-spec-fn env k' v) v)]))]
                       (-> acc
                           (update ak (fnil conj []) k')
                           (cond-> v' (update ::defs (fnil conj []) [k' v'])))))
                   {}
                   m)
               defs (::defs m)
               margs (apply concat (dissoc m ::defs))]
           `(do
              ~@(for [[k v] defs]
                  `(s/def ~k ~v))
              (s/keys ~@margs)))))))

#?(:clj
   (defn- coll-spec-fn [env n m]
     (if-let [f (cond
                  (map? m) -map
                  (vector? m) -vector
                  (set? m) -set)]
       (f env n m)
       `~m)))
#?(:clj
   (defmacro coll-spec [n m]
     (coll-spec-fn &env n m)))

;;
;; Specs
;;

(def spec-tools.core/string? (spec clojure.core/string?))
(def spec-tools.core/integer? (spec clojure.core/integer?))
(def spec-tools.core/int? (spec clojure.core/int?))
(def spec-tools.core/double? (spec #?(:clj clojure.core/double?, :cljs clojure.core/number?)))
(def spec-tools.core/keyword? (spec clojure.core/keyword?))
(def spec-tools.core/boolean? (spec clojure.core/boolean?))
(def spec-tools.core/uuid? (spec clojure.core/uuid?))
(def spec-tools.core/inst? (spec clojure.core/inst?))
