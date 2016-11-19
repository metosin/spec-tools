(ns spec-tools.core
  (:refer-clojure :exclude #?(:clj [type]
                              :cljs [type Inst Keyword UUID]))
  #?(:cljs (:require-macros [spec-tools.core :refer [type]]))
  (:require
    [spec-tools.impl :as impl]
    [spec-tools.convert :as convert]
    [clojure.spec :as s]
    #?@(:clj  [[clojure.spec.gen :as gen]]
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
      (if (string? x)
        (if-let [conformer (get *conformers* hint)]
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
;; Types
;;

#?(:clj (ns-unmap *ns* 'String))
(def spec-tools.core/String (type ::string string?))

#?(:clj (ns-unmap *ns* 'Integer))
(def spec-tools.core/Integer (type ::long integer?))

#?(:clj (ns-unmap *ns* 'Int))
(def spec-tools.core/Int (type ::long int?))

#?(:clj (ns-unmap *ns* 'Double))
(def spec-tools.core/Double (type ::double #?(:clj  double?
                                               :cljs number?)))

#?(:clj (ns-unmap *ns* 'Keyword))
(def spec-tools.core/Keyword (type ::keyword keyword?))

#?(:clj (ns-unmap *ns* 'Boolean))
(def spec-tools.core/Boolean (type ::boolean boolean?))

(def spec-tools.core/UUID (type ::uuid uuid?))

#?(:clj (ns-unmap *ns* 'Inst))
(def spec-tools.core/Inst (type ::date-time inst?))
