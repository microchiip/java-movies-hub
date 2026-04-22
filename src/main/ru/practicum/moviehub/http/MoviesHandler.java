package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        if (path.equals("/movies") || path.equals("/movies/")) {
            handleCollection(ex, method);
        } else if (path.startsWith("/movies/")) {
            String idStr = path.substring("/movies/".length());
            handleById(ex, method, idStr);
        } else {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Не найдено")));
        }
    }

    private void handleCollection(HttpExchange ex, String method) throws IOException {
        if ("GET".equalsIgnoreCase(method)) {
            String query = ex.getRequestURI().getQuery();
            if (query != null) {
                String yearValue = getQueryParam(query, "year");
                if (yearValue != null) {
                    handleFilterByYear(ex, yearValue);
                } else {
                    sendJson(ex, 200, gson.toJson(store.findAll()));
                }
            } else {
                sendJson(ex, 200, gson.toJson(store.findAll()));
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            handleCreate(ex);
        } else {
            ex.getResponseHeaders().set("Allow", "GET, POST");
            sendJson(ex, 405, gson.toJson(new ErrorResponse("Метод не поддерживается")));
        }
    }

    private void handleFilterByYear(HttpExchange ex, String yearStr) throws IOException {
        try {
            int year = Integer.parseInt(yearStr);
            sendJson(ex, 200, gson.toJson(store.findByYear(year)));
        } catch (NumberFormatException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный параметр запроса — 'year'")));
        }
    }

    private void handleCreate(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendJson(ex, 415, gson.toJson(new ErrorResponse("Неподдерживаемый тип содержимого")));
            return;
        }

        String body;
        try (InputStream is = ex.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse("Некорректный JSON")));
            return;
        }

        if (movie == null) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse("Некорректный JSON")));
            return;
        }

        List<String> errors = new ArrayList<>();
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название не должно превышать 100 символов");
        }

        int currentYear = Year.now().getValue();
        if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            errors.add("год должен быть между 1888 и " + (currentYear + 1));
        }

        if (!errors.isEmpty()) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse("Ошибка валидации", errors)));
            return;
        }

        sendJson(ex, 201, gson.toJson(store.add(movie)));
    }

    private void handleById(HttpExchange ex, String method, String idStr) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            var found = store.findById(id);
            if (found.isPresent()) {
                sendJson(ex, 200, gson.toJson(found.get()));
            } else {
                sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
            }
        } else if ("DELETE".equalsIgnoreCase(method)) {
            if (store.delete(id)) {
                sendNoContent(ex);
            } else {
                sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
            }
        } else {
            ex.getResponseHeaders().set("Allow", "GET, DELETE");
            sendJson(ex, 405, gson.toJson(new ErrorResponse("Метод не поддерживается")));
        }
    }

    private String getQueryParam(String query, String name) {
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                return pair[1];
            }
        }
        return null;
    }
}