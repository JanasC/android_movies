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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.popularmovies.BuildConfig;
import com.example.popularmovies.GetMovieDataService;
import com.example.popularmovies.model.Movie;
import com.example.popularmovies.MovieAdapter;
import com.example.popularmovies.model.MovieList;
import com.example.popularmovies.R;
import com.example.popularmovies.RetrofitInstance;
import com.example.popularmovies.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MovieActivity extends AppCompatActivity implements MovieAdapter.ListItemClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = MovieActivity.class.getSimpleName();
    private static String SAVED_INSTANCE_EXTRA = "Movies";
    private static boolean PREFERENCES_HAVE_BEEN_UPDATED = false;

    @BindView(R.id.error_message_tv)
    public TextView errorMessageTv;
    @BindView(R.id.no_favorites_tv)
    public TextView noFavoritesTv;
    @BindView(R.id.recyclerview_movie)
    public RecyclerView movieRv;
    @BindView(R.id.pb_loading_indicator)
    public ProgressBar progressBar;

    private MovieAdapter movieAdapter;
    private NetworkInfo networkInfo;
    private SharedPreferences sharedPreferences;
    private Call<MovieList> call = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkInfo = connMgr.getActiveNetworkInfo();
        setupRecyclerView();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVED_INSTANCE_EXTRA)) {
                ArrayList<Movie> movies = savedInstanceState.getParcelableArrayList(SAVED_INSTANCE_EXTRA);
                movieAdapter.setMovieData(movies);
            }
        } else {
            loadMovieData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (PREFERENCES_HAVE_BEEN_UPDATED) {
            Log.d(TAG, "onStart: preferences were updated");
            loadMovieData();
            PREFERENCES_HAVE_BEEN_UPDATED = false;
        }
    }

    private void setupRecyclerView() {
        movieAdapter = new MovieAdapter(this, MovieActivity.this);
        movieRv.setAdapter(movieAdapter);
        int portraitNumberOfColumns = 2;
        int landscapeNumberOfColumns = 4;
        LinearLayoutManager layoutManager;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            layoutManager = new GridLayoutManager(this, portraitNumberOfColumns);
        } else {
            layoutManager = new GridLayoutManager(this, landscapeNumberOfColumns);
        }
        movieRv.setLayoutManager(layoutManager);
        movieRv.setHasFixedSize(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        List<Movie> movies = movieAdapter.getMovies();
        if (movies != null && !movies.isEmpty()) {
            outState.putParcelableArrayList(SAVED_INSTANCE_EXTRA, new ArrayList<>(movies));
        }
    }

    private void loadMovieData() {
        GetMovieDataService service = RetrofitInstance.getRetrofitInstance().create(GetMovieDataService.class);
        String preference = sharedPreferences.getString(getString(R.string.pref_filter_key), "");
        if (preference == null || preference.isEmpty()) {
            preference = "popular";
        }

        switch (preference) {
            case "popular":
                if (networkInfo != null && networkInfo.isConnected()) {
                    noFavoritesTv.setVisibility(View.INVISIBLE);
                    errorMessageTv.setVisibility(View.INVISIBLE);
                    movieAdapter.setMovieData(null);
                    call = service.getPopularMovieList(BuildConfig.API_KEY);
                    runService();
                } else {
                    errorMessageTv.setText(getString(R.string.no_internet_connection));
                }
                break;
            case "top_rated":
                if (networkInfo != null && networkInfo.isConnected()) {
                    noFavoritesTv.setVisibility(View.INVISIBLE);
                    errorMessageTv.setVisibility(View.INVISIBLE);
                    movieAdapter.setMovieData(null);
                    call = service.getTopRatedMovieList(BuildConfig.API_KEY);
                    runService();
                } else {
                    errorMessageTv.setText(getString(R.string.no_internet_connection));
                }
                break;
            case "favorites":
                errorMessageTv.setVisibility(View.INVISIBLE);
                setupViewModel();
                break;
        }
    }

    private void runService() {
        progressBar.setVisibility(View.VISIBLE);
        call.enqueue(new Callback<MovieList>() {

            @Override
            public void onResponse(@NonNull Call<MovieList> call, Response<MovieList> response) {
                if (response.body() == null) {
                    progressBar.setVisibility(View.INVISIBLE);
                    errorMessageTv.setVisibility(View.VISIBLE);
                    errorMessageTv.setText(getString(R.string.error_has_occured));
                } else {
                    generateMovieList(response.body().getMovieArrayList());
                }
            }

            @Override
            public void onFailure(Call<MovieList> call, Throwable t) {
                Toast.makeText(MovieActivity.this, getString(R.string.error_has_occured), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateMovieList(List<Movie> movieDataList) {
        progressBar.setVisibility(View.INVISIBLE);
        errorMessageTv.setVisibility(View.INVISIBLE);
        movieAdapter.setMovieData(movieDataList);
    }

    @Override
    public void onListItemClick(Movie item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("Movie", item);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.movie, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
            startActivity(startSettingsActivity);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupViewModel() {
        final MainViewModel viewModel;
        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);
        viewModel.getMovies().observe(this, new Observer<List<Movie>>() {
            @Override
            public void onChanged(@Nullable List<Movie> movies) {
                Log.d(TAG, "Receiving database update from LiveData in ViewModel");
                String preference = sharedPreferences.getString(getString(R.string.pref_filter_key), "");
                if (preference.equals("favorites")) {
                    movieAdapter.setMovieData(null);
                    if (movies.isEmpty()) {
                        noFavoritesTv.setVisibility(View.VISIBLE);
                        noFavoritesTv.setText(getString(R.string.no_favorites));
                    } else {
                        noFavoritesTv.setVisibility(View.INVISIBLE);
                        generateMovieList(movies);
                    }
                }
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        PREFERENCES_HAVE_BEEN_UPDATED = true;
    }
}
