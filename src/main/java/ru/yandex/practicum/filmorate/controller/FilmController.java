package ru.yandex.practicum.filmorate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.filmorate.exception.IncorrectIdException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.service.FilmService;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import java.util.List;

@RestController
@RequestMapping("/films")
@Validated
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class FilmController {

    private final FilmService filmService;
    private final String topFilmsSize = "10";


    // FILMS
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Film postFilm(@Valid @RequestBody Film film) {

        // validation
        boolean filmIdNotEmpty = (film.getId() != null);
        if (filmIdNotEmpty) {
            throw new IncorrectIdException("Film ID must be empty");
        }

        // add film
        return filmService.addFilm(film);

    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public Film putFilm(@Valid @RequestBody Film film) {

        // validation
        boolean filmIdEmpty = (film.getId() == null);
        if (filmIdEmpty) {
            throw new IncorrectIdException("Film ID must be not empty");
        }

        // update film
        return filmService.updateFilm(film);

    }

    @GetMapping("/{id}")
    public Film getFilm(@PathVariable @Positive Long id) {

        return filmService.getFilm(id);

    }

    @GetMapping
    public List<Film> getFilms() {

        return filmService.getFilms();

    }

    @DeleteMapping("/{filmId}")
    public void deleteFilmById(@PathVariable Long filmId) {
        filmService.deleteFilmById(filmId);
    }

    // LIKES
    @PutMapping("{id}/like/{userId}") // idempotent
    public void putLike(@PathVariable @Positive Long id, @PathVariable @Positive Long userId) {

        filmService.addLike(id, userId);

    }

    @DeleteMapping("{id}/like/{userId}") // not idempotent
    public void deleteLike(@PathVariable @Positive Long id, @PathVariable /*@Positive*/ Long userId) {

        filmService.deleteLike(id, userId);

    }

    @GetMapping("/popular")
    public List<Film> getMostPopularFilms(@RequestParam(defaultValue = "10", required = false) Integer count,
                                          @RequestParam(required = false) Integer genreId,
                                          @RequestParam(required = false) Integer year) {
        return filmService.getMostPopularFilms(count, genreId, year);
    }

    @GetMapping("/common")
    public List<Film> getCommonFilmsWithFriend(@RequestParam("userId") Long userId, @RequestParam("friendId") Long friendId) {

        return filmService.getCommonFilmsWithFriend(userId, friendId);

    }

    // DIRECTOR
    @GetMapping("/director/{directorId}")
    public List<Film> getFilmsByDirector(@PathVariable @Positive Long directorId,
                                         @RequestParam @Pattern(regexp = "year|likes") String sortBy) {

        return filmService.getFilmsByDirector(directorId, sortBy);

    }

    // SEARCH
    @GetMapping("/search")
    public List<Film> searchInFilmsAndDirectors(@RequestParam String query,
                                                @RequestParam String[] by) {
        return filmService.search(query, by);
    }

}
