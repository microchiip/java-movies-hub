package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    static MoviesStore store;
    static MoviesServer server;
    static HttpClient client;
    static Gson gson;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newHttpClient();
        gson = new Gson();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    //GET /movies

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        var response = get("/movies");

        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body().trim());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    @Test
    void getMovies_withMovies_returnsList() throws Exception {
        addMovie("Inception", 2010);

        var response = get("/movies");

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(1, movies.size());
        assertEquals("Inception", movies.get(0).getTitle());
    }

    //POST /movies

    @Test
    void postMovies_withValidData_createsMovie() throws Exception {
        var response = post("/movies", "{\"title\":\"The Matrix\",\"year\":1999}", "application/json");

        assertEquals(201, response.statusCode());
        Movie movie = gson.fromJson(response.body(), Movie.class);
        assertEquals("The Matrix", movie.getTitle());
        assertEquals(1999, movie.getYear());
        assertTrue(movie.getId() > 0);
    }

    @Test
    void postMovies_withEmptyTitle_returns422() throws Exception {
        var response = post("/movies", "{\"title\":\"\",\"year\":2000}", "application/json");

        assertEquals(422, response.statusCode());
    }

    @Test
    void postMovies_withTitleTooLong_returns422() throws Exception {
        String longTitle = "A".repeat(101);
        var response = post("/movies", "{\"title\":\"" + longTitle + "\",\"year\":2000}", "application/json");

        assertEquals(422, response.statusCode());
    }

    @Test
    void postMovies_withYearTooSmall_returns422() throws Exception {
        var response = post("/movies", "{\"title\":\"Old\",\"year\":1887}", "application/json");

        assertEquals(422, response.statusCode());
    }

    @Test
    void postMovies_withYearTooBig_returns422() throws Exception {
        int tooFar = java.time.Year.now().getValue() + 2;
        var response = post("/movies", "{\"title\":\"Future\",\"year\":" + tooFar + "}", "application/json");

        assertEquals(422, response.statusCode());
    }

    @Test
    void postMovies_withWrongContentType_returns415() throws Exception {
        var response = post("/movies", "{\"title\":\"Test\",\"year\":2000}", "text/plain");

        assertEquals(415, response.statusCode());
    }

    @Test
    void postMovies_withInvalidJson_returns422() throws Exception {
        var response = post("/movies", "not json at all", "application/json");

        assertEquals(422, response.statusCode());
    }

    //GET /movies/{id}

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        Movie created = gson.fromJson(addMovie("Interstellar", 2014).body(), Movie.class);

        var response = get("/movies/" + created.getId());

        assertEquals(200, response.statusCode());
        Movie found = gson.fromJson(response.body(), Movie.class);
        assertEquals(created.getId(), found.getId());
        assertEquals("Interstellar", found.getTitle());
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        var response = get("/movies/9999");

        assertEquals(404, response.statusCode());
    }

    @Test
    void getMovieById_withNonNumericId_returns400() throws Exception {
        var response = get("/movies/abc");

        assertEquals(400, response.statusCode());
    }

    //DELETE /movies/{id}

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        Movie created = gson.fromJson(addMovie("Avatar", 2009).body(), Movie.class);

        var response = delete("/movies/" + created.getId());

        assertEquals(204, response.statusCode());
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        var response = delete("/movies/9999");

        assertEquals(404, response.statusCode());
    }

    @Test
    void deleteMovie_withNonNumericId_returns400() throws Exception {
        var response = delete("/movies/xyz");

        assertEquals(400, response.statusCode());
    }

    //GET /movies?year=YYYY

    @Test
    void getMoviesByYear_returnsFilteredMovies() throws Exception {
        addMovie("Old Movie", 2000);
        addMovie("Recent Movie", 2023);

        var response = get("/movies?year=2000");

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(1, movies.size());
        assertEquals("Old Movie", movies.get(0).getTitle());
    }

    @Test
    void getMoviesByYear_whenNoMatch_returnsEmpty() throws Exception {
        addMovie("Movie", 2020);

        var response = get("/movies?year=1990");

        assertEquals(200, response.statusCode());
        List<Movie> movies = gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMoviesByYear_withInvalidYear_returns400() throws Exception {
        var response = get("/movies?year=abc");

        assertEquals(400, response.statusCode());
    }


    @Test
    void successfulResponses_haveCorrectContentType() throws Exception {
        var response = get("/movies");

        String ct = response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", ct);
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/movies"))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }


    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080" + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String contentType) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080" + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080" + path))
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> addMovie(String title, int year) throws Exception {
        return post("/movies", "{\"title\":\"" + title + "\",\"year\":" + year + "}", "application/json");
    }
}