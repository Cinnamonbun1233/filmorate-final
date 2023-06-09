package ru.yandex.practicum.filmorate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.filmorate.exception.IncorrectIdException;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.service.FilmService;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.List;

@RestController
@RequestMapping("/genres")
@Validated
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class GenreController {

    private final FilmService filmService;

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public Genre postGenre(@Valid @RequestBody Genre genre) {

        // validation
        boolean genreIdNotEmpty = (genre.getId() != null);
        if (genreIdNotEmpty) {
            throw new IncorrectIdException("Genre ID must be empty");
        }

        // add film
        return filmService.addGenre(genre);

    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public Genre putGenre(@RequestBody Genre genre) {

        // validation
        boolean genreIdEmpty = (genre.getId() == null);
        if (genreIdEmpty) {
            throw new IncorrectIdException("Genre ID must be not empty");
        }

        // update film
        return filmService.updateGenre(genre);

    }

    @GetMapping("/{id}")
    public Genre getGenre(@PathVariable @Positive Long id) {

        return filmService.getGenre(id);

    }

    @GetMapping
    public List<Genre> getGenres() {

        return filmService.getGenres();

    }

}