package de.dotwee.openkwsolver.Fragments;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutionException;

import de.dotwee.openkwsolver.MainActivity;
import de.dotwee.openkwsolver.R;
import de.dotwee.openkwsolver.Tools.DownloadContentTask;
import de.dotwee.openkwsolver.Tools.DownloadImageTask;

/**
 * Created by Lukas on 09.03.2015.
 */
public class ConfirmFragment extends Fragment {
    public static final String URL_9WK = "http://www.9kw.eu:80/index.cgi";
    public static final String URL_PARAMETER_CAPTCHA_SHOW = "?action=usercaptchashow";
    public static final String URL_PARAMETER_CAPTCHA_ANSWER = "?action=usercaptchacorrect";
    public static final String URL_PARAMETER_SOURCE = "&source=androidopenkws";
    public static final String URL_PARAMETER_SERVER_BALANCE = "?action=usercaptchaguthaben";
    private static final String LOG_TAG = "ConfirmFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_confirm, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // declare main widgets
        final Button buttonOK = (Button) view.findViewById(R.id.buttonOK);
        final Button buttonNOTOK = (Button) view.findViewById(R.id.buttonNOTOK);
        final Button buttonSkip = (Button) view.findViewById(R.id.buttonSkip);

        final ImageView imageViewCaptcha = (ImageView) view.findViewById(R.id.imageViewCaptcha);
        final ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        final EditText editTextAnswer = (EditText) view.findViewById(R.id.editTextAnswer);

        // fix edittext width
        editTextAnswer.setMaxWidth(editTextAnswer.getWidth());

        // setup start text
        buttonOK.setText("Start");

        // init prefs
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        final Boolean prefLoop = prefs.getBoolean("pref_automation_loop", false);
        final Boolean prefVibrate = prefs.getBoolean("pref_notification_vibrate", false);

        // start showing balance if network and apikey is available
        if (MainActivity.networkAvailable(getActivity())) {
            if (!MainActivity.getApiKey(getActivity())
                    .equals("")) balanceThread();
        }

        buttonOK.setOnClickListener(v -> {
            Log.i(LOG_TAG, "Click on " + buttonOK);

            if (MainActivity.networkAvailable(getActivity())) {
                String tempCaptchaID = MainActivity.requestCaptchaID(getActivity(), prefLoop, 0);
                Boolean onCurrentCaptcha = false;

                onCurrentCaptcha = pullCaptchaPicture(tempCaptchaID);
                buttonNOTOK.setText("NOT OK");
                buttonOK.setText("OK");

                Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (prefVibrate) if (onCurrentCaptcha) vibrator.vibrate(500);

                final int[] i = {0};
                final CountDownTimer CountDownTimer;
                CountDownTimer = new CountDownTimer(26000, 1000) {

                    @Override
                    public void onTick(long millisUntilFinished) {
                        i[0]++;
                        progressBar.setProgress(i[0]);
                    }

                    @Override
                    public void onFinish() {
                    }
                };

                CountDownTimer.start();

                buttonOK.setOnClickListener(v1 -> {
                    sendCaptchaAnswer(true, tempCaptchaID);
                    progressBar.setProgress(0);
                    CountDownTimer.cancel();

                    imageViewCaptcha.setImageDrawable(null);
                    editTextAnswer.setText(null);

                    if (prefLoop) buttonOK.performClick();
                    else buttonOK.setEnabled(true);
                });

                buttonNOTOK.setOnClickListener(v1 -> {
                    sendCaptchaAnswer(false, tempCaptchaID);
                    progressBar.setProgress(0);
                    CountDownTimer.cancel();

                    imageViewCaptcha.setImageDrawable(null);
                    editTextAnswer.setText(null);

                    if (prefLoop) buttonOK.performClick();
                    else buttonOK.setEnabled(true);
                });

                buttonSkip.setOnClickListener(v1 -> {
                    MainActivity.skipCaptchaByID(getActivity(), tempCaptchaID);
                    progressBar.setProgress(0);
                    CountDownTimer.cancel();

                    imageViewCaptcha.setImageDrawable(null);
                    editTextAnswer.setText(null);

                    if (prefLoop) buttonOK.performClick();
                    else buttonOK.setEnabled(true);
                });
            }
        });
    }

    // BalanceThread: Update the balance every 5 seconds
    public void balanceThread() {
        Thread BalanceUpdate = new Thread() {

            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {
                        Thread.sleep(5000); // 5000ms = 5s
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    getActivity().runOnUiThread(() -> {
                        TextView textViewBalance;

                        String tBalance = null;
                        String BalanceURL = (URL_9WK + URL_PARAMETER_SERVER_BALANCE +
                                URL_PARAMETER_SOURCE + MainActivity.getApiKey(getActivity()));
                        Log.i("balanceThread", "BalanceURL: " + BalanceURL);

                        try {
                            tBalance = new DownloadContentTask().execute(BalanceURL, "").get();
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }

                        if (getView() != null) {
                            textViewBalance = (TextView) getView()
                                    .findViewById(R.id.textViewBalance);

                            Log.i("balanceThread", "Balance: " + tBalance);
                            textViewBalance.setText(tBalance);
                        }
                    });
                }

            }
        };

        // check if thread isn't already running.
        if (BalanceUpdate.isAlive()) {
            BalanceUpdate.interrupt();
            Log.i("balanceThread", "stopped");
        }

        // if not, start it
        else {
            BalanceUpdate.start();
            Log.i("balanceThread", "started");
        }
    }

    // Pull Captcha picture and display it
    public boolean pullCaptchaPicture(String CaptchaID) {
        String CaptchaPictureURL = (URL_9WK + URL_PARAMETER_CAPTCHA_SHOW +
                URL_PARAMETER_SOURCE + MainActivity.getExternalParameter(getActivity(), 0) + "&id=" + CaptchaID + MainActivity.getApiKey(getActivity()));

        Log.i("pullCaptchaPicture", "URL: " + CaptchaPictureURL);
        if (getView() != null) {
            ImageView ImageV = (ImageView) getView().findViewById(R.id.imageViewCaptcha);
            try {
                Bitmap returnBit = new DownloadImageTask(ImageV).execute(CaptchaPictureURL).get();
                if (returnBit != null) return true; // true = new image
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    public void sendCaptchaAnswer(Boolean state, String CaptchaID) {
        Log.i(LOG_TAG, "Answer: " + state);
        String answer;
        if (state) answer = "&captcha=yes";
        else answer = "&captcha=no";

        String CaptchaURL = (URL_9WK + URL_PARAMETER_CAPTCHA_ANSWER +
                URL_PARAMETER_SOURCE + MainActivity.getExternalParameter(getActivity(), 0) + answer +
                "&id=" + CaptchaID + MainActivity.getApiKey(getActivity()));

        // remove Spaces from URL
        CaptchaURL = CaptchaURL.replaceAll(" ", "%20");
        Log.i("sendCaptchaAnswer", "Answer-URL: " + CaptchaURL);

        try {
            String ret = new DownloadContentTask().execute(CaptchaURL, "").get();
            Log.i(LOG_TAG, "Return: " + ret);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}