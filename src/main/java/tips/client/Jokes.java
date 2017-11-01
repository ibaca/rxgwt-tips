package tips.client;

import static com.intendia.rxgwt2.elemental2.RxElemental2.fromPromise;
import static elemental2.dom.DomGlobal.fetch;
import static jsinterop.annotations.JsPackage.GLOBAL;

import elemental2.dom.Notification;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.TimeUnit;
import jsinterop.annotations.JsOverlay;
import jsinterop.annotations.JsType;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;

public interface Jokes {

    static Disposable showJokes() {
        return fromPromise(Notification.requestPermission()).filter("granted"::equals)
                .flatMapObservable(n -> Observable.interval(5, 30, TimeUnit.SECONDS))
                .flatMapSingle(n -> fetchJson("http://api.icndb.com/jokes/random", Random.class))
                .map(Random::value).subscribe(n -> notify("Joke #" + n.id, n.icon(), n.joke));
    }

    static Notification notify(String title, String icon, String message) {
        return new Notification(title, Js.uncheckedCast(JsPropertyMap.of("icon", icon, "body", message)));
    }

    static <T> Single<T> fetchJson(String url, Class<T> as) {
        return fromPromise(fetch(url))
                .flatMap(n -> fromPromise(n.json()))
                .map(Js::<T>cast);
    }

    @JsType(isNative = true, namespace = GLOBAL, name = "Object") class Random {
        public Joke value;
        public final @JsOverlay Joke value() { return value; }
    }

    @JsType(isNative = true, namespace = GLOBAL, name = "Object") class Joke {
        public int id;
        public String joke;
        public final @JsOverlay String icon() { return "https://api.adorable.io/avatars/60/" + id + ".png"; }
    }
}
