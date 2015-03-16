/*
 * Copyright 2015 Lukas "dotwee" Wolfsteiner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dotwee.openkwsolver.Fragments;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.dotwee.openkwsolver.MainActivity;
import de.dotwee.openkwsolver.R;
import de.dotwee.openkwsolver.Tools.DownloadImageTask;

public class ConfirmFragment extends Fragment {
    public static final String URL_9WK = "http://www.9kw.eu:80/index.cgi";
    public static final String URL_PARAMETER_CAPTCHA_SHOW = "?action=usercaptchashow";
    public static final String URL_PARAMETER_SOURCE = "&source=androidopenkws";
    private static final String LOG_TAG = "ConfirmFragment";

    Thread BalanceUpdate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	    Log.i(LOG_TAG, "onCreate");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    Log.i(LOG_TAG, "onCreateView");
	    return inflater.inflate(R.layout.fragment_confirm, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
	    Log.i(LOG_TAG, "onViewCreated");

        // init prefs
	    final SharedPreferences prefs = PreferenceManager
			    .getDefaultSharedPreferences(getActivity());

        // declare main widgets
        final Button buttonOK = (Button) view.findViewById(R.id.buttonOK);
        final Button buttonNOTOK = (Button) view.findViewById(R.id.buttonNOTOK);
        final Button buttonSkip = (Button) view.findViewById(R.id.buttonSkip);

        final ImageView imageViewCaptcha = (ImageView) view.findViewById(R.id.imageViewCaptcha);
        imageViewCaptcha.getLayoutParams().height = Integer.parseInt(prefs.getString("pref_layout_size", "200"));

        final ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        final EditText editTextAnswer = (EditText) view.findViewById(R.id.editTextAnswer);

        // fix edittext width
        editTextAnswer.setMaxWidth(editTextAnswer.getWidth());

        // setup start text
        buttonOK.setText("Start");

        // start showing balance if network and apikey is available
	    if (MainActivity.isNetworkAvailable(getActivity())) {
		    if (MainActivity.getApiKey(getActivity()) != null) {
			    balanceThread();
		    }
        }

        buttonOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(LOG_TAG, "Click on " + buttonOK);

	            if (MainActivity.isNetworkAvailable(getActivity())) {
		            final String tempCaptchaID = MainActivity.requestCaptchaID(getActivity(), MainActivity.isLoopEnabled(getActivity()), 0);
		            Boolean onCurrentCaptcha = false;

		            onCurrentCaptcha = pullCaptchaPicture(tempCaptchaID);
		            buttonNOTOK.setText("NOT OK");
                    buttonOK.setText("OK");

		            Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
		            if (MainActivity.isVibrateEnabled(getActivity())) {
		                if (onCurrentCaptcha) {
			                vibrator.vibrate(500);
		                }
	                }

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

                    buttonOK.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
	                        MainActivity.sendCaptchaByID(getActivity(), tempCaptchaID, "&answer=yes", true);

                            progressBar.setProgress(0);
                            CountDownTimer.cancel();

                            imageViewCaptcha.setImageDrawable(null);
                            editTextAnswer.setText(null);

	                        if (MainActivity.isLoopEnabled(getActivity())) {
		                        buttonOK.performClick();
	                        } else buttonOK.setEnabled(true);
                        }
                    });

                    buttonNOTOK.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
	                        MainActivity.sendCaptchaByID(getActivity(), tempCaptchaID, "&answer=no", true);

                            progressBar.setProgress(0);
                            CountDownTimer.cancel();

                            imageViewCaptcha.setImageDrawable(null);
                            editTextAnswer.setText(null);

	                        if (MainActivity.isLoopEnabled(getActivity())) {
		                        buttonOK.performClick();
	                        } else buttonOK.setEnabled(true);
                        }
                    });

                    buttonSkip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
	                        MainActivity.skipCaptchaByID(getActivity(), tempCaptchaID);
	                        progressBar.setProgress(0);
                            CountDownTimer.cancel();

                            imageViewCaptcha.setImageDrawable(null);
                            editTextAnswer.setText(null);

	                        if (MainActivity.isLoopEnabled(getActivity())) {
		                        buttonOK.performClick();
	                        } else buttonOK.setEnabled(true);
                        }
                    });
                }
            }
        });
    }

    // BalanceThread: Update the balance every 5 seconds
    public void balanceThread() {
        BalanceUpdate = new Thread() {

            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {
                        Thread.sleep(5000); // 5000ms = 5s
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (getView() != null) {
                                TextView textViewBalance = (TextView) getView()
		                                .findViewById(R.id.textViewBA);
	                            textViewBalance.setText(MainActivity.getBalance(getActivity()));
                            }
                        }
                    });
                }

            }
        };

        // check if thread isn't already running.
        if (BalanceUpdate.isAlive())
            BalanceUpdate.interrupt();

            // if not, start it
        else BalanceUpdate.start();
    }

    // Pull Captcha picture and display it
    public boolean pullCaptchaPicture(String CaptchaID) {
        String CaptchaPictureURL = (URL_9WK + URL_PARAMETER_CAPTCHA_SHOW +
                URL_PARAMETER_SOURCE + MainActivity.getExternalParameter(getActivity(), 0) + "&id=" + CaptchaID + MainActivity.getApiKey(getActivity()));

        Log.i("pullCaptchaPicture", "URL: " + CaptchaPictureURL);
        if (getView() != null) {
            ImageView ImageV = (ImageView) getView().findViewById(R.id.imageViewCaptcha);
            try {
	            Bitmap returnBit = new DownloadImageTask(ImageV).execute(CaptchaPictureURL).get(3000, TimeUnit.MILLISECONDS);
	            if (returnBit != null) return true; // true = new image
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
            }
        }

        return false;
    }
}
