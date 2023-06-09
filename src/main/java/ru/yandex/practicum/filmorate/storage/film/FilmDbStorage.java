package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.model.Director;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class FilmDbStorage implements FilmStorage {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    // FILMS - CRUD
    @Override
    public Film addFilm(Film film) {

        Mpa mpa = Mpa.getMpa(film.getMpa());
        Long mpaId = (mpa == null ? null : mpa.getId());

        // film
        Map<String, Object> filmFields = new HashMap<>();
        filmFields.put("name", film.getName());
        filmFields.put("description", film.getDescription());
        filmFields.put("release_date", film.getReleaseDate());
        filmFields.put("duration", film.getDuration());
        filmFields.put("rate", film.getRate());
        filmFields.put("mpa", mpaId);

        SimpleJdbcInsert simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("FILM")
                .usingGeneratedKeyColumns("id");

        Long filmId = simpleJdbcInsert.executeAndReturnKey(filmFields).longValue();
        film.setId(filmId);

        updateGenres(film);
        updateDirectors(film);

        return film;

    }

    @Override
    public Film updateFilm(Film film) {

        // film
        String sqlQuery = "UPDATE film SET name = ?, description = ?, release_date = ?, duration = ?" +
                ", rate = ?, mpa = ? WHERE id = ?";

        Mpa mpa = Mpa.getMpa(film.getMpa());
        Long mpaId = (mpa == null ? null : mpa.getId());

        jdbcTemplate.update(sqlQuery,
                film.getName(),
                film.getDescription(),
                film.getReleaseDate(),
                film.getDuration(),
                film.getRate(),
                mpaId,
                film.getId());

        updateGenres(film);
        updateDirectors(film);

        return film;

    }

    @Override
    public Film getFilm(Long id) {

        String sqlQuery = "SELECT id, name, description, release_date, duration, rate, mpa FROM film WHERE id = ?";

        try {
            // film
            Film film = jdbcTemplate.queryForObject(sqlQuery, (rs, rowNum) -> makeFilm(rs), id);
            linkGenresToFilms(List.of(film));
            linkDirectorsToFilms(List.of(film));
            return film;
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        }

    }

    @Override
    public List<Film> getFilms() {

        // films
        String sqlQuery = "SELECT id, name, description, release_date, duration, rate, mpa FROM film ORDER BY id";
        List<Film> filmList = jdbcTemplate.query(sqlQuery, (rs, rowNum) -> makeFilm(rs));

        linkGenresToAllFilms(filmList);
        linkDirectorsToAllFilms(filmList);

        return filmList;

    }

    @Override
    public void deleteFilmById(Long filmId) {
        String filmSqlQuery = "DELETE FROM FILM WHERE ID = ?";
        jdbcTemplate.update(filmSqlQuery, filmId);
    }

    @Override
    public List<Film> getFilms(List<Long> filmIds) {

        String sqlQuery = "SELECT id, name, description, release_date, duration, rate, mpa FROM film WHERE id IN (%s) ORDER BY id";
        String sqlParam = String.join(",", Collections.nCopies(filmIds.size(), "?"));
        sqlQuery = String.format(sqlQuery, sqlParam);
        List<Film> filmList = jdbcTemplate.query(sqlQuery, (rs, rowNum) -> makeFilm(rs), filmIds.toArray());

        linkGenresToFilms(filmList);
        linkDirectorsToFilms(filmList);

        return filmList;

    }


    // FILMS - Checking
    @Override
    public boolean hasTwin(Film film) {

        boolean newFilm = (film.getId() == null);

        String sql;
        Object[] params;

        if (newFilm) {
            sql = "SELECT count(*) FROM film WHERE name = ? AND description = ? AND release_date = ?";
            params = new Object[]{film.getName(), film.getDescription(), film.getReleaseDate()};
        } else {
            sql = "SELECT count(*) FROM film WHERE name = ? AND description = ? AND release_date = ? AND id <> ?";
            params = new Object[]{film.getName(), film.getDescription(), film.getReleaseDate(), film.getId()};
        }
        int count = jdbcTemplate.queryForObject(sql, Integer.class, params);

        return (count > 0);

    }

    @Override
    public List<Film> getMostPopularFilms(Integer count, Integer genre, Integer year) {
        final StringBuilder sqlBuilder = new StringBuilder(
                "SELECT * " +
                        "FROM film " +
                        "LEFT JOIN filmorate_like AS fl ON film.id = fl.film_id "
        );
        if (genre != null && year != null) {
            sqlBuilder.append(
                    "WHERE id IN (SELECT film_id FROM film_genre WHERE genre_id = :genre) " +
                            "AND EXTRACT(YEAR FROM cast(release_date AS date)) = :year ");
        }
        if (genre != null && year == null) {
            sqlBuilder.append(
                    "WHERE id IN (SELECT film_id FROM film_genre WHERE genre_id = :genre) ");
        }
        if (genre == null && year != null) {
            sqlBuilder.append(
                    "WHERE EXTRACT(YEAR FROM cast(release_date AS date)) = :year ");
        }
        sqlBuilder.append(
                "GROUP BY film.id, fl.film_id, fl.user_id " +
                        "ORDER BY COUNT(fl.film_id) DESC " +
                        "LIMIT :count ;"
        );

        final String sql = sqlBuilder.toString();
        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource("count", count)
                .addValue("genre", genre)
                .addValue("year", year);
        List<Film> filmList = namedParameterJdbcTemplate.query(sql, mapSqlParameterSource,
                (rs, rowNum) -> makeFilm(rs));
        linkGenresToAllFilms(filmList);
        linkDirectorsToAllFilms(filmList);
        return filmList;
    }

    @Override
    public List<Film> getCommonFilmsWithFriend(Long userId, Long friendId) {

        String sqlQuery = "WITH common AS (SELECT film_id FROM FILMORATE_LIKE " +
                "WHERE user_id = ? AND film_id IN (SELECT film_id FROM FILMORATE_LIKE " +
                "WHERE user_id = ?)) " +
                "SELECT id, name, description, release_date, duration, rate, MPA,COUNT(user_id) as likes " +
                "FROM FILM as f " +
                "LEFT JOIN FILMORATE_LIKE fl ON f.id=fl.film_id " +
                "WHERE id IN (SELECT * FROM common) " +
                "GROUP BY f.id " +
                "ORDER BY likes DESC";

        List<Film> filmList = jdbcTemplate.query(sqlQuery, (rs, rowNum) -> makeFilm(rs), userId, friendId);
        linkGenresToFilms(filmList);
        return filmList;
    }

    @Override
    public List<Film> getFilmsByDirector(Long directorId, String sortBy) {

        String sqlQueryNoSort = "SELECT f.id, f.name, f.description, f.release_date, f.duration, f.rate, f.mpa " +
                "FROM film f " +
                "LEFT JOIN FILM_DIRECTOR fd ON f.id = fd.film_id WHERE fd.director_id = ?";

        String sqlQuerySortByYear = sqlQueryNoSort + " ORDER BY f.release_date ASC";

        String sqlQuerySortByLikes = "SELECT f.id, f.name, f.description, f.release_date, f.duration, f.rate, f.mpa " +
                "FROM film f " +
                "LEFT JOIN film_director fd ON f.id = fd.film_id " +
                "LEFT JOIN filmorate_like fl ON f.id = fl.film_id " +
                "WHERE fd.director_id = ? " +
                "GROUP BY f.id, f.name, f.description, f.release_date, f.duration, f.rate, f.mpa " +
                "ORDER BY COUNT(fl.user_id) DESC";

        String sqlQuery = "";
        if (sortBy == null) {
            sqlQuery = sqlQueryNoSort;
        } else if (sortBy.equals("year")) {
            sqlQuery = sqlQuerySortByYear;
        } else if (sortBy.equals("likes")) {
            sqlQuery = sqlQuerySortByLikes;
        }

        List<Film> filmList = jdbcTemplate.query(sqlQuery, (rs, rowNum) -> makeFilm(rs), directorId);
        linkGenresToFilms(filmList);
        linkDirectorsToFilms(filmList);

        return filmList;

    }


    // SEARCH
    @Override
    public List<Film> search(String query, String[] by) {
        List<Film> filmList = new ArrayList<>();

        StringBuilder sqlQueryBuilder = new StringBuilder("SELECT f.id, f.name, f.description, f.release_date, " +
                "f.duration, f.rate, f.mpa");

        if (by.length == 1) {
            if (by[0].equals("title")) {
                sqlQueryBuilder
                        .append(" FROM film f " +
                                "LEFT JOIN filmorate_like fl ON f.id = fl.film_id " +
                                "WHERE LCASE(f.name) LIKE '%")
                        .append(query)
                        .append("%' " +
                                "GROUP BY f.id " +
                                "ORDER BY COUNT(fl.film_id) DESC");
            }
            if (by[0].equals("director")) {
                sqlQueryBuilder
                        .append(", d.name " +
                                "FROM film f " +
                                "LEFT JOIN filmorate_like fl ON f.id = fl.film_id " +
                                "LEFT JOIN film_director fd on f.id = fd.film_id " +
                                "LEFT JOIN director d on fd.director_id = d.id " +
                                "WHERE LCASE(d.name) LIKE '%")
                        .append(query)
                        .append("%' " +
                                "GROUP BY f.id " +
                                "ORDER BY COUNT(fl.film_id) DESC");
            }
            String sqlQuery = sqlQueryBuilder.toString();
            MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource("query", query);
            filmList = namedParameterJdbcTemplate.query(sqlQuery, mapSqlParameterSource,
                    (rs, rowNum) -> makeFilm(rs));

            linkGenresToFilms(filmList);
            linkDirectorsToFilms(filmList);
        }

        if (by.length == 2) {
            sqlQueryBuilder
                    .append(", d.name " +
                            "FROM film f " +
                            "LEFT JOIN filmorate_like fl ON f.id = fl.film_id " +
                            "LEFT JOIN film_director fd on f.id = fd.film_id " +
                            "LEFT JOIN director d on fd.director_id = d.id " +
                            "WHERE LCASE(d.name) LIKE '%")
                    .append(query)
                    .append("%' " +
                            "OR LCASE(f.name) LIKE '%")
                    .append(query)
                    .append("%' " +
                            "GROUP BY f.id " +
                            "ORDER BY COUNT(fl.film_id) DESC");

            String sqlQuery = sqlQueryBuilder.toString();
            MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource("query", query);
            filmList = namedParameterJdbcTemplate.query(sqlQuery, mapSqlParameterSource,
                    (rs, rowNum) -> makeFilm(rs));

            linkGenresToFilms(filmList);
            linkDirectorsToFilms(filmList);
        }

        return filmList;
    }


    // RESET STORAGE
    @Override
    public void deleteAllData() {

        jdbcTemplate.update("DELETE FROM FILMORATE_LIKE");

        jdbcTemplate.update("DELETE FROM FILM_GENRE");

        jdbcTemplate.update("DELETE FROM FILM");
        jdbcTemplate.update("ALTER TABLE FILM ALTER COLUMN ID RESTART WITH 1");

        jdbcTemplate.update("DELETE FROM GENRE WHERE (id > 6)");
        jdbcTemplate.update("ALTER TABLE GENRE ALTER COLUMN ID RESTART WITH 7");

        jdbcTemplate.update("DELETE FROM FILMORATE_USER");
        jdbcTemplate.update("ALTER TABLE FILMORATE_USER ALTER COLUMN ID RESTART WITH 1");

    }

    // PRIVATE

    // RowMappers
    private Film makeFilm(ResultSet resultSet) throws SQLException {

        return Film.builder()
                .id(resultSet.getLong("id"))
                .name(resultSet.getString("name"))
                .description(resultSet.getString("description"))
                .releaseDate(resultSet.getDate("release_date").toLocalDate())
                .duration(resultSet.getInt("duration"))
                .rate(resultSet.getByte("rate"))
                .mpa(Mpa.getMpa(resultSet.getLong("mpa")))
                .build();

    }


    // genres
    private void updateGenres(Film film) {

        // delete
        String sqlQueryDelete = "DELETE FROM film_genre WHERE film_id = ?";
        jdbcTemplate.update(sqlQueryDelete, film.getId());

        // insert
        if (film.getGenres() == null || film.getGenres().size() == 0) {
            return;
        }
        List<Genre> genres = new ArrayList<>(film.getGenres());
        String sqlQueryInsert = "INSERT INTO film_genre(film_id, genre_id) VALUES (?, ?)";

        jdbcTemplate.batchUpdate(sqlQueryInsert, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, film.getId());
                ps.setLong(2, genres.get(i).getId());
            }

            @Override
            public int getBatchSize() {
                return genres.size();
            }

        });

    }

    private void linkGenresToFilms(List<Film> filmList) {

        linkGenresToFilms(filmList, true);

    }

    private void linkGenresToAllFilms(List<Film> filmList) {

        linkGenresToFilms(filmList, false);

    }

    private void linkGenresToFilms(List<Film> filmList, boolean allFilms) {

        // all genres
        String sqlQueryAllGenres = "SELECT id, name FROM genre";
        List<Genre> allGenresList = jdbcTemplate.query(sqlQueryAllGenres, (rs, rowNum) -> makeGenre(rs));
        Map<Long, Genre> allGenresListMap = allGenresList.stream().collect(Collectors.toMap(Genre::getId, Function.identity()));

        // films' genres
        String sqlFilmsGenres;
        SqlRowSet sqlRowSet;

        if (allFilms) {
            String sqlFilmsGenresTemplate = "SELECT film_id, genre_id FROM film_genre WHERE film_id IN (%s)";
            List<Long> filmIds = filmList.stream().map(Film::getId).collect(Collectors.toList());
            String sqlParam = String.join(",", Collections.nCopies(filmIds.size(), "?"));
            sqlFilmsGenres = String.format(sqlFilmsGenresTemplate, sqlParam);
            sqlRowSet = jdbcTemplate.queryForRowSet(sqlFilmsGenres, filmIds.toArray());
        } else {
            sqlFilmsGenres = "SELECT film_id, genre_id FROM film_genre";
            sqlRowSet = jdbcTemplate.queryForRowSet(sqlFilmsGenres);
        }

        Map<Long, List<Genre>> filmsGenres = new HashMap<>();
        while (sqlRowSet.next()) {
            Long filmId = sqlRowSet.getLong("film_id");
            Long genreId = sqlRowSet.getLong("genre_id");
            filmsGenres.computeIfAbsent(filmId, v -> new ArrayList<>()).add(allGenresListMap.get(genreId));
        }

        // set genres to film
        filmList.forEach(film -> {
            List<Genre> genres = filmsGenres.get(film.getId());
            film.setGenres((genres != null ? genres : new ArrayList<>()));
        });

    }

    private Genre makeGenre(ResultSet resultSet) throws SQLException {

        return Genre.builder()
                .id(resultSet.getLong("id"))
                .name(resultSet.getString("name"))
                .build();

    }


    // directors
    private void updateDirectors(Film film) {

        // delete
        String sqlQueryDelete = "DELETE FROM film_director WHERE film_id = ?";
        jdbcTemplate.update(sqlQueryDelete, film.getId());

        // insert
        if (film.getDirectors() == null || film.getDirectors().size() == 0) {
            return;
        }
        List<Director> directors = new ArrayList<>(film.getDirectors());
        String sqlQueryInsert = "INSERT INTO film_director(film_id, director_id) VALUES (?, ?)";

        jdbcTemplate.batchUpdate(sqlQueryInsert, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, film.getId());
                ps.setLong(2, directors.get(i).getId());
            }

            @Override
            public int getBatchSize() {
                return directors.size();
            }

        });

    }

    private void linkDirectorsToFilms(List<Film> filmList) {

        linkDirectorsToFilms(filmList, true);

    }

    private void linkDirectorsToAllFilms(List<Film> filmList) {

        linkDirectorsToFilms(filmList, false);

    }

    private void linkDirectorsToFilms(List<Film> filmList, boolean allFilms) {

        // all directors
        String sqlQueryAllDirectors = "SELECT id, name FROM director";
        List<Director> allDirectorsList = jdbcTemplate.query(sqlQueryAllDirectors, (rs, rowNum) -> makeDirector(rs));
        Map<Long, Director> allDirectorsListMap = allDirectorsList
                .stream()
                .collect(Collectors.toMap(Director::getId, Function.identity()));

        // films' directors
        String sqlFilmsDirectors;
        SqlRowSet sqlRowSet;

        if (allFilms) {
            String sqlFilmsDirectorsTemplate = "SELECT film_id, director_id FROM film_director WHERE film_id IN (%s)";
            List<Long> filmIds = filmList.stream().map(Film::getId).collect(Collectors.toList());
            String sqlParam = String.join(",", Collections.nCopies(filmIds.size(), "?"));
            sqlFilmsDirectors = String.format(sqlFilmsDirectorsTemplate, sqlParam);
            sqlRowSet = jdbcTemplate.queryForRowSet(sqlFilmsDirectors, filmIds.toArray());
        } else {
            sqlFilmsDirectors = "SELECT film_id, director_id FROM film_director";
            sqlRowSet = jdbcTemplate.queryForRowSet(sqlFilmsDirectors);
        }

        Map<Long, List<Director>> filmsDirectors = new HashMap<>();
        while (sqlRowSet.next()) {
            Long filmId = sqlRowSet.getLong("film_id");
            Long directorId = sqlRowSet.getLong("director_id");
            filmsDirectors.computeIfAbsent(filmId, v -> new ArrayList<>()).add(allDirectorsListMap.get(directorId));
        }

        // set genres to film
        filmList.forEach(film -> {
            List<Director> directors = filmsDirectors.get(film.getId());
            film.setDirectors((directors != null ? directors : new ArrayList<>()));
        });

    }

    private Director makeDirector(ResultSet resultSet) throws SQLException {

        return Director.builder()
                .id(resultSet.getLong("id"))
                .name(resultSet.getString("name"))
                .build();

    }

}