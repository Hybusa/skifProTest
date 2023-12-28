package org.example.endpoints;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.BasicHttpCredentials;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import org.example.actor.UserRegistry;
import org.junit.*;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UserRoutesTest extends JUnitRouteTest {

    @ClassRule
    public static TestKitJunitResource testkit = new TestKitJunitResource();
    private static ActorRef<UserRegistry.Command> userRegistry;
    private TestRoute appRoute;

    @BeforeClass
    public static void beforeClass() {
        userRegistry = testkit.spawn(UserRegistry.create());
    }

    @Before
    public void before() {
        UserRoutes userRoutes = new UserRoutes(testkit.system(), userRegistry);
        appRoute = testRoute(userRoutes.apiRoutes());
    }
    @AfterClass
    public static void afterClass() {
        testkit.stop(userRegistry);
    }

    @Test
    public void test1Registrate_success() {
        appRoute.run(HttpRequest.POST("/api_v1/registrate")
                        .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\"email\": \"mshevelevich@gmail.com\",\"password\": \"himmih1234\",\"name\": \"Миша\"}"))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json")
                .assertEntity("{}");
    }

    @Test
    public void test2RegistrateDupe_Fail() {
        appRoute.run(HttpRequest.POST("/api_v1/registrate")
                        .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                                "{\"email\": \"mshevelevich@gmail.com\",\"password\": \"himmih1234\",\"name\": \"Миша\"}"))
                .assertStatusCode(StatusCodes.UNPROCESSABLE_CONTENT)
                .assertMediaType("application/json")
                .assertEntity("{\"error\":\"session.errors.emailAlreadyRegistered\"}");
    }

    //#actual-test
    //#testing-post
    @Test
    public void test4login_success() {
        appRoute.run(HttpRequest.POST("/api_v1/login")
                        .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                                "{\"email\": \"mshevelevich@gmail.com\", \"password\": \"himmih1234\"}"))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json")
                .assertEntity("{}");
    }

    @Test
    public void test5login_fail() {
        appRoute.run(HttpRequest.POST("/api_v1/login")
                        .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                                "{\"email\": \"Kapi\", \"password\": \"123321\"}"))
                .assertStatusCode(StatusCodes.UNPROCESSABLE_CONTENT)
                .assertMediaType("application/json")
                .assertEntity("{\"error\":\"session.errors\"}");
    }

    @Test
    public void test6me_noCredentials_Fail(){
        appRoute.run(HttpRequest.POST("/api_v1/me"))
                .assertStatusCode(StatusCodes.UNAUTHORIZED);
    }

    @Test
    public void test7me_invalidCredentials_Fail(){
        final HttpCredentials invalidCredentials =
                BasicHttpCredentials.createBasicHttpCredentials("John", "p4ssw0rd");
        appRoute.run(HttpRequest.POST("/api_v1/me")
                        .addCredentials(invalidCredentials))
                .assertStatusCode(StatusCodes.UNAUTHORIZED);
    }

    @Test
    public void test8me_validCredentials_Success(){
        final HttpCredentials validCredentials =
                BasicHttpCredentials.createBasicHttpCredentials("mshevelevich@gmail.com", "himmih1234");
        appRoute.run(HttpRequest.POST("/api_v1/me")
                        .addCredentials(validCredentials))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json");
    }

    @Test
    public void test9logout_Success(){
        final HttpCredentials validCredentials =
                BasicHttpCredentials.createBasicHttpCredentials("mshevelevich@gmail.com", "himmih1234");
        appRoute.run(HttpRequest.PUT("/api_v1/logout")
                        .addCredentials(validCredentials))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json")
                .assertEntity("{}");
    }
}
