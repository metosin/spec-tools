(ns spec-tools.core
  (:refer-clojure :exclude #?(:clj [type]
                              :cljs [type Inst Keyword UUID]))
  #?(:cljs (:require-macros [spec-tools.core :refer [type]]))
  (:require
    [spec-tools.impl :as impl]
    [clojure.spec :as s]
    #?@(:clj  [[clojure.spec.gen :as gen]]
        :cljs [[goog.date.UtcDateTime]
               [clojure.test.check.generators]
               [cljs.spec.impl.gen :as gen]]))
  (:import
    #?@(:clj
        [(clojure.lang AFn IFn Var)
         (java.io Writer)])))

(defn- ->sym [x]
  #?(:clj  (if (var? x)
             (let [^Var v x]
               (symbol (str (.name (.ns v)))
                       (str (.sym v))))
             x)
     :cljs (if (map? x)
             (:name x)
             x)))

(defn- double-like? [x]
  (#?(:clj  double?
      :cljs number?) x))

(defn -string->int [x]
  (if (string? x)
    (try
      #?(:clj  (java.lang.Integer/parseInt x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn -string->long [x]
  (if (string? x)
    (try
      #?(:clj  (Long/parseLong x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn -string->double [x]
  (if (string? x)
    (try
      #?(:clj  (java.lang.Double/parseDouble x)
         :cljs (js/parseFloat x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn -string->keyword [x]
  (if (string? x)
    (keyword x)))

(defn -string->boolean [x]
  (if (string? x)
    (cond
      (= "true" x) true
      (= "false" x) false
      :else ::s/invalid)))

(defn -string->uuid [x]
  (if (string? x)
    (try
      #?(:clj  (java.util.UUID/fromString x)
         :cljs (uuid x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn -string->inst [x]
  (if (string? x)
    (try
      #?(:clj  (.toDate (org.joda.time.DateTime/parse x))
         :cljs (js/Date. (.getTime (goog.date.UtcDateTime.fromIsoString x))))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

;;
;; Public API
;;

(def string-conformers
  {::integer -string->int
   ::int -string->long
   ::double -string->double
   ::keyword -string->keyword
   ::boolean -string->boolean
   ::uuid -string->uuid
   ::inst -string->inst})

(def json-conformers
  {::keyword -string->keyword
   ::uuid -string->uuid
   ::inst -string->inst})

(def ^:dynamic ^:private *conformers* nil)

(defn conform
  ([spec value]
   (s/conform spec value))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (s/conform spec value))))

;;
;; Types
;;

(defprotocol TypeHint
  (type-hint [this]))

(defn- extra-type-map [t]
  (dissoc t :hint :form :pred :gfn))

(defrecord Type [hint form pred gfn]
  #?@(:clj
      [s/Specize
       (specize* [s] s)
       (specize* [s _] s)])

  s/Spec
  (conform* [this x]
    (if (pred x)
      x
      (if (string? x)
        (if-let [conformer (get *conformers* (type-hint this))]
          (conformer x)
          '::s/invalid)
        ::s/invalid)))
  (unform* [_ x] x)
  (explain* [_ path via in x]
    (when (= ::s/invalid (if (pred x) x ::s/invalid))
      [{:path path :pred (s/abbrev form) :val x :via via :in in}]))
  (gen* [_ _ _ _] (if gfn
                    (gfn)
                    (gen/gen-for-pred pred)))
  (with-gen* [this gfn] (assoc this :gfn gfn))
  (describe* [this]
    (let [info (extra-type-map this)]
      (if (seq info)
        `(spec-tools.core/type ~hint ~form ~info)
        `(spec-tools.core/type ~hint ~form))))
  IFn
  #?(:clj  (invoke [_ x] (pred x))
     :cljs (-invoke [_ x] (pred x)))

  TypeHint
  (type-hint [_] hint))

#?(:clj
   (defmethod print-method Type
     [^Type t ^Writer w]
     (.write w (str "#Type"
                    (merge
                      {:hint (:hint t)
                       :pred (:form t)}
                      (extra-type-map t))))))

#?(:clj
   (defmacro type
     ([hint pred]
      (if (impl/in-cljs? &env)
        `(map->Type {:hint ~hint :form '~(or (->> pred (impl/res &env) ->sym) pred), :pred ~pred})
        `(map->Type {:hint ~hint :form '~(or (->> pred resolve ->sym) pred), :pred ~pred})))
     ([hint pred info]
      (if (impl/in-cljs? &env)
        `(map->Type (merge ~info {:hint ~hint :form '~(or (->> pred (impl/res &env) ->sym) pred), :pred ~pred}))
        `(map->Type (merge ~info {:hint ~hint :form '~(or (->> pred resolve ->sym) pred), :pred ~pred}))))))

(defn with-info [^Type t info]
  (assoc t :info info))

(defn info [^Type t]
  (:info t))

;;
;; concrete types
;;

#?(:clj (ns-unmap *ns* 'String))
(def spec-tools.core/String (type ::string string?))

#?(:clj (ns-unmap *ns* 'Integer))
(def spec-tools.core/Integer (type ::integer integer?))

#?(:clj (ns-unmap *ns* 'Int))
(def spec-tools.core/Int (type ::int int?))

#?(:clj (ns-unmap *ns* 'Double))
(def spec-tools.core/Double (type ::double double-like?))

#?(:clj (ns-unmap *ns* 'Keyword))
(def spec-tools.core/Keyword (type ::keyword keyword?))

#?(:clj (ns-unmap *ns* 'Boolean))
(def spec-tools.core/Boolean (type ::boolean boolean?))

(def spec-tools.core/UUID (type ::uuid uuid?))

#?(:clj (ns-unmap *ns* 'Inst))
(def spec-tools.core/Inst (type ::inst inst?))
