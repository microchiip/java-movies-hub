package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    public Movie add(Movie movie) {
        int id = idCounter.incrementAndGet();
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public Optional<Movie> findById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public List<Movie> findAll() {
        return new ArrayList<>(movies.values());
    }

    public boolean delete(int id) {
        return movies.remove(id) != null;
    }

    public List<Movie> findByYear(int year) {
        return movies.values().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());
    }

    public void clear() {
        movies.clear();
        idCounter.set(0);
    }
}