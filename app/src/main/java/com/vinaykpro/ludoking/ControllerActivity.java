package com.vinaykpro.ludoking;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

public class ControllerActivity extends Activity {

    private static final String SERVICE_TYPE = "_ludoremote._tcp.";
    private static final int PORT = 8765;

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private WifiManager.MulticastLock multicastLock;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private String hostAddress;
    private boolean discovering;
    private boolean resolving;
    private boolean destroyed;

    private Runnable retryRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        WifiManager wifiManager =
                (WifiManager) getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);

        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("LudoControllerLock");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }

        createScreen();
        startDiscovery();
    }

    private void createScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        root.setBackgroundColor(0xFF101827);

        TextView title = new TextView(this);
        title.setText("🎮 LUDO CONTROLLER");
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 15, 0, 20);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setText("গেমিং ডিভাইস খোঁজা হচ্ছে...");
        statusText.setTextSize(16);
        statusText.setTextColor(0xFFFFD54F);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 10, 0, 20);
        root.addView(statusText);

        Button searchButton = new Button(this);
        searchButton.setText("🔄 SEARCH AGAIN");
        searchButton.setOnClickListener(v -> startDiscovery());
        root.addView(searchButton);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout playersLayout = new LinearLayout(this);
        playersLayout.setOrientation(LinearLayout.VERTICAL);
        playersLayout.setPadding(0, 20, 0, 0);

        String[] playerNames = {
                "PLAYER 1", "PLAYER 2", "PLAYER 3", "PLAYER 4"
        };

        int[] playerColors = {
                0xFFE53935, 0xFF43A047, 0xFFFFCA28, 0xFF1E88E5
        };

        for (int player = 0; player < 4; player++) {
            final int selectedPlayer = player;

            TextView playerTitle = new TextView(this);
            playerTitle.setText(playerNames[player]);
            playerTitle.setTextSize(18);
            playerTitle.setTextColor(playerColors[player]);
            playerTitle.setGravity(Gravity.CENTER);
            playerTitle.setPadding(0, 18, 0, 8);
            playersLayout.addView(playerTitle);

            LinearLayout buttons = new LinearLayout(this);
            buttons.setGravity(Gravity.CENTER);
            buttons.setOrientation(LinearLayout.HORIZONTAL);

            for (int dice = 1; dice <= 6; dice++) {
                final int selectedDice = dice;

                Button button = new Button(this);
                button.setText(String.valueOf(dice));
                button.setTextSize(18);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1
                        );

                params.setMargins(3, 3, 3, 3);
                buttons.addView(button, params);

                button.setOnClickListener(v ->
                        sendDiceCommand(selectedPlayer, selectedDice)
                );
            }

            playersLayout.addView(buttons);
        }

        scrollView.addView(playersLayout);

        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
    }

    private void startDiscovery() {
        if (destroyed || nsdManager == null) return;

        hostAddress = null;
        resolving = false;

        statusText.setText("গেমিং ডিভাইস স্ক্যানিং...");

        stopDiscovery();

        try {
            discoveryListener = new NsdManager.DiscoveryListener() {

                @Override
                public void onDiscoveryStarted(String serviceType) {
                    discovering = true;
                }

                @Override
                public void onServiceFound(NsdServiceInfo serviceInfo) {
                    if (destroyed || resolving) return;

                    if (SERVICE_TYPE.equals(serviceInfo.getServiceType())) {
                        resolving = true;
                        resolveService(serviceInfo);
                    }
                }

                @Override
                public void onServiceLost(NsdServiceInfo serviceInfo) {
                    hostAddress = null;
                    scheduleRetry();
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                    discovering = false;
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    discovering = false;
                    scheduleRetry();
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                    discovering = false;
                    scheduleRetry();
                }
            };

            nsdManager.discoverServices(
                    SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener
            );

            handler.postDelayed(() -> {
                if (!destroyed && hostAddress == null) {
                    resolving = false;
                    stopDiscovery();
                    scheduleRetry();
                }
            }, 5000);

        } catch (Exception e) {
            scheduleRetry();
        }
    }

    private void resolveService(NsdServiceInfo serviceInfo) {
        try {
            nsdManager.resolveService(
                    serviceInfo,
                    new NsdManager.ResolveListener() {

                        @Override
                        public void onResolveFailed(
                                NsdServiceInfo info, int errorCode) {
                            resolving = false;
                            scheduleRetry();
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo info) {
                            if (destroyed) return;

                            if (info.getHost() != null) {
                                hostAddress = info.getHost().getHostAddress();

                                if (info.getPort() > 0) {
                                    statusText.setText(
                                            "🟢 গেম ফোন কানেক্টেড\n" + hostAddress
                                    );
                                    cancelRetry();
                                } else {
                                    hostAddress = null;
                                    scheduleRetry();
                                }
                            } else {
                                hostAddress = null;
                                scheduleRetry();
                            }

                            resolving = false;
                        }
                    }
            );
        } catch (Exception e) {
            resolving = false;
            scheduleRetry();
        }
    }

    private void sendDiceCommand(int player, int dice) {
        if (hostAddress == null) {
            statusText.setText("গেম ফোন এখনো পাওয়া যায়নি। খোঁজা হচ্ছে...");
            startDiscovery();
            return;
        }

        final String targetHost = hostAddress;

        statusText.setText(
                "Player " + (player + 1) + " এর জন্য " + dice + " পাঠানো হচ্ছে..."
        );

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String urlText = String.format(
                        Locale.US,
                        "http://%s:%d/cmd?cmd=setdice&player=%d&value=%d",
                        targetHost,
                        PORT,
                        player,
                        dice
                );

                URL url = new URL(urlText);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);

                int responseCode = connection.getResponseCode();

                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        statusText.setText(
                                "🟢 Player " + (player + 1)
                                        + " এর পরের চালের জন্য " + dice
                                        + " সেট হয়েছে"
                        );
                    } else {
                        statusText.setText("কমান্ড পাঠানো যায়নি। আবার চেষ্টা করো।");
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    hostAddress = null;
                    statusText.setText("কানেকশন হারিয়েছে। আবার খোঁজা হচ্ছে...");
                    startDiscovery();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void scheduleRetry() {
        if (destroyed) return;

        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
        }

        retryRunnable = () -> {
            if (!destroyed && hostAddress == null) {
                startDiscovery();
            }
        };

        handler.postDelayed(retryRunnable, 4000);
    }

    private void cancelRetry() {
        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }
    }

    private void stopDiscovery() {
        if (nsdManager != null && discoveryListener != null && discovering) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception ignored) {
            }
        }

        discovering = false;
        discoveryListener = null;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        cancelRetry();
        handler.removeCallbacksAndMessages(null);
        stopDiscovery();

        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }

        super.onDestroy();
    }
}
