package com.akkademy.parser.tell;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActor;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.akkademy.message.parse.ArticleBody;
import com.akkademy.message.parse.ParseArticle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.mockito.Mockito;

public class ArticleParserTest extends Mockito {

    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = akka.actor.ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void shouldReturnCachedArticle() {
        new TestKit(system) {{
            final TestProbe cacheActorProbe = TestProbe.apply(system);
            cacheActorProbe.setAutoPilot(new TestActor.AutoPilot() {
                public TestActor.AutoPilot run(ActorRef sender, Object msg) {
                    sender.tell("parsedArticle", ActorRef.noSender());
                    return noAutoPilot();
                }
            });

            final ActorRef cacheActorRef = cacheActorProbe.ref();

            final Props props = Props.create(ArticleParser.class, cacheActorRef.path().toString(), "", "", new Timeout(Duration.create(3, "second")));
            final ActorRef subject = system.actorOf(props);

            subject.tell(new ParseArticle("http://test.com/article"), getRef());
            expectMsgEquals("parsedArticle");
        }};
    }

    @Test
    public void shouldReturnHttpArticle() {
        new TestKit(system) {{
            final TestProbe cacheActorProbe = TestProbe.apply(system);
            cacheActorProbe.setAutoPilot(new TestActor.AutoPilot() {
                public TestActor.AutoPilot run(ActorRef sender, Object msg) {
                    sender.tell(new Exception(), ActorRef.noSender());
                    return noAutoPilot();
                }
            });
            final ActorRef cacheActorRef = cacheActorProbe.ref();

            HttpResponse httpResponse = mock(HttpResponse.class);
            when(httpResponse.toString()).thenReturn("Http article to parse");
            final TestProbe httpActorProbe = TestProbe.apply(system);
            httpActorProbe.setAutoPilot(new TestActor.AutoPilot() {
                public TestActor.AutoPilot run(ActorRef sender, Object msg) {
                    sender.tell(httpResponse, ActorRef.noSender());
                    return noAutoPilot();
                }
            });
            final ActorRef httpActorRef = httpActorProbe.ref();

            final TestProbe parserActorProbe = TestProbe.apply(system);
            parserActorProbe.setAutoPilot(new TestActor.AutoPilot() {
                public TestActor.AutoPilot run(ActorRef sender, Object msg) {
                    sender.tell(new ArticleBody("http://test.com/article", "Parsed article"), ActorRef.noSender());
                    return noAutoPilot();
                }
            });
            final ActorRef parserActorRef = parserActorProbe.ref();

            final Props props = Props.create(ArticleParser.class,
                    cacheActorRef.path().toString(), httpActorRef.path().toString(),
                    parserActorRef.path().toString(), new Timeout(Duration.create(3, "second")));
            final ActorRef subject = system.actorOf(props);

            subject.tell(new ParseArticle("http://test.com/article"), getRef());
            expectMsgEquals("Parsed article");
        }};
    }
}
