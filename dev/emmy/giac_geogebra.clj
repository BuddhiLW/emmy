(ns emmy.giac-geogebra
  "# Emmy + Giac + GeoGebra

  Drag the point `P` or the slider `a` in the GeoGebra applet below. The
  applet sends both values to the JVM; Emmy builds `f`, Giac integrates it
  exactly, Emmy checks Giac's answer by differentiating it, and the new `f`
  and `F` go back into the applet as GeoGebra commands."
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   ;; Results hold a live process and compiled functions; nothing to cache.
   :nextjournal.clerk/no-cache true}
  (:require [emmy.env :as e]
            [emmy.giac :as giac]
            [emmy.giac.process :as gp]
            [emmy.giac.render :refer [->giac]]
            [nextjournal.clerk :as clerk]))

;; The engine is one long-lived Giac process; it survives notebook reloads.
(defonce engine (gp/start!))

;; Shared with the browser: the applet writes `:a` and `:p` here.
^::clerk/sync
(defonce !state (atom {:a 1 :p 2}))

(def f (e/* 'a 'x (e/sin 'x)))

(def F (giac/integrate engine f 'x))

(def F-fn
  "Giac's `F` as an Emmy function of `x`, so Emmy can differentiate it."
  (let [body (e/freeze F)
        g (binding [*ns* (the-ns 'emmy.env)]
            (eval (list 'fn '[x a] body)))]
    (fn [x] (g x 'a))))

(def check (e/simplify (e/- ((e/D F-fn) 'x) f)))

(defn- exact
  "A rational close to `x`, so Giac answers exactly."
  [x]
  (/ (Math/round (* 100.0 (double x))) 100))

(def a (exact (:a @!state)))

(def p (exact (:p @!state)))

(def f-at-a (e/* a 'x (e/sin 'x)))

(def F-at-a (giac/integrate engine f-at-a 'x))

(def area (giac/integrate engine f-at-a 'x 0 p))

(def area-approx (double (giac/call engine 'evalf area)))

(def geogebra-viewer
  {;; Hand the render-fn the raw map, not Clerk's presented form of it.
   :transform-fn clerk/mark-presented
   :render-fn
   '(fn [{:keys [commands init]} _]
      (reagent.core/with-let
        [id (str "ggb-" (random-uuid))
         !api (reagent.core/atom nil)
         !timer (atom nil)
         push! (fn [api]
                 ;; Debounced, and only when a value really moved, so the
                 ;; applet's own setup does not trigger a recompute.
                 (js/clearTimeout @!timer)
                 (reset! !timer
                         (js/setTimeout
                          (fn []
                            (let [a (.getValue api "a")
                                  p (.getXcoord api "P")
                                  s @emmy.giac-geogebra/!state]
                              (when (or (not= a (:a s)) (not= p (:p s)))
                                (swap! emmy.giac-geogebra/!state assoc :a a :p p))))
                          150)))
         on-load (fn [api]
                   ;; evalCommand returns false for scripting commands such as
                   ;; SetValue even when they apply, so results are not checked.
                   (doseq [c ["a = Slider(0.2, 3, 0.01)"
                              (str "SetValue(a, " (:a init) ")")
                              "P = Point(xAxis)"
                              (str "SetCoords(P, " (:p init) ", 0)")]]
                     (.evalCommand api c))
                   (.registerUpdateListener
                    api
                    (fn [obj] (when (#{"a" "P"} obj) (push! api))))
                   (reset! !api api))
         start! (fn []
                  (.inject (js/GGBApplet.
                            (clj->js {:appName "graphing" :width 760 :height 460
                                      :showToolBar false :showMenuBar false
                                      :showAlgebraInput false :enableShiftDragZoom true
                                      :appletOnLoad on-load})
                            true)
                           id))
         _ (if (.-GGBApplet js/window)
             (js/setTimeout start! 0)
             (let [s (js/document.createElement "script")]
               (set! (.-src s) "https://www.geogebra.org/apps/deployggb.js")
               (set! (.-onload s) start!)
               (.appendChild js/document.head s)))]
        (when-let [api @!api]
          (doseq [c commands] (.evalCommand api c)))
        [:div {:id id}]))})

{::clerk/visibility {:result :show}}

;; ## The function, built in Emmy

(clerk/tex (str "f(x) = " (e/->TeX f)))

;; ## Its antiderivative, from Giac

(clerk/tex (str "F(x) = \\int f(x)\\,dx = " (e/->TeX F)))

;; ## Emmy checks Giac
;;
;; Emmy differentiates Giac's `F` with its own `D` and simplifies `F' - f`.

(clerk/tex (str "F'(x) - f(x) = " (e/->TeX check)))

;; ## At the values the applet sent

(clerk/tex
 (str "a = " (e/->TeX a) ",\\quad "
      "\\int_0^{" (e/->TeX p) "} f(x)\\,dx = " (e/->TeX area)
      " \\approx " (format "%.6f" area-approx)))

;; ## GeoGebra

(clerk/with-viewer geogebra-viewer
  {:init @!state
   :commands [(str "f(x) = " (->giac f-at-a))
              (str "F(x) = " (->giac F-at-a))
              "SetColor(f, \"blue\")"
              "SetColor(F, \"red\")"
              "area = Integral(f, 0, x(P))"]})
