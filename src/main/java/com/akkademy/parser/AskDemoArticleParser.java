package com.akkademy.parser;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
import com.akkademy.message.parse.ArticleBody;
import com.akkademy.message.parse.ParseArticle;
import com.akkademy.message.parse.ParseHtmlArticle;
import message.GetRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.pattern.Patterns.ask;
import static scala.compat.java8.FutureConverters.toJava;

public class AskDemoArticleParser extends AbstractActor {

    private final ActorSelection cacheActor;
    private final ActorSelection httpClientActor;
    private final ActorSelection articleParseActor;
    private final Timeout timeout;

    public AskDemoArticleParser(String cacheActorPath, String httpClientActorPath,
                                String articleParseActorPath, Timeout timeout) {
        this.cacheActor = getContext().actorSelection(cacheActorPath);
        this.httpClientActor = getContext().actorSelection(httpClientActorPath);
        this.articleParseActor = getContext().actorSelection(articleParseActorPath);
        this.timeout = timeout;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ParseArticle.class, message -> {
                    final CompletionStage cacheResult = toJava(ask(cacheActor, new GetRequest(message.getUrl()), timeout));
                    final CompletionStage result = cacheResult.handle((x, t) -> {
                        return (x != null) ?
                                CompletableFuture.completedFuture(x) :
                                toJava(ask(httpClientActor, message.getUrl(), timeout))
                                        .thenCompose(rawArticle -> toJava(ask(articleParseActor, new ParseHtmlArticle(message.getUrl(),
                                                ((HttpResponse) rawArticle).toString()), timeout)));
                    }).thenCompose(x -> x);
                    final ActorRef senderRef = getSender();
                    result.handle((x, t) -> {
                        if (x != null) {
                            if (x instanceof ArticleBody) {
                                String body = ((ArticleBody) x).getBody();
                                cacheActor.tell(body, self());
                                senderRef.tell(body, self());
                            } else if (x instanceof String)
                                senderRef.tell(x, self());
                        } else
                            senderRef.tell(new akka.actor.Status.
                                    Failure((Throwable) t), self());
                        return null;
                    });
                }).matchAny(o -> getSender()
                        .tell(new Status.Failure(new Exception("Unknown message")), getSelf()))
                .build();
    }
}
