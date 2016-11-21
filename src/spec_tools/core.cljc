(ns spec-tools.core
  (:refer-clojure :exclude [map type string? integer? int? double? keyword? boolean? uuid? inst?
                            #?@(:cljs [Inst Keyword UUID])])
  #?(:cljs (:require-macros [spec-tools.core :refer [type map]]))
  (:require
    [spec-tools.impl :as impl]
    [spec-tools.convert :as convert]
    [clojure.spec :as s]
    #?@(:clj  [
    [clojure.spec.gen :as gen]]
        :cljs [[goog.date.UtcDateTime]
               [goog.date.Date]
               [clojure.test.check.generators]
               [cljs.spec.impl.gen :as gen]])
    [clojure.string :as str])
  (:import
    #?@(:clj
        [(clojure.lang AFn IFn Var)
         (java.io Writer)])))

;;
;; Dynamic conforming
;;

(def ^:dynamic ^:private *conformers* nil)

(def string-conformers
  {::long convert/string->long
   ::double convert/string->double
   ::keyword convert/string->keyword
   ::boolean convert/string->boolean
   ::uuid convert/string->uuid
   ::date-time convert/string->date-time})

(def json-conformers
  {::keyword convert/string->keyword
   ::uuid convert/string->uuid
   ::date-time convert/string->date-time})

(defn conform
  ([spec value]
   (s/conform spec value))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (s/conform spec value))))

;;
;; Type Record
;;

(defn- extra-type-map [t]
  (dissoc t :hint :form :pred :gfn))

(defrecord Type [hint form pred gfn]
  #?@(:clj
      [s/Specize
       (specize* [s] s)
       (specize* [s _] s)])

  s/Spec
  (conform* [_ x]
    (if (pred x)
      x
      (if-let [conformer (get *conformers* hint)]
        (conformer x)
        '::s/invalid)))
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
     :cljs (-invoke [_ x] (pred x))))

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
        `(map->Type {:hint ~hint :form '~(or (->> pred (impl/cljs-resolve &env) impl/->sym) pred), :pred ~pred})
        `(map->Type {:hint ~hint :form '~(or (->> pred resolve impl/->sym) pred), :pred ~pred})))
     ([hint pred info]
      (if (impl/in-cljs? &env)
        `(map->Type (merge ~info {:hint ~hint :form '~(or (->> pred (impl/cljs-resolve &env) impl/->sym) pred), :pred ~pred}))
        `(map->Type (merge ~info {:hint ~hint :form '~(or (->> pred resolve impl/->sym) pred), :pred ~pred}))))))

;;
;; Map Spec
;;

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))

#?(:clj
   (defn -map [n m env]
     (reduce-kv
       (fn [acc k v]
         (let [resolve (if (impl/in-cljs? env) (partial impl/cljs-resolve env) resolve)
               [req? kv] (if (seq? k) [(not= (resolve `opt) (resolve (first k))) (second k)] [true k])
               k1 (if req? "req" "opt")
               k2 (if-not (qualified-keyword? kv) "-un")
               ak (keyword (str k1 k2))
               [k' v'] (if (qualified-keyword? kv)
                         [kv (if (not= kv v) v)]
                         [(keyword (str (namespace n) "$$" (name n) "/" (name kv))) v])]

           (-> acc
               (update ak (fnil conj []) k')
               (cond-> v' (update ::defs (fnil conj []) [k' v'])))))
       {}
       m)))


#?(:clj
   (defmacro map [n m]
     (let [m (-map n m &env)
           defs (::defs m)
           margs (apply concat (dissoc m ::defs))]
       `(do
          ~@(for [[k v] defs]
              `(s/def ~k ~v))
          (s/keys ~@margs)))))

;;
;; Types
;;

(def spec-tools.core/string? (type ::string clojure.core/string?))
(def spec-tools.core/integer? (type ::long clojure.core/integer?))
(def spec-tools.core/int? (type ::long clojure.core/int?))
(def spec-tools.core/double? (type ::double #?(:clj clojure.core/double?, :cljs clojure.core/number?)))
(def spec-tools.core/keyword? (type ::keyword clojure.core/keyword?))
(def spec-tools.core/boolean? (type ::boolean clojure.core/boolean?))
(def spec-tools.core/uuid? (type ::uuid clojure.core/uuid?))
(def spec-tools.core/inst? (type ::date-time clojure.core/inst?))
