package io.github.ryanhoo.firFlight.account;

import android.content.Context;
import android.text.TextUtils;
import io.github.ryanhoo.firFlight.FlightApplication;
import io.github.ryanhoo.firFlight.RxBus;
import io.github.ryanhoo.firFlight.analytics.FlightAnalytics;
import io.github.ryanhoo.firFlight.data.model.Token;
import io.github.ryanhoo.firFlight.data.model.User;
import io.github.ryanhoo.firFlight.data.source.TokenRepository;
import io.github.ryanhoo.firFlight.data.source.UserRepository;
import io.github.ryanhoo.firFlight.event.SignOutEvent;
import io.github.ryanhoo.firFlight.event.UserUpdatedEvent;
import io.github.ryanhoo.firFlight.util.DbUtils;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Created with Android Studio.
 * User: ryan.hoo.j@gmail.com
 * Date: 3/15/16
 * Time: 11:22 PM
 * Desc: UserSession
 */
public class UserSession {

    private static UserSession sInstance;

    private static Context sContext;

    private Token mToken;
    private User mUser;

    private TokenRepository mTokenRepository;
    private UserRepository mUserRepository;

    private UserSession() {
        sContext = FlightApplication.getInstance();
        mTokenRepository = TokenRepository.getInstance();
        mUserRepository = UserRepository.getInstance();

        restoreSession();
    }

    public static UserSession getInstance() {
        if (sInstance == null) {
            synchronized (UserSession.class) {
                if (sInstance == null)
                    sInstance = new UserSession();
            }
        }
        return sInstance;
    }

    public boolean isSignedIn() {
        return mToken != null && mToken.getAccessToken() != null;
    }

    /**
     * 1. Request access token by email and password
     * 2. Request api token by access token
     * 3. If api token is null, force to refresh api token
     * 4. Request user info
     */
    public Observable<User> signIn(final String email, final String password) {
        return mTokenRepository.accessToken(email, password)
                .flatMap(new Func1<Token, Observable<Token>>() {
                    @Override
                    public Observable<Token> call(Token token) {
                        // As access token is ready, time to request api token
                        mToken = token;
                        return mTokenRepository.apiToken();
                    }
                })
                .doOnNext(new Action1<Token>() {
                    @Override
                    public void call(Token token) {
                        if (TextUtils.isEmpty(token.getApiToken())) {
                            // It's possible to get an empty api token, so you need to refresh api token
                            throw new ApiTokenInvalidException();
                        }
                    }
                })
                .retryWhen(new Func1<Observable<? extends Throwable>, Observable<Token>>() {
                    @Override
                    public Observable<Token> call(Observable<? extends Throwable> observable) {
                        return observable.flatMap(new Func1<Throwable, Observable<Token>>() {
                            @Override
                            public Observable<Token> call(Throwable throwable) {
                                if (throwable instanceof ApiTokenInvalidException) {
                                    return mTokenRepository.refreshApiToken();
                                }
                                return Observable.error(throwable);
                            }
                        });
                    }
                })
                .doOnNext(new Action1<Token>() {
                    @Override
                    public void call(Token token) {
                        mToken.setApiToken(token.getApiToken());
                    }
                })
                .flatMap(new Func1<Token, Observable<User>>() {
                    @Override
                    public Observable<User> call(Token token) {
                        return mUserRepository.user(true);
                    }
                }).doOnNext(new Action1<User>() {
                    @Override
                    public void call(User user) {
                        mUser = user;

                        storeSession();
                    }
                });
    }

    public void signOut() {
        // Broadcast sign out event
        RxBus.getInstance().post(new SignOutEvent());
        // Clear Database
        DbUtils.clearDataBase(sContext);

        mToken = null;
        mUser = null;
    }

    // Analytics Configs

    private void configAnalytics() {
        FlightAnalytics.configUserSession(this);
    }

    // Requests

    public Observable<User> user(boolean forceUpdate) {
        return mUserRepository.user(forceUpdate).doOnNext(new Action1<User>() {
            @Override
            public void call(User user) {
                mUser = user;
                storeSession();

                RxBus.getInstance().post(new UserUpdatedEvent(mUser));
            }
        });
    }

    // Session Store & Restore

    /* package */ void storeSession() {
        mTokenRepository.storeToken(mToken);
        // mUserRepository.storeUser(); // Already handled by UserRepository

        configAnalytics();
    }

    /* package */ void restoreSession() {
        mToken = mTokenRepository.restoreToken();
        if (isSignedIn()) {
            mUser = mUserRepository.restoreUser();
        }

        configAnalytics();
    }

    // Getters & Setters

    public Token getToken() {
        return mToken;
    }

    public void setToken(Token token) {
        this.mToken = token;
    }

    public void setAccessToken(String accessToken) {
        if (mToken == null) {
            mToken = new Token();
        }
        mToken.setAccessToken(accessToken);
    }

    public void setApiToken(String apiToken) {
        if (mToken == null) {
            mToken = new Token();
        }
        mToken.setApiToken(apiToken);
    }

    public User getUser() {
        return mUser;
    }

    public void setUser(User user) {
        this.mUser = user;
    }

    /**
     * The api token might be null even the request is successful, when this happens we need
     * to force refreshing api token.
     */
    private static class ApiTokenInvalidException extends RuntimeException {
        // Empty
    }
}
