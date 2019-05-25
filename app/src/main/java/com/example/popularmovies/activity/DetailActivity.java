/*
 * PROJECT LICENSE
 *
 * This project was submitted by Janas Chatkevicius as part of the Nanodegree At Udacity.
 *
 * As part of Udacity Honor code, your submissions must be your own work, hence
 * submitting this project as yours will cause you to break the Udacity Honor Code
 * and the suspension of your account.
 *
 * Me, the author of the project, allow you to check the code as a reference, but if
 * you submit it, it's your own responsibility if you get expelled.
 *
 * Copyright (c) 2019 Janas Chatkevicius
 *
 * Besides the above notice, the following license applies and this license notice
 * must be included in all works derived from this project.
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.example.popularmovies.activity;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.popularmovies.BuildConfig;
import com.example.popularmovies.GetMovieDataService;
import com.example.popularmovies.GlideApp;
import com.example.popularmovies.RetrofitInstance;
import com.example.popularmovies.ReviewAdapter;
import com.example.popularmovies.VideoAdapter;
import com.example.popularmovies.database.AppDatabase;
import com.example.popularmovies.database.AppExecutors;
import com.example.popularmovies.model.Movie;
import com.example.popularmovies.R;
import com.example.popularmovies.model.Review;
import com.example.popularmovies.model.ReviewList;
import com.example.popularmovies.model.Video;
import com.example.popularmovies.model.VideoList;
import com.example.popularmovies.viewmodel.DetailsMovieViewModel;
import com.example.popularmovies.viewmodel.DetailsMovieViewModelFactory;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DetailActivity extends AppCompatActivity implements VideoAdapter.ListItemClickListener, View.OnClickListener {

    private static final String TAG = DetailActivity.class.getSimpleName();
    @BindView(R.id.title_tv)
    protected TextView movieTitleTv;
    @BindView(R.id.image_iv)
    protected ImageView posterIv;
    @BindView(R.id.plot_tv)
    protected TextView plotTv;
    @BindView(R.id.release_date_iv)
    protected TextView releaseTv;
    @BindView(R.id.rating_tv)
    protected TextView ratingTv;
    @BindView(R.id.recyclerview_video)
    public RecyclerView videoRv;
    @BindView(R.id.trailer_tv)
    public TextView trailerTv;
    @BindView(R.id.line_tv)
    public View lineTv;
    @BindView(R.id.recyclerview_review)
    public RecyclerView reviewRv;
    @BindView(R.id.review_tv)
    public TextView reviewTv;
    @BindView(R.id.favorite_btn)
    public Button favoriteTv;

    private AppDatabase mDb;
    private Movie movie;
    private Boolean isAlreadyFavourite;
    private GetMovieDataService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        ButterKnife.bind(this);
        favoriteTv.setOnClickListener(this);
        mDb = AppDatabase.getInstance(getApplicationContext());
        service = RetrofitInstance.getRetrofitInstance().create(GetMovieDataService.class);
        Intent intentThatStartedThisActivity = getIntent();

        String INTENT_EXTRA = "Movie";
        if (intentThatStartedThisActivity.hasExtra(INTENT_EXTRA)) {
            movie = intentThatStartedThisActivity.getParcelableExtra(INTENT_EXTRA);
            movieTitleTv.setText(movie.getTitle());

            String baseMoviePosterPath = "http://image.tmdb.org/t/p/w185";
            GlideApp
                    .with(this)
                    .load(baseMoviePosterPath + movie.getPoster())
                    .placeholder(new ColorDrawable(Color.BLACK))
                    .error(R.mipmap.ic_image)
                    .into(posterIv);
            plotTv.setText(movie.getPlotSynopsis());
            long movieId = movie.getId();
            DetailsMovieViewModelFactory factory = new DetailsMovieViewModelFactory(mDb, movieId);
            final DetailsMovieViewModel viewModel = ViewModelProviders.of(this, factory).get(DetailsMovieViewModel.class);
            viewModel.getMovie().observe(this, new Observer<Movie>() {
                @Override
                public void onChanged(@Nullable Movie movie) {
                    Log.d(TAG, "Receiving database update from LiveData");
                    if (movie == null) {
                        isAlreadyFavourite = false;
                        favoriteTv.setText(getString(R.string.mark_as_favorite));
                    } else {
                        isAlreadyFavourite = true;
                        favoriteTv.setText(getString(R.string.unmark_as_favorite));
                    }
                }
            });

            setupTrailersList();
            setupReviewsList();

            if (!movie.getReleaseDate().isEmpty()) {
                releaseTv.setText(movie.getReleaseDate());
            } else {
                releaseTv.setText(getString(R.string.no_release_data));
            }

            String movieRating = String.valueOf(movie.getUserRating());
            if (!movieRating.equals("0.0")) {
                String formattedMovieRating = getString(R.string.movie_rating, movieRating);
                ratingTv.setText(formattedMovieRating);
            } else {
                ratingTv.setText(getString(R.string.no_rating_data));
            }
        }
    }

    private void setupReviewsList() {
        long movieId = movie.getId();
        Call<ReviewList> reviewCall = service.getMovieReviewsList(movieId, BuildConfig.API_KEY);
        reviewCall.enqueue(new Callback<ReviewList>() {
            @Override
            public void onResponse(@NonNull Call<ReviewList> call, Response<ReviewList> response) {
                assert response.body() != null;
                if ((response.body().getReviewArrayList().isEmpty())) {
                    reviewTv.setVisibility(View.GONE);
                    return;
                }
                generateReviews(response);
            }

            @Override
            public void onFailure(Call<ReviewList> call, Throwable t) {
                Toast.makeText(DetailActivity.this, "Something went wrong...Cannot load reviews!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupTrailersList() {
        long movieId = movie.getId();
        Call<VideoList> videoCall = service.getMovieVideosList(movieId, BuildConfig.API_KEY);
        videoCall.enqueue(new Callback<VideoList>() {
            @Override
            public void onResponse(@NonNull Call<VideoList> call, Response<VideoList> response) {
                assert response.body() != null;
                if ((response.body().getVideoArrayList().isEmpty())) {
                    trailerTv.setVisibility(View.GONE);
                    lineTv.setVisibility(View.GONE);
                    return;
                }
                generateVideosList((response.body().getVideoArrayList()));
            }

            @Override
            public void onFailure(Call<VideoList> call, Throwable t) {
                Toast.makeText(DetailActivity.this, "Something went wrong...Cannot load trailers!", Toast.LENGTH_SHORT).show();

            }
        });
    }

    private void generateReviews(Response<ReviewList> response) {
        List<Review> list = response.body().getReviewArrayList();
        ReviewAdapter reviewAdapter = new ReviewAdapter();
        reviewRv.setAdapter(reviewAdapter);
        reviewAdapter.setVideoData(list);
        reviewRv.setNestedScrollingEnabled(false);
        LinearLayoutManager layoutManager = new LinearLayoutManager(DetailActivity.this);
        reviewRv.setLayoutManager(layoutManager);
        reviewRv.setHasFixedSize(true);
    }

    private void generateVideosList(ArrayList<Video> videoArrayList) {
        VideoAdapter videoAdapter = new VideoAdapter(this);
        videoRv.setAdapter(videoAdapter);
        videoAdapter.setVideoData(videoArrayList);
        videoRv.setNestedScrollingEnabled(false);
        LinearLayoutManager layoutManager = new LinearLayoutManager(DetailActivity.this);
        videoRv.setLayoutManager(layoutManager);
        videoRv.setHasFixedSize(true);
    }

    @Override
    public void onListItemClick(Video item) {
        Uri webPage = Uri.parse("http://www.youtube.com/watch?v=" + item.getKey());
        Intent webIntent = new Intent(Intent.ACTION_VIEW, webPage);
        if (webIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(webIntent);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.favorite_btn) {
            if (isAlreadyFavourite) {
                AppExecutors.getInstance().getDiskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        isAlreadyFavourite = false;
                        mDb.movieDao().deleteMovie(movie);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                favoriteTv.setText(getString(R.string.mark_as_favorite));
                            }
                        });
                    }
                });

            } else {
                AppExecutors.getInstance().getDiskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        isAlreadyFavourite = true;
                        mDb.movieDao().insertMovie(movie);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                favoriteTv.setText(getString(R.string.unmark_as_favorite));
                            }
                        });
                    }
                });
            }
        }
    }
}
