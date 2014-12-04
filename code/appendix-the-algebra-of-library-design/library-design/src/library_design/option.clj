(ns library-design.option
  (:require [uncomplicate.fluokitten.protocols :as fkp]
            [uncomplicate.fluokitten.core :as fkc]
            [uncomplicate.fluokitten.jvm :as fkj]
            [imminent.core :as i]))

;;
;; Functor
;;

(def pirates [{:name "Jack Sparrow"    :born 1700 :died 1740 :ship "Black Pearl"}
              {:name "Blackbeard"      :born 1680 :died 1750 :ship "Queen Anne's Revenge"}
              {:name "Hector Barbossa" :born 1680 :died 1740 :ship nil}])


(defn pirate-by-name [name]
  (->> pirates
       (filter #(= name (:name %)))
       first))

(defn age [{:keys [born died]}]
  (- died born))

(comment

  (-> (pirate-by-name "Jack Sparrow")
      age) ;; 40

  (-> (pirate-by-name "Davy Jones")
      age) ;; NullPointerException   clojure.lang.Numbers.ops (Numbers.java:961)




  )

(defrecord Some [v])

(defrecord None [])


(defn option [v]
  (if v
    (Some. v)
    (None.)))


(comment


  ;; delete me
  (defprotocol Functor

    (fmap [fv g] ))
  ;; laws


  )




(extend-protocol fkp/Functor
  Some
  (fmap [f g]
    (Some. (g (:v f))))
  None
  (fmap [_ _]
    (None.)))


(->> (option (pirate-by-name "Jack Sparrow"))
     (fkc/fmap age)) ;; #library_design.option.Some{:v 40}

(->> (option (pirate-by-name "Davy Jones"))
     (fkc/fmap age)) ;; #library_design.option.None{}


(->> (option (pirate-by-name "Jack Sparrow"))
     (fkc/fmap age)
     (fkc/fmap inc)
     (fkc/fmap #(* 2 %))) ;; #library_design.option.Some{:v 82}

(->> (option (pirate-by-name "Davy Jones"))
     (fkc/fmap age)
     (fkc/fmap inc)
     (fkc/fmap #(* 2 %))) ;; #library_design.option.None{}



(some-> (pirate-by-name "Davy Jones")
        age
        inc
        (* 2)) ;; nil


(->> (i/future (pirate-by-name "Jack Sparrow"))
     (fkc/fmap age)
     (fkc/fmap inc)
     (fkc/fmap #(* 2 %))) ;; #<Future@30518bfc: #<Success@39bd662c: 82>>

(= (fkc/fmap identity (option 1))
   (identity (option 1)))


(= (fkc/fmap (comp identity inc) (option 1))
   (fkc/fmap identity (fkc/fmap inc (option 1))))


;; (defn sum-and-double-option [a b c]
;;   (->> (when (and a b c)
;;          (sum-3 a b c))
;;        double-n))


;; (sum-and-double-option 1 2 3) ;; 12

;; (sum-and-double-option 1 nil 3) ;; NullPointerException   clojure.lang.Numbers.ops

(def  repl-out *out*)
(defn prn-to-repl [& args]
  (binding [*out* repl-out]
    (apply prn args)))


(as-> (i/const-future 31) f
      (fkc/fmap #(* % 2) f)
      (i/on-success f #(prn-to-repl (str "Value: " %))))


(map inc [1 2 ])


(defn avg [& xs]
  (float (/ (apply + xs) (count xs))))

(def age-option (comp (partial fkc/fmap age) option pirate-by-name))

;;
;; Applicative
;;


(extend-protocol fkp/Applicative
  Some
  (pure [_ v]
    (Some. v))

  (fapply [ag av]
    (Some. ((:v ag) (:v av))))

  None
  (pure [_ v]
    (Some. v))

  (fapply [ag av]
    (None.)))

(fkc/<*> (option (fkj/curry avg 3))
         (age-option "Jack Sparrow")
         (age-option "Blackbeard")
         (age-option "Hector Barbossa")) ;; #library_design.option.Some{:v 56.666668}


(defn alift
  "Lifts a n-ary function `f` into a applicative context"
  [f]
  (fn [& as]
    {:pre  [(seq as)]}
    (let [curried (fkj/curry f (count as))]
      (prn "first " (fkc/fmap curried (first as)))
      (apply fkc/<*>
             (fkc/fmap curried (first as))
             (rest as)))))

((alift avg) (age-option "Jack Sparrow") (age-option "Blackbeard") (age-option "Hector Barbossa"))
((alift prn)  (option 2) (age-option "Jack Sparrow"))
((alift prn)  (age-option "Jack Sparrow") (option 2) )

(= (type (age-option "Jack Sparrow")) (type (option 2)) )


(fkc/fmap (fkj/curry prn 1) (age-option "Jack Sparrow"))
;; #library_design.option.Some{:v 56.666668}



;;
;; Monad
;;

(comment






  (let  [a (-> (pirate-by-name "Jack Sparrow")    age)
         b (-> (pirate-by-name "Blackbeard")      age)
         c (-> (pirate-by-name "Hector Barbossa") age)]
    (avg a b c)) ;; 56.666668

  (let  [a (-> (pirate-by-name "Jack Sparrow")    age)
         b (-> (pirate-by-name "Davy Jones")      age)
         c (-> (pirate-by-name "Hector Barbossa") age)]
    (avg a b c)) ;; NullPointerException   clojure.lang.Numbers.ops (Numbers.java:961)


  (let  [a (some-> (pirate-by-name "Jack Sparrow")    age)
         b (some-> (pirate-by-name "Davy Jones")      age)
         c (some-> (pirate-by-name "Hector Barbossa") age)]
    (avg a b c)) ;; NullPointerException   clojure.lang.Numbers.ops (Numbers.java:961)

  (let  [a (some-> (pirate-by-name "Jack Sparrow")    age)
         b (some-> (pirate-by-name "Davy Jones")      age)
         c (some-> (pirate-by-name "Hector Barbossa") age)]
    (when (and a b c)
      (avg a b c))) ;; nil


  )

(extend-protocol fkp/Monad
  Some
  (bind [mv g]
    (g (:v mv)))

  None
  (bind [_ _]
    (None.)))



;; (fkc/bind (None.) identity)

(fkc/bind (age-option "Jack Sparrow")
          (fn [a]
            (fkc/bind (age-option "Blackbeard")
                      (fn [b]
                        (fkc/bind (age-option "Hector Barbossa")
                                  (fn [c]
                                    (option (float (/ (+ a b c) 3)))))))))

(fkc/mdo [a (age-option "Jack Sparrow")
          b (age-option "Blackbeard")
          c (age-option "Hector Barbossa")]
         (option  (float (/ (+ a b c) 3)))) ;; #library_design.option.Some{:v 56.666668}

(fkc/mdo [a (age-option "Jack Sparrow")
          b (age-option "Davy Jones")
          c (age-option "Hector Barbossa")]
         (option  (float (/ (+ a b c) 3)))) ;; #library_design.option.None{}


(require '[clojure.walk :as w])
(w/macroexpand-all '(fkc/mdo [a (age-option "Jack Sparrow")
                              b (age-option "Blackbeard")
                              c (age-option "Hector Barbossa")]
                             (option  (float (/ (+ a b c) 3)))))


(defn mlift2
  "Lifts a binary function `f` into a monadic context"
  [f]
  (fn [ma mb]
    (fkc/mdo [a ma
              b mb]
             (fkc/pure ma (f a b)))))

(defn msequence
  "`ctx` is the monadic context.
  Given a monad `m` and a list of monads `ms`, it returns a single monad containing a list of
  the values yielded by all monads in `ms`"
  [ctx ms]
  (reduce (mlift2 conj)
          (fkc/pure ctx [])
          ms))

(def m-ctx (option nil))
(->> (msequence  m-ctx [(age-option "Jack Sparrow")
                        (age-option "Blackbeard")
                        (age-option "Hector Barbossa")]) ;; #library_design.option.Some{:v [40 70 60]}
     (fkc/fmap #(apply avg %))) ;; #library_design.option.Some{:v 56.666668}
