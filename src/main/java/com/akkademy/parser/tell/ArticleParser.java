package com.akkademy.parser.tell;

import akka.actor.*;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
import com.akkademy.message.parse.ArticleBody;
import com.akkademy.message.parse.ParseArticle;
import com.akkademy.message.parse.ParseHtmlArticle;
import message.GetRequest;
import message.SetRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

public class ArticleParser extends AbstractActor {

    private ActorRef cacheActor;
    private final ActorSelection httpClientActor;
    private final ActorSelection articleParseActor;
    private final Timeout timeout;

    public ArticleParser(String cacheActorPath, String httpClientActorPath,
                         String articleParseActorPath, Timeout timeout) {
        this.cacheActor = getContext().actorFor(cacheActorPath);
        this.httpClientActor = getContext().actorSelection(httpClientActorPath);
        this.articleParseActor = getContext().actorSelection(articleParseActorPath);
        this.timeout = timeout;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ActorRef.class, message -> {
                    this.cacheActor = message;
                    getSender().tell("done", getSelf());
                })
                .match(ParseArticle.class, message -> {
                    ActorRef extraActor = buildExtraActor(sender(),
                            message.getUrl());
                    cacheActor.tell(new GetRequest(message.getUrl()),
                            extraActor);
                    httpClientActor.tell(message.getUrl(), extraActor);
                    context().system().scheduler().
                            scheduleOnce(timeout.duration(), extraActor, "timeout", context().system().
                                    dispatcher(), ActorRef.noSender());
                })
                .matchAny(t -> {
                    System.out.println("ignoring msg: " + t.getClass());
                }).build();
    }

    private ActorRef buildExtraActor(ActorRef senderRef, String uri) {
        class ExtraActor extends AbstractActor {

            public final ActorRef senderReference;
            public final String articleUri;

            public ExtraActor() {
                this.senderReference = senderRef;
                this.articleUri = uri;
            }

            @Override
            public Receive createReceive() {
                return ReceiveBuilder.create()
                        .matchEquals(String.class, x -> x.equals("timeout"), x -> {
                            senderReference.tell(new Status.Failure(new TimeoutException("timeout!")), self());
                            context().stop(self());
                        })
                        .match(HttpResponse.class, httpResponse -> {
                            articleParseActor.tell(new ParseHtmlArticle(articleUri, httpResponse.toString()), self());
                        })
                        .match(String.class, body -> {
                            senderReference.tell(body, self());
                            context().stop(self());
                        })
                        .match(ArticleBody.class, articleBody -> {
                            cacheActor.tell(new SetRequest(articleBody.getUri(), articleBody.getBody()), self());
                            senderReference.tell(articleBody.getBody(), self());
                            context().stop(self());
                        })
                        .matchAny(t -> {
                            System.out.println("ignoring msg: " + t.getClass());
                        })
                        .build();
            }
        }
        return context().actorOf(Props.create(ExtraActor.class, () -> new ExtraActor()));
    }
}
