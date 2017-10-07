package tips.client;

import static elemental2.dom.DomGlobal.console;
import static io.reactivex.Single.timer;
import static io.reactivex.schedulers.Schedulers.computation;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.intendia.rxgwt2.user.RxEvents;
import com.intendia.rxgwt2.user.RxHandlers;
import elemental2.dom.Console;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.gwt.schedulers.GwtSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Original idea from http://philipnilsson.github.io/badness
 * Style from https://github.com/staltz/flux-challenge
 */
public class Tips implements EntryPoint {
    static final Console L = console;

    @Override public void onModuleLoad() {
        callbackHell();
        callbackUnifier();
    }

    private void callbackUnifier() {//@formatter:off
        // how many callbacks exits in your app? did you include errors in all of them, did you even have listeners
        // (means, and handler with more than one success path, like the old MouseListener with onMove, onClick, etc.
        // in just one "callback")?
        // >> JDK <<
        Runnable javaThreadTarget = () -> L.log("no arguments, no error path");
        // new Thread(javaThreadTarget)
        Consumer<String> java8Action = success -> L.log("from the sdk, and looks good, but and standard at all");
        Stream.of("a","b","c").forEach(java8Action);
        BiConsumer<String, Throwable> java8Callback = (success, error) -> L.log("nice, but also no one use this");

        // GWT Schedulers, various callbacks, not easy to combine or specify timing operations like a timeout!
        gwtScheduler().scheduleDeferred((ScheduledCommand) () -> L.log("one time command done!"));
        gwtScheduler().scheduleIncremental((RepeatingCommand) () -> {L.log("repeating command done!"); return false;});
        // to use RX first just wrap the task in a RX type, for example a log call into a Completable
        Completable rxTask = Completable.fromAction(() -> L.log("one time command done!")); // by default synchronous
        // with RX you can specify in which scheduler do you want to execute the task
        rxTask.subscribeOn(GwtSchedulers.deferredScheduler()); // async using a deferred scheduler
        rxTask.subscribeOn(GwtSchedulers.incrementalScheduler()); // async using a incremental scheduler
        rxTask.subscribeOn(Schedulers.io()); // GWT agnostic, but yep, this is mapped to deferred
        rxTask.subscribeOn(Schedulers.computation()); // and this one to is mapped to incremental
        // remember that this is a chained description, so you should save the instance, like this
        rxTask.subscribeOn(Schedulers.io()).subscribe(() -> L.log("task executed async!"));

        // for repeating tasks like a timer
        new Timer() { public void run() { L.log("whOOt? inheritance instead of composition?!");} }.schedule(100);
        // you should generate stream of ticks, called 'interval' (timer exists, but just emmit 1 tick)
        Observable.interval(100, MILLISECONDS).flatMapCompletable(n -> rxTask);

        // and a final example, if the web is online (and stop if not) do a task each 5min
        online().switchMap(online -> online ? Observable.interval(5, MINUTES) : Observable.never())
                // fetching a big list of data, so big that need to be reduced incrementally to no block the
                // main loop, as our API is RX friendly, just observe each result item in the computation scheduler
                .flatMapSingle(refresh -> requestData().observeOn(computation())
                        // and reduce each item here, until the whole response is processed
                        .<Set<String>>reduceWith(HashSet::new, (acc,n) -> { acc.add(n); return acc; }))
                // at that point the response has been processed incrementally!
                .doOnNext(result -> GWT.log("safe the retrieved and processed result: " + result))
                // if something goes wrong, wait 1 minute and try again, the try will reconnect the whole observable
                // so if the web is offline, it will not try to process again until it get online!
                .retryWhen(at -> at.flatMapSingle(ex -> { GWT.log("updater error", ex); return timer(1, MINUTES); }))
                .subscribe(); // eventually we'll see that subscribe responsibility can be delegated! (safer!)


        // GWT events
        Button btn = new Button();
        // rxgwt contains utilities mappings all HasHandler interfaces
        btn.addClickHandler(new ClickHandler() { public void onClick(ClickEvent e) {L.log("click!");}});
        RxHandlers.click(btn).subscribe(ev -> L.log("click!"));
        // all Event types, so you can add it to anything extending Widget
        btn.addDomHandler(new FocusHandler(){public void onFocus(FocusEvent e){L.log("focus!");}},FocusEvent.getType());
        RxEvents.focus(btn).subscribe(ev -> L.log("focus!"));
        // or you can just wrap the event stream using Observable.create, like for custom events used in an event bus
        EventBus bus = new SimpleEventBus();
        bus.addHandler(ResizeEvent.getType(),new ResizeHandler(){public void onResize(ResizeEvent e) {L.log("rsz!");}});
        // you need to wrap it as a observable, usually you will save and reuse this wrapper
        Observable.create(em -> em.setCancellable(bus.addHandler(ResizeEvent.getType(), em::onNext)::removeHandler))
            .subscribe(ev -> L.log("resize!"));
        // but, at this point you might even stop using event bus and create a Subject instead
        PublishSubject<String> rxBus = PublishSubject.create();
        rxBus.subscribe(ev -> L.log("rxBus received event: " + ev)); // you can listen to the eventBus events
        rxBus.onNext("fire an event!"); // and fire events to the event bus


    }

    public void callbackHell() {
        // callback hell (handling errors is so cumbersome that cannot even be included in this compact example)
        doAsync(1, success1 -> {
            doAsync(2, success2 -> {
                doAsync(3, success3 -> {
                    out(success1 + success2 + success3);
                });
            });
        });
        // wrapping async methods in RX is supper easy, and it's ok to doAsync throw an exception!
        Function<Integer, Single<String>> rx = n -> Single.create(em -> doAsync(n, em::onSuccess));
        Single<String> rx1 = rx.apply(1), rx2 = rx.apply(2), rx3 = rx.apply(3); // reusable 👍, not a Promise 😜
        // now you can use many strategies to compose, like creating a flow of all the results
        Single.concat(rx1, rx2, rx3).reduce((n, m) -> n + m).subscribe(this::out);
        // or if you don't care about the order, do it in parallel (not easy with callbacks 😬)
        Single.merge(rx1, rx2, rx3).reduce((n, m) -> n + m).subscribe(this::out);
        // or just chain each async task one each other combining the results
        rx1.flatMap(n -> rx2.map(m -> n + m)).flatMap(n -> rx3.map(m -> n + m)).subscribe(this::out);
        // in all this examples error are unified and can be handled in many ways, the basic one being
        Single.merge(rx1, rx2, rx3).reduce((n, m) -> n + m).subscribe(this::out, err -> {/*handle any error*/});
        // but you can always handle it per source, so for example if rx1 is not so important you can do
        Single.merge(rx1.onErrorReturnItem("it's fine"), rx2, rx3).reduce((n, m) -> n + m).subscribe(this::out);
        // so if rx1 fail it wont kill the whole operation, how do yo make it (and so descriptive) with callbacks?
    }

    // just to make example works
    public void out(Object obj) { L.log(obj); }
    public void doAsync(int num, Consumer<String> fn) { fn.accept("done [" + num + "]!"); }
    public static Scheduler gwtScheduler() { return Scheduler.get(); }
    public static Observable<Boolean> online() { return Observable.never(); }
    public static Flowable<String> requestData() { return Flowable.never(); }
}
