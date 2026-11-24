package com.vinaykpro.ludoking;

public class ControllerActivity extends android.app.Activity {
    private android.net.nsd.NsdManager nsd;
    private android.net.nsd.NsdManager.DiscoveryListener discovery;
    private android.net.wifi.WifiManager.MulticastLock multicastLock;
    private String host = null;
    private int port = 8765;
    private boolean resolving = false;
    private boolean discovering = false;
    private android.widget.TextView status;

    @Override
    protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(14, 18, 14, 18);
        root.setBackgroundColor(android.graphics.Color.rgb(23, 37, 84));

        status = new android.widget.TextView(this);
        status.setTextColor(-1);
        status.setTextSize(17);
        status.setGravity(17);
        status.setText("Searching for Ludo game...");
        root.addView(status, new android.widget.LinearLayout.LayoutParams(-1, 65));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout rows = new android.widget.LinearLayout(this);
        rows.setOrientation(1);

        for (int player = 0; player < 4; player++) {
            android.widget.TextView title = new android.widget.TextView(this);
            title.setText("PLAYER " + (player + 1));
            title.setTextColor(-1);
            title.setTextSize(19);
            title.setGravity(17);
            title.setPadding(0, 16, 0, 5);
            rows.addView(title);

            android.widget.LinearLayout numbers = new android.widget.LinearLayout(this);
            numbers.setOrientation(0);

            for (int number = 1; number <= 6; number++) {
                final int p = player;
                final int n = number;
                android.widget.Button b = new android.widget.Button(this);
                b.setText(String.valueOf(number));
                b.setTextSize(17);
                android.widget.LinearLayout.LayoutParams lp =
                        new android.widget.LinearLayout.LayoutParams(0, 58, 1);
                lp.setMargins(2, 2, 2, 2);
                numbers.addView(b, lp);
                b.setOnClickListener(v ->
                        sendCommand("setdice&player=" + p + "&value=" + n,
                                "Player " + (p + 1) + " next dice = " + n));
            }
            rows.addView(numbers);
        }

        scroll.addView(rows);
        root.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, 0, 1));

        android.widget.Button retry = new android.widget.Button(this);
        retry.setText("SEARCH AGAIN");
        retry.setOnClickListener(v -> {
            host = null;
            resolving = false;
            startDiscovery();
        });
        root.addView(retry);

        setContentView(root);

        android.net.wifi.WifiManager wifi =
                (android.net.wifi.WifiManager) getApplicationContext()
                        .getSystemService(WIFI_SERVICE);
        multicastLock = wifi.createMulticastLock("LudoRemoteDiscovery");
        multicastLock.setReferenceCounted(false);
        multicastLock.acquire();

        nsd = (android.net.nsd.NsdManager) getSystemService(NSD_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.NEARBY_WIFI_DEVICES")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{"android.permission.NEARBY_WIFI_DEVICES"}, 2001);
        } else {
            startDiscovery();
        }
    }

    private void startDiscovery() {
        if (discovering) {
            try { nsd.stopServiceDiscovery(discovery); }
            catch (Exception ignored) {}
            discovering = false;
        }
        host = null;
        resolving = false;
        status.setText("Searching for game phone...");

        discovery = new android.net.nsd.NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) { discovering = true; }
            public void onDiscoveryStopped(String type) { discovering = false; }
            public void onStartDiscoveryFailed(String type, int error) {
                discovering = false;
                status.setText("Search failed. Tap SEARCH AGAIN.");
            }
            public void onStopDiscoveryFailed(String type, int error) {
                discovering = false;
            }
            public void onServiceLost(android.net.nsd.NsdServiceInfo service) {}
            public void onServiceFound(android.net.nsd.NsdServiceInfo service) {
                if (!resolving && service.getServiceName().startsWith("LudoRemote-")) {
                    resolving = true;
                    nsd.resolveService(service,
                        new android.net.nsd.NsdManager.ResolveListener() {
                            public void onResolveFailed(
                                    android.net.nsd.NsdServiceInfo info, int error) {
                                resolving = false;
                                status.setText("Could not connect. Searching again...");
                            }
                            public void onServiceResolved(
                                    android.net.nsd.NsdServiceInfo info) {
                                host = info.getHost().getHostAddress();
                                port = info.getPort();
                                status.setText("CONNECTED • Select a number");
                                try { nsd.stopServiceDiscovery(discovery); }
                                catch (Exception ignored) {}
                                discovering = false;
                            }
                        });
                }
            }
        };

        try {
            nsd.discoverServices("_ludoremote._tcp.",
                    android.net.nsd.NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Exception e) {
            status.setText("Discovery unavailable. Tap SEARCH AGAIN.");
        }
    }

    private void sendCommand(String command, String successText) {
        final String address = host;
        if (address == null) {
            status.setText("Game phone not found. Keep both phones on Wi-Fi.");
            return;
        }
        status.setText("Sending...");
        new Thread(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URL url = new java.net.URL(
                        "http://" + address + ":" + port + "/cmd?cmd=" + command);
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                int code = connection.getResponseCode();
                runOnUiThread(() -> status.setText(
                        code == 200 ? successText : "Game phone error"));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Connection lost. Tap SEARCH AGAIN.");
                    host = null;
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 2001 && results.length > 0 &&
                results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
            startDiscovery();
        else
            status.setText("Nearby devices permission is required.");
    }

    @Override
    protected void onDestroy() {
        try {
            if (discovering) nsd.stopServiceDiscovery(discovery);
        } catch (Exception ignored) {}
        try {
            if (multicastLock != null && multicastLock.isHeld())
                multicastLock.release();
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
