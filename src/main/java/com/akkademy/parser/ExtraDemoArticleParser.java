package com.akkademy.parser;

import akka.actor.*;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
import com.akkademy.message.parse.ArticleBody;
import com.akkademy.message.parse.ParseArticle;
import com.akkademy.message.parse.ParseHtmlArticle;
import message.GetRequest;
import message.SetRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.concurrent.TimeoutException;

public class ExtraDemoArticleParser extends AbstractActor {

    private final ActorSelection cacheActor;
    private final ActorSelection httpClientActor;
    private final ActorSelection articleParseActor;
    private final Timeout timeout;

    public ExtraDemoArticleParser(String cacheActorPath, String httpClientActorPath,
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
                    ActorRef extraActor = buildExtraActor(sender(),
                            message.getUrl());
                    cacheActor.tell(new GetRequest(message.getUrl()),
                            extraActor);
                    httpClientActor.tell(message.getUrl(), extraActor);
                    context().system().scheduler().
                            scheduleOnce(timeout.duration(), extraActor, "timeout", context().system().
                                    dispatcher(), ActorRef.noSender());
                }).build();
    }

    private ActorRef buildExtraActor(ActorRef senderRef, String uri) {
        class MyActor extends AbstractActor {
            @Override
            public Receive createReceive() {
                return ReceiveBuilder.create()
                        .matchEquals(String.class, x -> x.equals("timeout"), x -> {
                            senderRef.tell(new Status.Failure(new TimeoutException("timeout!")), self());
                            context().stop(self());
                        })
                        .match(HttpResponse.class, httpResponse -> {
                            articleParseActor.tell(new ParseHtmlArticle(uri, httpResponse.toString()), self());
                        })
                        .match(String.class, body -> {
                            senderRef.tell(body, self());
                            context().stop(self());
                        })
                        .match(ArticleBody.class, articleBody -> {
                            cacheActor.tell(new SetRequest(articleBody.getUri(), articleBody.getBody()), self());
                            senderRef.tell(articleBody.getBody(), self());
                            context().stop(self());
                        })
                        .matchAny(t -> {
                            System.out.println("ignoring msg: " + t.getClass());
                        })
                        .build();
            }
        }
        return context().actorOf(Props.create(MyActor.class, MyActor::new));
    }
}
