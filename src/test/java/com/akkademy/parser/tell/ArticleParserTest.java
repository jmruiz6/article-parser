package com.akkademy.parser.tell;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActor;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.akkademy.message.parse.ParseArticle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

public class ArticleParserTest {

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
    public void testIt() {
        new TestKit(system) {{
            final Props props = Props.create(ArticleParser.class, "", "", "", new Timeout(Duration.create(3, "second")));
            final ActorRef subject = system.actorOf(props);
            final TestProbe cacheActorProbe = TestProbe.apply(system);
            cacheActorProbe.setAutoPilot(new TestActor.AutoPilot() {
                public TestActor.AutoPilot run(ActorRef sender, Object msg) {
                    sender.tell("parsedArticle", ActorRef.noSender());
                    return noAutoPilot();
                }
            });

            final ActorRef cacheActorRef = cacheActorProbe.ref();
            final TestKit httpClientActorProbe = new TestKit(system);
            final TestKit articleParseActorProbe = new TestKit(system);

            subject.tell(cacheActorRef, getRef());
            expectMsg(duration("1 second"), "done");

            subject.tell(new ParseArticle("http://test.com/article"), getRef());
            expectMsgEquals("parsedArticle");
        }};
    }
}
