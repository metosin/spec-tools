(ns spec-tools.core
  (:refer-clojure :exclude [map string? integer? int? double? keyword? boolean? uuid? inst?
                            #?@(:cljs [Inst Keyword UUID])])
  #?(:cljs (:require-macros [spec-tools.core :refer [spec map]]))
  (:require
    [spec-tools.impl :as impl]
    [spec-tools.convert :as convert]
    [clojure.spec :as s]
    #?@(:clj  [
    [clojure.spec.gen :as gen]]
        :cljs [[goog.date.UtcDateTime]
               [goog.date.Date]
               [clojure.test.check.generators]
               [cljs.spec.impl.gen :as gen]]))
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
;; Spec Record
;;

(defn- extra-spec-map [t]
  (dissoc t :hint :form :pred :gfn))

(defrecord Spec [hint form pred gfn]
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
    (let [info (extra-spec-map this)]
      (if (seq info)
        `(spec ~hint ~form ~info)
        `(spec ~hint ~form))))
  IFn
  #?(:clj  (invoke [_ x] (pred x))
     :cljs (-invoke [_ x] (pred x))))

#?(:clj
   (defmethod print-method Spec
     [^Spec t ^Writer w]
     (.write w (str "#Spec"
                    (merge
                      {:hint (:hint t)
                       :pred (:form t)}
                      (extra-spec-map t))))))

#?(:clj
   (defmacro spec
     ([hint pred]
      (if (impl/in-cljs? &env)
        `(map->Spec {:hint ~hint :form '~(or (->> pred (impl/cljs-resolve &env) impl/->sym) pred), :pred ~pred})
        `(map->Spec {:hint ~hint :form '~(or (->> pred resolve impl/->sym) pred), :pred ~pred})))
     ([hint pred info]
      (if (impl/in-cljs? &env)
        `(map->Spec (merge ~info {:hint ~hint :form '~(or (->> pred (impl/cljs-resolve &env) impl/->sym) pred), :pred ~pred}))
        `(map->Spec (merge ~info {:hint ~hint :form '~(or (->> pred resolve impl/->sym) pred), :pred ~pred}))))))

;;
;; Map Spec
;;

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))

#?(:clj
   (defmacro map [n m]
     (let [m (reduce-kv
               (fn [acc k v]
                 (let [resolve (if (impl/in-cljs? &env) (partial impl/cljs-resolve &env) resolve)
                       [req? kv] (if (seq? k) [(not= (resolve `opt) (resolve (first k))) (second k)] [true k])
                       k1 (if req? "req" "opt")
                       k2 (if-not (qualified-keyword? kv) "-un")
                       ak (keyword (str k1 k2))
                       [k' v'] (if (qualified-keyword? kv)
                                 [kv (if (not= kv v) v)]
                                 (let [k' (keyword (str (str (namespace n) "$" (name n)) "/" (name kv)))]
                                   [k' (if (map? v) `(map ~k' ~v) v)]))]
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
          (s/keys ~@margs)))))

;;
;; Specs
;;

(def spec-tools.core/string? (spec ::string clojure.core/string?))
(def spec-tools.core/integer? (spec ::long clojure.core/integer?))
(def spec-tools.core/int? (spec ::long clojure.core/int?))
(def spec-tools.core/double? (spec ::double #?(:clj clojure.core/double?, :cljs clojure.core/number?)))
(def spec-tools.core/keyword? (spec ::keyword clojure.core/keyword?))
(def spec-tools.core/boolean? (spec ::boolean clojure.core/boolean?))
(def spec-tools.core/uuid? (spec ::uuid clojure.core/uuid?))
(def spec-tools.core/inst? (spec ::date-time clojure.core/inst?))
