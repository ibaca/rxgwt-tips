package tips.client;

import static com.intendia.rxgwt2.elemental2.RxElemental2.fromPromise;
import static elemental2.dom.DomGlobal.fetch;
import static io.reactivex.Flowable.defer;
import static io.reactivex.Flowable.interval;
import static jsinterop.annotations.JsPackage.GLOBAL;

import elemental2.dom.Notification;
import elemental2.dom.NotificationOptions;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.TimeUnit;
import jsinterop.annotations.JsType;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;

public interface Pokeapi {

    static Disposable showPokemons() {
        return fromPromise(Notification.requestPermission()).filter("granted"::equals).toFlowable()
                .concatMap(n -> pokePaging("https://pokeapi.co/api/v2/pokemon/?limit=5"), 1)
                .zipWith(interval(5, 30, TimeUnit.SECONDS), (url, tick) -> url, false, 1)
                .flatMapSingle(n -> fetchJson(n.url, Pokemon.class))
                .subscribe(n -> {
                    NotificationOptions options = Js.uncheckedCast(JsPropertyMap.of());
                    options.icon = n.sprites.front_default;
                    options.body = "Do you know that " + n.name + " weight is " + n.weight + ".";
                    new Notification(n.name, options);
                });
    }

    static Flowable<Pokeref> pokePaging(String n) {
        return defer(() -> fetchJson(n, Pokeref.Wrap.class).toFlowable()
                .concatMap(res -> Flowable.fromArray(res.results).concatWith(pokePaging(res.next))));
    }

    static <T> Single<T> fetchJson(String url, Class<T> as) {
        return fromPromise(fetch(url)).flatMap(n -> fromPromise(n.json())).map(Js::<T>cast);
    }

    @JsType(isNative = true, namespace = GLOBAL, name = "Object") class Pokeref {
        public String name, url;

        @JsType(isNative = true, namespace = GLOBAL, name = "Object") public static class Wrap {
            public Pokeref[] results;
            public String next;
        }
    }

    @JsType(isNative = true, namespace = GLOBAL, name = "Object") class Pokemon {
        public String name;
        public int height;
        public int weight;
        public Sprites sprites;
    }

    @JsType(isNative = true, namespace = GLOBAL, name = "Object") class Sprites {
        public String front_default;
    }
}
