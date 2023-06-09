package ru.yandex.practicum.filmorate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.filmorate.model.LikeType;
import ru.yandex.practicum.filmorate.model.Review;
import ru.yandex.practicum.filmorate.service.ReviewService;

import javax.validation.Valid;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;


    // REVIEWS
    @PostMapping
    public Review createReview(@Valid @RequestBody Review review) {
        return reviewService.create(review);
    }

    @PutMapping
    public Review updateReview(@Valid @RequestBody Review review) {
        return reviewService.update(review);
    }

    @DeleteMapping("/{id}")
    public void deleteReview(@PathVariable long id) {
        reviewService.delete(id);
    }

    @GetMapping("/{id}")
    public Review getReviewById(@PathVariable long id) {
        return reviewService.getReviewById(id);
    }

    @GetMapping
    public List<Review> getReviews(@RequestParam(required = false) Long filmId, @RequestParam(defaultValue = "10") @PositiveOrZero int count) {
        return reviewService.getReviews(filmId, count);
    }


    // LIKES
    @PutMapping("/{id}/like/{userId}")
    public void addLike(@PathVariable("id") long reviewId, @PathVariable long userId) {
        reviewService.addLike(reviewId, userId, LikeType.LIKE);
    }

    @PutMapping("/{id}/dislike/{userId}")
    public void addDislike(@PathVariable("id") long reviewId, @PathVariable long userId) {
        reviewService.addLike(reviewId, userId, LikeType.DISLIKE);
    }

    @DeleteMapping("/{id}/like/{userId}")
    public void deleteLike(@PathVariable("id") long reviewId, @PathVariable long userId) {
        reviewService.deleteLike(reviewId, userId, LikeType.LIKE);
    }

    @DeleteMapping("/{id}/dislike/{userId}")
    public void deleteDisLike(@PathVariable("id") long reviewId, @PathVariable long userId) {
        reviewService.deleteLike(reviewId, userId, LikeType.DISLIKE);
    }
}
